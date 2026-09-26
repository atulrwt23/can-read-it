package com.canreadit.media.internal;

import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUploads;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class MediaService implements MediaUrls, MediaUploads {

    static final String IMMUTABLE = "public, max-age=31536000, immutable";

    private final JdbcClient jdbc;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final String publicBaseUrl;

    MediaService(JdbcClient jdbc, ObjectStorage storage, MediaProperties properties) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    @Override
    public Map<UUID, ImageRef> resolve(Collection<UUID> assetIds, ImageVariant variant) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        // TODO(phase 3): PROTECTED assets get short-lived signed URLs after an entitlement check.
        Map<UUID, ImageRef> refs = new HashMap<>();
        jdbc.sql("""
                        select id, storage_key, width, height from media.assets
                        where id in (:ids) and status = 'READY' and visibility = 'PUBLIC'
                        """)
                .param("ids", List.copyOf(assetIds))
                .query((rs, row) -> refs.put(
                        rs.getObject("id", UUID.class),
                        new ImageRef(
                                publicBaseUrl + "/" + rs.getString("storage_key"),
                                rs.getInt("width"),
                                rs.getInt("height"))))
                .list();
        return refs;
    }

    @Override
    public UUID storePublicImage(AssetKind kind, byte[] content) {
        if (content.length > properties.maxUploadSize().toBytes()) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "upload_too_large", "The image is too large.");
        }
        // TODO(step 3): the upload pipeline re-encodes to WebP, which also strips metadata.
        ImageInspector.ImageInfo info = ImageInspector.inspect(content);
        String sha256 = sha256(content);
        String key = "%ss/%s/%s.%s"
                .formatted(kind.name().toLowerCase(Locale.ROOT), sha256.substring(0, 2), sha256, info.extension());

        var existing = findByKey(key);
        if (existing != null) {
            return existing;
        }
        storage.put(key, content, info.contentType(), IMMUTABLE);
        jdbc.sql("""
                        insert into media.assets
                            (id, kind, visibility, storage_key, sha256, content_type, width, height, bytes, status)
                        values (:id, :kind, 'PUBLIC', :key, :sha256, :contentType, :width, :height, :bytes, 'READY')
                        on conflict (storage_key) do nothing
                        """)
                .param("id", Ids.newId())
                .param("kind", kind.name())
                .param("key", key)
                .param("sha256", sha256)
                .param("contentType", info.contentType())
                .param("width", info.width())
                .param("height", info.height())
                .param("bytes", (long) content.length)
                .update();
        UUID id = findByKey(key);
        if (id == null) {
            throw new IllegalStateException("Asset vanished after insert: " + key);
        }
        return id;
    }

    private @org.jspecify.annotations.Nullable UUID findByKey(String key) {
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
