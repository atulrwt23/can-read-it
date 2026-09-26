package com.canreadit.media.internal;

/** Port for object storage. The only adapter today speaks the S3 API (ADR 0002). */
interface ObjectStorage {

    void put(String key, byte[] content, String contentType, String cacheControl);
}
