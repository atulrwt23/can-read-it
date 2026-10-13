package com.canreadit.catalog.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicGetEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CatalogWebConfig {

    @Bean
    PublicGetEndpoints catalogPublicEndpoints() {
        return PublicGetEndpoints.of("/api/v1/genres", "/api/v1/series", "/api/v1/series/**");
    }

    /** Staff editing; each endpoint also checks its permission. */
    @Bean
    AuthenticatedEndpoints catalogAdminEndpoints() {
        return AuthenticatedEndpoints.of(
                "/api/v1/admin/series", "/api/v1/admin/series/**", "/api/v1/admin/chapters/**");
    }
}
