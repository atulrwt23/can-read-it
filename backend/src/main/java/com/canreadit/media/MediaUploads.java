package com.canreadit.media;

import java.util.UUID;

/** Stores images. Content is validated by magic bytes, and dimensions are read server-side. */
public interface MediaUploads {

    /**
     * Stores a public image and returns its asset ID. Uploading identical bytes for the same kind
     * returns the existing asset (storage is content-addressed).
     *
     * @throws com.canreadit.shared.ApiException if the content is not a supported image or too big
     */
    UUID storePublicImage(AssetKind kind, byte[] content);
}
