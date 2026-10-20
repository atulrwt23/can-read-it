package com.canreadit.moderation.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ModerationWebConfig {

    /** Staff only; the controller also checks the permission. */
    @Bean
    AuthenticatedEndpoints moderationEndpoints() {
        return AuthenticatedEndpoints.of("/api/v1/admin/moderation/**");
    }
}
