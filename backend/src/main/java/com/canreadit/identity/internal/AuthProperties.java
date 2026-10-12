package com.canreadit.identity.internal;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in settings ({@code app.auth}).
 *
 * @param accessTokenTtl lifetime of the JWT access token
 * @param refreshTokenTtl sliding lifetime of a session: every refresh extends it by this much
 */
@ConfigurationProperties("app.auth")
record AuthProperties(Duration accessTokenTtl, Duration refreshTokenTtl, Jwt jwt, Turnstile turnstile) {

    /**
     * @param issuer the {@code iss} claim of our tokens
     * @param privateKey ES256 (P-256) private key, base64 PKCS#8 DER. Empty: a new key is generated at every start,
     *     so sessions end when the API restarts (fine locally, not in production)
     */
    record Jwt(String issuer, @Nullable String privateKey) {}

    /**
     * Cloudflare Turnstile, the bot check on public forms. Without a secret key the check is off.
     *
     * @param secretKey the widget's secret key
     * @param verifyUrl Cloudflare's siteverify endpoint
     */
    record Turnstile(@Nullable String secretKey, URI verifyUrl) {}
}
