package com.canreadit.media.internal;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final String bucket;

    S3ObjectStorage(S3Client s3, MediaProperties properties) {
        this.s3 = s3;
        this.bucket = properties.s3().bucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType, String cacheControl) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(cacheControl)
                        .build(),
                RequestBody.fromBytes(content));
    }
}
