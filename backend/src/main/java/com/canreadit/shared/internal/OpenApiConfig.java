package com.canreadit.shared.internal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI openApi(BrandProperties brand) {
        return new OpenAPI().info(new Info().title(brand.name() + " API").version("v1"));
    }
}
