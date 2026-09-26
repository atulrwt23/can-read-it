package com.canreadit.media.internal;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param publicBaseUrl base URL browsers load public objects from (the CDN in prod)
 * @param maxUploadSize largest accepted upload
 * @param s3 object storage connection (any S3-compatible service)
 */
@ConfigurationProperties("app.media")
public record MediaProperties(
        String publicBaseUrl, @DefaultValue("20MB") DataSize maxUploadSize, S3 s3) {

    /**
     * @param endpoint custom endpoint (SeaweedFS, R2); null means AWS S3 itself
     * @param pathStyle path-style addressing, needed by most S3-compatible services
     */
    public record S3(
            @Nullable URI endpoint,
            @DefaultValue("us-east-1") String region,
            String bucket,
            String accessKey,
            String secretKey,
            @DefaultValue("true") boolean pathStyle) {}
}
