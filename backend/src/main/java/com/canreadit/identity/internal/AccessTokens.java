package com.canreadit.identity.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Issues ES256 access tokens (CLAUDE.md section 7): {@code sub} user, {@code sid} session, {@code roles},
 * {@code ev} email verified, {@code iat}, {@code exp}, and {@code kid} in the header.
 */
@Component
class AccessTokens {

    private final JwtKeys keys;
    private final AuthProperties properties;
    private final Clock clock;

    AccessTokens(JwtKeys keys, AuthProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    record Issued(String token, Instant expiresAt) {}

    Issued issue(UUID userId, UUID sessionId, Collection<String> roles, boolean emailVerified) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        var claims = new JWTClaimsSet.Builder()
                .issuer(properties.jwt().issuer())
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim("roles", List.copyOf(roles))
                .claim("ev", emailVerified)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(keys.signingKey().getKeyID())
                .build();
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new ECDSASigner(keys.signingKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign an access token", e);
        }
        return new Issued(jwt.serialize(), expiresAt);
    }

    /**
     * The session of a token we signed, even if it has expired. Signing out must work with a stale token, and needs
     * nothing more from it than which session to end.
     */
    Optional<UUID> sessionIgnoringExpiry(String token) {
        try {
            var jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new ECDSAVerifier(keys.publicKey()))) {
                return Optional.empty();
            }
            Object sid = jwt.getJWTClaimsSet().getClaim("sid");
            return sid instanceof String value ? Optional.of(UUID.fromString(value)) : Optional.empty();
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
