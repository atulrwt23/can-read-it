package com.canreadit.media.internal;

import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaAssets;
import com.canreadit.media.MediaUploads;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class MediaService implements MediaUrls, MediaUploads, MediaAssets {

    static final String IMMUTABLE = "public, max-age=31536000, immutable";

    /** Decoded pixels cost 4 bytes each, so this bounds one image at about 200 MB of heap. */
    static final long MAX_PIXELS = 50_000_000L;

    static final int PAGE_WIDTH = 1080;

    /** Cover renditions: the stored original, then the smaller variants. */
    static final Map<ImageVariant, Integer> COVER_WIDTHS =
            new EnumMap<>(Map.of(ImageVariant.ORIGINAL, 1200, ImageVariant.MEDIUM, 600, ImageVariant.SMALL, 300));

    /** Decoding and encoding are memory-hungry; two at a time keeps the 2 GB API container safe. */
    private static final Semaphore PROCESSING = new Semaphore(2);

    private final JdbcClient jdbc;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final JsonMapper json;
    private final String publicBaseUrl;

    MediaService(JdbcClient jdbc, ObjectStorage storage, MediaProperties properties, JsonMapper json) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.json = json;
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    /** A stored rendition other than the original (the {@code variants} column). */
    record StoredVariant(String key, int width, int height) {}

    @Override
    public Map<UUID, ImageRef> resolve(Collection<UUID> assetIds, ImageVariant variant) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        // TODO(phase 3): PROTECTED assets get short-lived signed URLs after an entitlement check.
        Map<UUID, ImageRef> refs = new HashMap<>();
        jdbc.sql("""
                        select id, storage_key, width, height, variants::text as variants from media.assets
                        where id in (:ids) and status = 'READY' and visibility = 'PUBLIC'
                        """)
                .param("ids", List.copyOf(assetIds))
                .query((rs, row) -> {
                    StoredVariant stored = variant == ImageVariant.ORIGINAL
                            ? null
                            : variants(rs.getString("variants")).get(variant.name());
                    ImageRef ref = stored != null
                            ? new ImageRef(url(stored.key()), stored.width(), stored.height())
                            : new ImageRef(url(rs.getString("storage_key")), rs.getInt("width"), rs.getInt("height"));
                    return refs.put(rs.getObject("id", UUID.class), ref);
                })
                .list();
        return refs;
    }

    @Override
    public Map<UUID, AssetInfo> find(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AssetInfo> found = new HashMap<>();
        jdbc.sql("select id, kind, width, height, status from media.assets where id in (:ids)")
                .param("ids", List.copyOf(assetIds))
                .query((rs, row) -> found.put(
                        rs.getObject("id", UUID.class),
                        new AssetInfo(
                                rs.getObject("id", UUID.class),
                                AssetKind.valueOf(rs.getString("kind")),
                                rs.getInt("width"),
                                rs.getInt("height"),
                                "READY".equals(rs.getString("status")))))
                .list();
        return found;
    }

    @Override
    public UUID storePublicImage(AssetKind kind, byte[] content) {
        if (content.length > properties.maxUploadSize().toBytes()) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "upload_too_large", "The image is too large.");
        }
        ImageInspector.ImageInfo info = ImageInspector.inspect(content);
        if ((long) info.width() * info.height() > MAX_PIXELS) {
            throw new ApiException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "image_too_large",
                    "The image has too many pixels (50 megapixels at most).");
        }
        // Addressed by the source bytes, so the same upload maps to the same asset without decoding it again.
        String sha256 = sha256(content);
        String base = "%ss/%s/%s".formatted(kind.name().toLowerCase(Locale.ROOT), sha256.substring(0, 2), sha256);
        String key = base + ".webp";
        UUID existing = findByKey(key);
        if (existing != null) {
            return existing;
        }

        Map<ImageVariant, ImageProcessor.Rendition> renditions = process(kind, content);
        ImageProcessor.Rendition original = renditions.get(ImageVariant.ORIGINAL);
        Map<String, StoredVariant> variants = new HashMap<>();
        for (var entry : renditions.entrySet()) {
            ImageProcessor.Rendition rendition = entry.getValue();
            if (entry.getKey() == ImageVariant.ORIGINAL) {
                storage.put(key, rendition.bytes(), "image/webp", IMMUTABLE);
            } else {
                String variantKey = "%s-%d.webp".formatted(base, rendition.width());
                storage.put(variantKey, rendition.bytes(), "image/webp", IMMUTABLE);
                variants.put(
                        entry.getKey().name(), new StoredVariant(variantKey, rendition.width(), rendition.height()));
            }
        }
        jdbc.sql("""
                        insert into media.assets
                            (id, kind, visibility, storage_key, sha256, content_type, width, height, bytes, status,
                             variants)
                        values (:id, :kind, 'PUBLIC', :key, :sha256, 'image/webp', :width, :height, :bytes, 'READY',
                                cast(:variants as jsonb))
                        on conflict (storage_key) do nothing
                        """)
                .param("id", Ids.newId())
                .param("kind", kind.name())
                .param("key", key)
                .param("sha256", sha256)
                .param("width", original.width())
                .param("height", original.height())
                .param("bytes", (long) original.bytes().length)
                .param("variants", json.writeValueAsString(variants))
                .update();
        UUID id = findByKey(key);
        if (id == null) {
            throw new IllegalStateException("Asset vanished after insert: " + key);
        }
        return id;
    }

    /** The renditions to store for this kind of image, largest first. */
    private Map<ImageVariant, ImageProcessor.Rendition> process(AssetKind kind, byte[] content) {
        try {
            PROCESSING.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "try_again", "The upload was interrupted.");
        }
        try {
            BufferedImage image = ImageProcessor.decode(content);
            Map<ImageVariant, ImageProcessor.Rendition> renditions = new EnumMap<>(ImageVariant.class);
            switch (kind) {
                case PAGE ->
                    renditions.put(
                            ImageVariant.ORIGINAL,
                            ImageProcessor.encodeWebp(ImageProcessor.fitWidth(image, PAGE_WIDTH)));
                case COVER, AVATAR -> {
                    BufferedImage current = image;
                    for (ImageVariant variant : ImageVariant.values()) {
                        current = ImageProcessor.fitWidth(current, COVER_WIDTHS.get(variant));
                        renditions.put(variant, ImageProcessor.encodeWebp(current));
                    }
                }
            }
            return renditions;
        } finally {
            PROCESSING.release();
        }
    }

    private Map<String, StoredVariant> variants(@Nullable String text) {
        if (text == null || text.isBlank() || text.equals("{}")) {
            return Map.of();
        }
        return json.readValue(
                text, json.getTypeFactory().constructMapType(Map.class, String.class, StoredVariant.class));
    }

    private String url(String key) {
        return publicBaseUrl + "/" + key;
    }

    private @Nullable UUID findByKey(String key) {
        return jdbc.sql("select id from media.assets where storage_key = :key")
                .param("key", key)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
