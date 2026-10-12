package com.canreadit.identity.internal;

import com.canreadit.shared.AuthenticatedEndpoints;
import com.canreadit.shared.PublicGetEndpoints;
import com.canreadit.shared.PublicPostEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Identity's routes. Sign-up, sign-in, password reset and refresh are exempt from CSRF: they don't act on an existing session
 * except refresh, whose cookie is {@code SameSite=Lax} and scoped to its path, so another site can't send it with
 * a POST. Sign-out acts on the access cookie and keeps CSRF protection.
 */
@Configuration(proxyBeanMethods = false)
class IdentityWebConfig {

    @Bean
    PublicPostEndpoints identitySignInEndpoints() {
        return PublicPostEndpoints.of(
                "/api/v1/auth/password/register",
                "/api/v1/auth/password/login",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/email/start",
                "/api/v1/auth/email/verify",
                "/api/v1/auth/refresh");
    }

    @Bean
    PublicPostEndpoints identitySignOutEndpoint() {
        return PublicPostEndpoints.withCsrf("/api/v1/auth/logout");
    }

    @Bean
    PublicGetEndpoints identityPublicKeys() {
        return PublicGetEndpoints.of("/.well-known/jwks.json");
    }

    @Bean
    AuthenticatedEndpoints identityAccountEndpoints() {
        return AuthenticatedEndpoints.of("/api/v1/me", "/api/v1/me/**");
    }
}
