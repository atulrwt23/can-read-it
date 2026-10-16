package com.canreadit.analytics.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AnalyticsWebConfig {

    /** Beacons from any visitor; they rely on no cookie, so CSRF doesn't apply. */
    @Bean
    PublicPostEndpoints analyticsEvents() {
        return PublicPostEndpoints.of("/api/v1/analytics/events");
    }

    /** Staff only; the endpoint also checks its permission. */
    @Bean
    AuthenticatedEndpoints analyticsAdmin() {
        return AuthenticatedEndpoints.of("/api/v1/admin/analytics");
    }
}
