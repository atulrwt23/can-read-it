package com.canreadit.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtKeysTest {

    @Test
    void loadsAPkcs8KeyAndDerivesItsPublicHalf() throws Exception {
        KeyPair pair = keyPair("secp256r1");

        var key = JwtKeys.load(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));

        assertThat(key.getCurve()).isEqualTo(Curve.P_256);
        assertThat(key.toECPublicKey().getW()).isEqualTo(((ECPublicKey) pair.getPublic()).getW());
        assertThat(key.getKeyID()).isNotBlank();

        var jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder().subject("x").build());
        jwt.sign(new ECDSASigner(key));
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) pair.getPublic())))
                .isTrue();
    }

    @Test
    void keyIdIsStableForTheSameKey() throws Exception {
        String encoded = Base64.getEncoder()
                .encodeToString(keyPair("secp256r1").getPrivate().getEncoded());

        assertThat(JwtKeys.load(encoded).getKeyID())
                .isEqualTo(JwtKeys.load(encoded).getKeyID());
    }

    @Test
    void rejectsOtherCurvesAndGarbage() throws Exception {
        String p384 = Base64.getEncoder()
                .encodeToString(keyPair("secp384r1").getPrivate().getEncoded());

        assertThatThrownBy(() -> JwtKeys.load(p384)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtKeys.load("not a key")).isInstanceOf(IllegalStateException.class);
    }

    private static KeyPair keyPair(String curve) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }
}
