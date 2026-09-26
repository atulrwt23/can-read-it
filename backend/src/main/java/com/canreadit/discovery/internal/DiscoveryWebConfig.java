package com.canreadit.discovery.internal;

import com.canreadit.shared.PublicGetEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DiscoveryWebConfig {

    @Bean
    PublicGetEndpoints discoveryPublicEndpoints() {
        return PublicGetEndpoints.of("/api/v1/home");
    }
}
