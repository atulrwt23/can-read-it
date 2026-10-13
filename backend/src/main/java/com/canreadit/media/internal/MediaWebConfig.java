package com.canreadit.media.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MediaWebConfig {

    /** Staff uploads; each endpoint also checks its permission. */
    @Bean
    AuthenticatedEndpoints mediaAdminEndpoints() {
        return AuthenticatedEndpoints.of("/api/v1/admin/media/**");
    }
}
