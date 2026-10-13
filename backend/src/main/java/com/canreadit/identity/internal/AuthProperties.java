package com.canreadit.identity.internal;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in settings ({@code app.auth}).
 *
 * @param accessTokenTtl lifetime of the JWT access token
 * @param refreshTokenTtl sliding lifetime of a session: every refresh extends it by this much
 * @param eventRetention how long {@code auth_events} rows are kept
 * @param oidc the social sign-in providers (CLAUDE.md section 7): configuration entries, not code
 */
@ConfigurationProperties("app.auth")
record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration eventRetention,
        Jwt jwt,
        Turnstile turnstile,
        Map<IdentityProvider, OidcProvider> oidc) {

    /**
     * @param issuer the {@code iss} claim of our tokens
     * @param privateKey ES256 (P-256) private key, base64 PKCS#8 DER. Empty: a new key is generated at every start,
     *     so access tokens stop working at a restart and clients renew them with their refresh token
     */
    record Jwt(String issuer, @Nullable String privateKey) {}

    /**
     * Cloudflare Turnstile, the bot check on public forms. Without a secret key the check is off.
     *
     * @param secretKey the widget's secret key
     * @param verifyUrl Cloudflare's siteverify endpoint
     */
    record Turnstile(@Nullable String secretKey, URI verifyUrl) {}

    /**
     * An OpenID Connect provider whose ID tokens we accept. It is switched off until it has an audience.
     *
     * @param issuers accepted {@code iss} values
     * @param jwksUrl where the provider publishes its signing keys
     * @param audiences our client IDs registered with the provider (web, iOS, Android)
     */
    record OidcProvider(List<String> issuers, URI jwksUrl, List<String> audiences) {

        List<String> clientIds() {
            return audiences.stream()
                    .map(String::strip)
                    .filter(a -> !a.isEmpty())
                    .toList();
        }
    }
}
