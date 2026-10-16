package com.canreadit.media;

import java.util.UUID;

/**
 * Stores images. Content is validated by magic bytes (PNG, JPEG or WebP; never SVG), size and pixel count, then
 * re-encoded to WebP, which also drops metadata (EXIF, location). Pages are scaled down to 1080 px wide; covers
 * get the sizes in {@link ImageVariant}.
 */
public interface MediaUploads {

    /**
     * Stores a public image and returns its asset ID. Uploading identical bytes for the same kind returns the
     * existing asset (storage is addressed by the source content).
     *
     * @throws com.canreadit.shared.ApiException if the content is not a supported image, too big, or too tall
     */
    UUID storePublicImage(AssetKind kind, byte[] content);
}
