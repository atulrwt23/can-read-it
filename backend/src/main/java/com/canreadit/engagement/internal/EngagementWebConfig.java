package com.canreadit.engagement.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicGetEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EngagementWebConfig {

    /** The same for every visitor, so CDN-cacheable. */
    @Bean
    PublicGetEndpoints seriesRatings() {
        return PublicGetEndpoints.of("/api/v1/series/*/rating");
    }

    @Bean
    AuthenticatedEndpoints myRatings() {
        return AuthenticatedEndpoints.of("/api/v1/me/ratings/**");
    }
}
