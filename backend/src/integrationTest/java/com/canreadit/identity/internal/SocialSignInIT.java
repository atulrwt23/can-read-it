package com.canreadit.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.shared.AuthCookies;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Google and Apple sign-in against a local JWKS, with tokens signed by a test key. */
class SocialSignInIT extends IdentityTestSupport {

    static final String GOOGLE_CLIENT = "web-client.apps.googleusercontent.test";
    static final String APPLE_CLIENT = "com.canreadit.test.web";

    static final RSAKey KEY = generateKey("test-key");
    static final RSAKey OTHER_KEY = generateKey("test-key");
    static final HttpServer JWKS = startJwks();

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks";
        registry.add("app.auth.oidc.google.jwks-url", () -> url);
        registry.add("app.auth.oidc.google.audiences", () -> GOOGLE_CLIENT);
        registry.add("app.auth.oidc.apple.jwks-url", () -> url);
        registry.add("app.auth.oidc.apple.audiences", () -> APPLE_CLIENT);
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Test
    void firstSignInCreatesAVerifiedAccountAndTheNextFindsIt() {
        var first = signIn("google", google("g-1", "ana@gmail.test", true));
        assertThat(first).hasStatusOk();
        assertThat(first).bodyJson().extractingPath("$.email").isEqualTo("ana@gmail.test");
        assertThat(first).bodyJson().extractingPath("$.emailVerified").isEqualTo(true);
        assertThat(first).bodyJson().extractingPath("$.handle").asString().startsWith("reader_");
        assertThat(me(first.getResponse().getCookie(AuthCookies.ACCESS))).hasStatusOk();

        // Matched on the subject, even if the email changed at Google.
        assertThat(signIn("google", google("g-1", "ana.new@gmail.test", true)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("ana@gmail.test");
        assertThat(count("select count(*) from identity.users")).isEqualTo(1);
    }

    @Test
    void appleWithoutAnEmailOnLaterSignInsStillWorks() {
        var first = signIn("apple", apple("a-1", "relay@privaterelay.appleid.test", "true"));
        assertThat(first)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.emailVerified")
                .isEqualTo(true);

        assertThat(signIn("apple", apple("a-1", null, null))).hasStatusOk();
        assertThat(jdbc.sql("select provider_email from identity.user_identities")
                        .query(String.class)
                        .single())
                .isEqualTo("relay@privaterelay.appleid.test");
    }

    @Test
    void linksToAVerifiedAccountWithTheSameEmail() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        verifyEmail("ana@example.com");

        assertThat(signIn("google", google("g-1", "ana@example.com", true)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.handle")
                .isEqualTo("ana_reads");
        // Linking keeps the password: both sides had proven the email.
        assertThat(login("ana@example.com", PASSWORD)).hasStatusOk();
    }

    @Test
    void takesBackAnUnverifiedAccountWithTheSameEmail() {
        Cookie squatter = signUp("ana@example.com", "not_ana");

        assertThat(signIn("google", google("g-1", "ana@example.com", true)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.emailVerified")
                .isEqualTo(true);

        assertThat(login("ana@example.com", PASSWORD)).hasStatus(401);
        assertThat(me(squatter)).hasStatus(401);
    }

    @Test
    void anUnverifiedProviderEmailNeverLinks() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);

        var signedIn = signIn("google", google("g-1", "ana@example.com", false));
        assertThat(signedIn).hasStatusOk();
        assertThat(signedIn).bodyJson().extractingPath("$.handle").isNotEqualTo("ana_reads");
        assertThat(signedIn).bodyJson().extractingPath("$.email").isNull();
        assertThat(login("ana@example.com", PASSWORD)).hasStatusOk();
    }

    @Test
    void rejectsBadTokens() {
        // Wrong audience, wrong issuer, expired, unknown signing key, garbage.
        assertThat(signIn(
                        "google",
                        token(KEY, "https://accounts.google.com", "someone-else", "g-1", "a@b.test", true, 600)))
                .hasStatus(401);
        assertThat(signIn("google", token(KEY, "https://evil.test", GOOGLE_CLIENT, "g-1", "a@b.test", true, 600)))
                .hasStatus(401);
        assertThat(signIn(
                        "google",
                        token(KEY, "https://accounts.google.com", GOOGLE_CLIENT, "g-1", "a@b.test", true, -600)))
                .hasStatus(401);
        assertThat(signIn(
                        "google",
                        token(OTHER_KEY, "https://accounts.google.com", GOOGLE_CLIENT, "g-1", "a@b.test", true, 600)))
                .hasStatus(401);
        assertThat(signIn("google", n -> "not.a.jwt")).hasStatus(401);
        // An Apple token is not a Google token.
        assertThat(signIn("google", apple("a-1", "a@b.test", "true"))).hasStatus(401);
        assertThat(count("select count(*) from identity.users")).isZero();
    }

    @Test
    void nonceMustBeOursUnusedAndInTheToken() {
        // A nonce we never issued.
        assertThat(post(
                        "/api/v1/auth/oauth/google",
                        Map.of("idToken", google("g-1", "a@b.test", true).forNonce("made-up"), "nonce", "made-up")))
                .hasStatus(401);

        // A token whose nonce is not the one sent.
        String nonce = nonce();
        assertThat(post(
                        "/api/v1/auth/oauth/google",
                        Map.of("idToken", google("g-1", "a@b.test", true).forNonce("other"), "nonce", nonce)))
                .hasStatus(401);

        // Replaying a token with its (spent) nonce.
        String fresh = nonce();
        String idToken = google("g-1", "a@b.test", true).forNonce(fresh);
        assertThat(post("/api/v1/auth/oauth/google", Map.of("idToken", idToken, "nonce", fresh)))
                .hasStatusOk();
        assertThat(post("/api/v1/auth/oauth/google", Map.of("idToken", idToken, "nonce", fresh)))
                .hasStatus(401);

        // Some SDKs send the provider the SHA-256 of the nonce.
        String hashed = nonce();
        assertThat(post(
                        "/api/v1/auth/oauth/apple",
                        Map.of(
                                "idToken",
                                apple("a-1", "x@y.test", "true").forNonce(OpaqueTokens.hash(hashed)),
                                "nonce",
                                hashed)))
                .hasStatusOk();
    }

    @Test
    void unknownProvidersAreNotFound() {
        assertThat(post("/api/v1/auth/oauth/facebook", Map.of("idToken", "x", "nonce", nonce())))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("provider_unavailable");
    }

    @Test
    void linksAndUnlinksWhileSignedIn() {
        Cookie access = signUp("ana@example.com", "ana_reads");
        Cookie csrf = csrfCookie();

        String nonce = nonce();
        var link = mvc.post()
                .uri("/api/v1/me/identities/google")
                .cookie(access, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "idToken", google("g-1", "ana.other@gmail.test", true).forNonce(nonce), "nonce", nonce)))
                .exchange();
        assertThat(link).hasStatus(204);

        // Google now signs in to Ana's account.
        assertThat(signIn("google", google("g-1", "ana.other@gmail.test", true)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.handle")
                .isEqualTo("ana_reads");

        // Unlinking needs CSRF, then works because the password remains.
        assertThat(mvc.delete().uri("/api/v1/me/identities/google").cookie(access))
                .hasStatus(403);
        assertThat(mvc.delete()
                        .uri("/api/v1/me/identities/google")
                        .cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .hasStatus(204);
        assertThat(count("select count(*) from identity.user_identities")).isZero();
    }

    @Test
    void refusesToUnlinkTheOnlyWayIn() {
        // A Google account whose email Google did not verify: no password, no code sign-in.
        Cookie access = signIn("google", google("g-1", "unverified@gmail.test", false))
                .getResponse()
                .getCookie(AuthCookies.ACCESS);
        Cookie csrf = csrfCookie();

        assertThat(mvc.delete()
                        .uri("/api/v1/me/identities/google")
                        .cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("last_sign_in_method");
    }

    @Test
    void anIdentityBelongsToOneUser() {
        signIn("google", google("g-1", "first@gmail.test", true));
        Cookie access = signUp("ana@example.com", "ana_reads");
        Cookie csrf = csrfCookie();

        String nonce = nonce();
        assertThat(mvc.post()
                        .uri("/api/v1/me/identities/google")
                        .cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "idToken",
                                google("g-1", "first@gmail.test", true).forNonce(nonce),
                                "nonce",
                                nonce))))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("identity_in_use");
    }

    // --- helpers

    /** Builds an ID token for a given nonce. */
    interface TokenForNonce {
        String forNonce(String nonce);
    }

    private MvcTestResult signIn(String provider, TokenForNonce token) {
        String nonce = nonce();
        return post("/api/v1/auth/oauth/" + provider, Map.of("idToken", token.forNonce(nonce), "nonce", nonce));
    }

    private String nonce() {
        var response = mvc.post().uri("/api/v1/auth/oauth/nonce").exchange();
        assertThat(response).hasStatusOk();
        return json.readTree(response.getResponse().getContentAsByteArray())
                .get("nonce")
                .asString();
    }

    private void verifyEmail(String email) {
        String code = com.canreadit.RecordingEmailSender.codeIn(mail.await(email));
        assertThat(post("/api/v1/auth/email/verify", Map.of("email", email, "code", code)))
                .hasStatusOk();
    }

    private static TokenForNonce google(String subject, String email, boolean verified) {
        return nonce -> sign(
                KEY,
                claims("https://accounts.google.com", GOOGLE_CLIENT, subject, 600)
                        .claim("email", email)
                        .claim("email_verified", verified)
                        .claim("nonce", nonce)
                        .build());
    }

    private static TokenForNonce apple(String subject, @Nullable String email, @Nullable String verified) {
        return nonce -> {
            var claims = claims("https://appleid.apple.com", APPLE_CLIENT, subject, 600)
                    .claim("nonce", nonce);
            if (email != null) {
                claims.claim("email", email).claim("email_verified", verified);
            }
            return sign(KEY, claims.build());
        };
    }

    private static TokenForNonce token(
            RSAKey key, String issuer, String audience, String subject, String email, boolean verified, long ttl) {
        return nonce -> sign(
                key,
                claims(issuer, audience, subject, ttl)
                        .claim("email", email)
                        .claim("email_verified", verified)
                        .claim("nonce", nonce)
                        .build());
    }

    private static JWTClaimsSet.Builder claims(String issuer, String audience, String subject, long ttlSeconds) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now.minusSeconds(Math.max(0, -ttlSeconds) + 5)))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
