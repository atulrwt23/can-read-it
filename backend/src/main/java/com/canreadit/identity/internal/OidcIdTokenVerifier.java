package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Verifies ID tokens with Nimbus against each provider's JWKS ({@code app.auth.oidc}). Keys are fetched on first use
 * and cached; an unknown key ID triggers a refetch, so provider key rotation needs nothing from us.
 */
@Component
class OidcIdTokenVerifier implements IdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(OidcIdTokenVerifier.class);
    private static final Set<JWSAlgorithm> ALGORITHMS = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256);

    private final Map<IdentityProvider, Verifier> verifiers = new EnumMap<>(IdentityProvider.class);

    OidcIdTokenVerifier(AuthProperties properties) {
        properties.oidc().forEach((provider, config) -> {
            if (!config.clientIds().isEmpty()) {
                verifiers.put(provider, new Verifier(config));
            }
        });
    }

    @Override
    public boolean enabled(IdentityProvider provider) {
        return verifiers.containsKey(provider);
    }

    @Override
    public VerifiedIdToken verify(IdentityProvider provider, String idToken, String expectedNonce) {
        Verifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw providerUnavailable();
        }
        JWTClaimsSet claims;
        try {
            claims = verifier.processor.process(idToken, null);
        } catch (ParseException | BadJOSEException e) {
            throw invalid();
        } catch (JOSEException e) {
            // Usually the provider's keys could not be fetched.
            log.warn("Could not verify a {} ID token: {}", provider, e.toString());
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "provider_unreachable",
                    "Sign-in with this provider failed. Try again.");
        }
        if (!verifier.issuers.contains(claims.getIssuer()) || !nonceMatches(claims, expectedNonce)) {
            throw invalid();
        }
        String email = stringClaim(claims, "email");
        return new VerifiedIdToken(
                claims.getSubject(), email, email != null && isTrue(claims.getClaim("email_verified")));
    }

    private static boolean nonceMatches(JWTClaimsSet claims, String expected) {
        String nonce = stringClaim(claims, "nonce");
        if (nonce == null) {
            return false;
        }
        return equal(nonce, expected) || equal(nonce, OpaqueTokens.hash(expected));
    }

    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Google sends {@code email_verified} as a boolean, Apple as a string. */
    private static boolean isTrue(@Nullable Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    private static @Nullable String stringClaim(JWTClaimsSet claims, String name) {
        return claims.getClaim(name) instanceof String value && !value.isBlank() ? value : null;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_id_token", "Sign-in with this provider failed.");
    }

    static ApiException providerUnavailable() {
        return ApiException.notFound("provider_unavailable", "This sign-in method is not available.");
    }

    private static final class Verifier {

        final Set<String> issuers;
        final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

        Verifier(AuthProperties.OidcProvider config) {
            this.issuers = Set.copyOf(config.issuers());
            JWKSource<SecurityContext> keys;
            try {
                keys = JWKSourceBuilder.create(
                                config.jwksUrl().toURL(), new DefaultResourceRetriever(3_000, 5_000, 512 * 1024))
                        .build();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid JWKS URL " + config.jwksUrl(), e);
            }
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALGORITHMS, keys));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    new HashSet<>(config.clientIds()), null, Set.of("iss", "sub", "aud", "exp", "iat"), null));
        }
    }
}
