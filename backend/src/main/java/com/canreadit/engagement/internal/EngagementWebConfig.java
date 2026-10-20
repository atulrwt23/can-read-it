package com.canreadit.engagement.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicGetEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EngagementWebConfig {

    /** The same for every visitor, so CDN-cacheable. */
    @Bean
    PublicGetEndpoints engagementReads() {
        return PublicGetEndpoints.of("/api/v1/series/*/rating", "/api/v1/series/*/chapters/*/comments");
    }

    @Bean
    AuthenticatedEndpoints engagementWrites() {
        // Reading comments is public (GET above); posting one needs an account.
        return AuthenticatedEndpoints.of(
                "/api/v1/me/ratings/**",
                "/api/v1/me/comments/**",
                "/api/v1/series/*/chapters/*/comments",
                "/api/v1/comments/*/reports");
    }
}
