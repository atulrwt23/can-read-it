package com.canreadit.identity.internal;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * How the API checks the access tokens it issued: our public key, ES256 only, our issuer, not expired, and the
 * session not revoked. Roles become {@code ROLE_*} authorities, and each role's permissions ({@link Permission})
 * authorities of their own.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, AuthProperties properties, SessionDenylist denylist) {
        // ES256 only, with our one public key: a token signed any other way is rejected.
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.ES256,
                new ImmutableJWKSet<>(new JWKSet(keys.signingKey().toPublicJWK()))));
        NimbusJwtDecoder nimbus = new NimbusJwtDecoder(processor);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())));
        return token -> {
            Jwt jwt = nimbus.decode(token);
            String sid = jwt.getClaimAsString("sid");
            if (sid == null || denylist.isRevoked(parse(sid))) {
                throw new BadJwtException("The session has ended");
            }
            return jwt;
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.<GrantedAuthority>of();
            }
            var authorities = new LinkedHashSet<GrantedAuthority>();
            for (String name : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
                // Unknown role names (from an older or newer release) grant nothing.
                Arrays.stream(Role.values())
                        .filter(role -> role.name().equals(name))
                        .flatMap(role -> Permission.of(role).stream())
                        .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
            }
            return List.copyOf(authorities);
        });
        return converter;
    }

    private static UUID parse(String sid) {
        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("Malformed session ID");
        }
    }
}
