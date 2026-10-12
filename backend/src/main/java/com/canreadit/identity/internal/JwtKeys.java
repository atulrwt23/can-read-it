package com.canreadit.identity.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The ES256 key that signs access tokens. Its public half is published at {@code /.well-known/jwks.json}, with a
 * key ID derived from the key itself (RFC 7638 thumbprint).
 */
@Component
class JwtKeys {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);

    private final ECKey key;

    JwtKeys(AuthProperties properties) {
        String configured = properties.jwt().privateKey();
        this.key = configured == null || configured.isBlank() ? generate() : load(configured.strip());
    }

    ECKey signingKey() {
        return key;
    }

    ECPublicKey publicKey() {
        try {
            return key.toECPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unusable signing key", e);
        }
    }

    /** The JWK Set document: public keys only. */
    Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject(true);
    }

    private static ECKey generate() {
        log.warn("No app.auth.jwt.private-key (AUTH_JWT_PRIVATE_KEY): signing with a new key, so every session"
                + " ends when the API restarts");
        try {
            return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate a P-256 key", e);
        }
    }

    /** Loads a base64 PKCS#8 P-256 private key and derives its public point (Q = d·G). */
    static ECKey load(String base64Der) {
        try {
            var factory = KeyFactory.getInstance("EC");
            var privateKey = (ECPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64Der)));
            var curve = ECNamedCurveTable.getParameterSpec("secp256r1");
            if (!curve.getN().equals(privateKey.getParams().getOrder())) {
                throw new IllegalStateException("The JWT signing key must be a P-256 (secp256r1) key");
            }
            var q = curve.getG().multiply(privateKey.getS()).normalize();
            BigInteger x = q.getAffineXCoord().toBigInteger();
            BigInteger y = q.getAffineYCoord().toBigInteger();
            var publicKey = (ECPublicKey)
                    factory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), privateKey.getParams()));
            return new ECKey.Builder(Curve.P_256, publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (GeneralSecurityException | IllegalArgumentException | JOSEException e) {
            throw new IllegalStateException("app.auth.jwt.private-key is not a base64 PKCS#8 P-256 key", e);
        }
    }
}
