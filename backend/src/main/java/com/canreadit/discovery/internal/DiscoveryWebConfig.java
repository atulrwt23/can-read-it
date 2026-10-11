package com.canreadit.discovery.internal;

import com.canreadit.shared.PublicGetEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DiscoveryWebConfig {

    @Bean
    PublicGetEndpoints discoveryPublicEndpoints() {
        return PublicGetEndpoints.of("/api/v1/home");
    }

    @Bean
    PublicPostEndpoints discoveryBeacons() {
        return PublicPostEndpoints.of("/api/v1/series/*/views");
    }
}
