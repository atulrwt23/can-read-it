package com.canreadit.reading.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReadingWebConfig {

    /** All of it is personal: a signed-in reader's own library and history. */
    @Bean
    AuthenticatedEndpoints readingEndpoints() {
        return AuthenticatedEndpoints.of(
                "/api/v1/me/library",
                "/api/v1/me/library/**",
                "/api/v1/me/series/**",
                "/api/v1/me/progress/**",
                "/api/v1/me/history",
                "/api/v1/me/history/**");
    }
}
