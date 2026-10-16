package com.canreadit.media.internal;

import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.shared.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Staff uploads of covers and chapter pages, one image per request (Cloudflare caps request bodies, and one at a
 * time keeps memory bounded). The image is processed before the response, so the asset is ready to reference.
 */
@RestController
class MediaAdminController {

    private final MediaService media;

    MediaAdminController(MediaService media) {
        this.media = media;
    }

    record UploadedImage(UUID id, String url, int width, int height) {}

    @PostMapping(path = "/api/v1/admin/media/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MEDIA_UPLOAD')")
    ResponseEntity<UploadedImage> upload(@PathVariable String kind, @RequestParam("file") MultipartFile file) {
        AssetKind assetKind = switch (kind) {
            case "covers" -> AssetKind.COVER;
            case "pages" -> AssetKind.PAGE;
            default -> throw ApiException.notFound("unknown_media_kind", "Upload covers or pages.");
        };
        if (file.isEmpty()) {
            throw ApiException.badRequest("empty_upload", "Choose an image to upload.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        UUID id = media.storePublicImage(assetKind, content);
        ImageRef ref = media.resolve(List.of(id), ImageVariant.ORIGINAL).get(id);
        if (ref == null) {
            throw new IllegalStateException("Uploaded asset is not resolvable: " + id);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(new UploadedImage(id, ref.url(), ref.width(), ref.height()));
    }
}
