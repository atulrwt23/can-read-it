package com.canreadit.catalog.internal;

import com.canreadit.shared.PublicGetEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CatalogWebConfig {

    @Bean
    PublicGetEndpoints catalogPublicEndpoints() {
        return PublicGetEndpoints.of("/api/v1/genres", "/api/v1/series", "/api/v1/series/**");
    }
}
