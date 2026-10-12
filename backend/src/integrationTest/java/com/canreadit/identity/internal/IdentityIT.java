package com.canreadit.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class IdentityIT extends IdentityTestSupport {

    @Test
    void registersAndSignsInWithThePassword() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        assertThat(mail.await("ana@example.com").subject()).isEqualTo("Confirm your email for CanReadIt");

        var result = login("ana@example.com", PASSWORD);
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.handle").isEqualTo("ana_reads");
        assertThat(result).bodyJson().extractingPath("$.displayName").isEqualTo("ana_reads");
        assertThat(result).bodyJson().extractingPath("$.emailVerified").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.roles").asArray().containsExactly("READER");

        MockHttpServletResponse response = result.getResponse();
        Cookie access = response.getCookie(AuthCookies.ACCESS);
        Cookie refresh = response.getCookie(AuthCookies.REFRESH);
        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo(AuthCookies.REFRESH_PATH);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(c -> c.startsWith(AuthCookies.ACCESS) || c.startsWith(AuthCookies.REFRESH))
                .hasSize(2)
                .allMatch(c -> c.contains("SameSite=Lax"));

        assertThat(me(access))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("ana@example.com");

        assertThat(jdbc.sql("select password_hash from identity.credentials")
                        .query(String.class)
                        .single())
                .startsWith("$argon2id$");
        assertThat(jdbc.sql("select type from identity.auth_events order by created_at")
                        .query(String.class)
                        .list())
                .containsExactly("REGISTERED", "SIGNED_IN");
    }

    @Test
    void registeringATakenEmailLooksTheSameButEmailsTheOwner() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        mail.await("ana@example.com");

        assertThat(register("Ana@Example.com", "someone_else")).hasStatus(202);
        assertThat(mail.await("ana@example.com", 2).subject()).isEqualTo("You already have a CanReadIt account");
        assertThat(count("select count(*) from identity.users")).isEqualTo(1);

        // Handles are public, so a taken one is said plainly.
        assertThat(register("bo@example.com", "ANA_READS"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("handle_taken");
    }

    @Test
    void validatesRegistration() {
        assertThat(post(
                        "/api/v1/auth/password/register",
                        Map.of("email", "ana@example.com", "password", "short", "handle", "ana_reads")))
                .hasStatus(400);
        assertThat(post(
                        "/api/v1/auth/password/register",
                        Map.of("email", "ana@example.com", "password", PASSWORD, "handle", "a!")))
                .hasStatus(400);
        assertThat(post(
                        "/api/v1/auth/password/register",
                        Map.of("email", "not-an-email", "password", PASSWORD, "handle", "ana_reads")))
                .hasStatus(400);
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        register("ana@example.com", "ana_reads");
        mail.await("ana@example.com");

        var wrongPassword = login("ana@example.com", "not the password");
        var unknownEmail = login("nobody@example.com", PASSWORD);
        assertThat(wrongPassword)
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_credentials");
        assertThat(unknownEmail)
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_credentials");
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());

        assertThat(login("ANA@example.com", PASSWORD)).hasStatusOk();
        assertThat(jdbc.sql("select type from identity.auth_events order by created_at")
                        .query(String.class)
                        .list())
                .containsExactly("REGISTERED", "SIGN_IN_FAILED", "SIGN_IN_FAILED", "SIGNED_IN");
    }

    @Test
    void backsOffAfterRepeatedFailures() {
        register("ana@example.com", "ana_reads");

        for (int i = 0; i < LoginThrottle.FREE_FAILURES; i++) {
            assertThat(login("ana@example.com", "guess " + i)).hasStatus(401);
        }
        // Even the right password waits until the lock ends.
        var locked = login("ana@example.com", PASSWORD);
        assertThat(locked).hasStatus(429);
        assertThat(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    void refreshRotatesAndReuseEndsTheSession() {
        register("ana@example.com", "ana_reads");
        Cookie firstRefresh = login("ana@example.com", PASSWORD).getResponse().getCookie(AuthCookies.REFRESH);

        var refreshed = refresh(firstRefresh);
        assertThat(refreshed).hasStatus(204);
        Cookie access = refreshed.getResponse().getCookie(AuthCookies.ACCESS);
        Cookie secondRefresh = refreshed.getResponse().getCookie(AuthCookies.REFRESH);
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).hasStatusOk();

        // A second tab racing the first gets a retryable answer and the session survives.
        assertThat(refresh(firstRefresh))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("refresh_superseded");
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).hasStatusOk();

        // Later, the spent token can only be a copy: the whole session ends.
        jdbc.sql("update identity.refresh_tokens set used_at = now() - interval '1 minute' where used_at is not null")
                .update();
        var reused = refresh(firstRefresh);
        assertThat(reused).hasStatus(401).bodyJson().extractingPath("$.code").isEqualTo("invalid_refresh");
        assertThat(reused.getResponse().getCookie(AuthCookies.ACCESS).getMaxAge())
                .isZero();
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).hasStatus(401);
        assertThat(refresh(secondRefresh)).hasStatus(401);
        assertThat(jdbc.sql("select count(*) from identity.auth_events where type = 'REFRESH_REUSED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        assertThat(mvc.post().uri("/api/v1/auth/refresh")).hasStatus(401);
        assertThat(refresh(new Cookie(AuthCookies.REFRESH, "made-up"))).hasStatus(401);
    }

    @Test
    void logoutNeedsTheCsrfTokenAndEndsTheSession() {
        Cookie access = signUp("ana@example.com", "ana_reads");

        assertThat(mvc.post().uri("/api/v1/auth/logout").cookie(access)).hasStatus(403);

        Cookie csrf = csrfCookie();
        assertThat(csrf).isNotNull();
        assertThat(csrf.isHttpOnly()).isFalse();
        assertThat(csrf.getAttribute("SameSite")).isEqualTo("Lax");
        var loggedOut = mvc.post()
                .uri("/api/v1/auth/logout")
                .cookie(access, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
        assertThat(loggedOut).hasStatus(204);
        assertThat(loggedOut.getResponse().getCookie(AuthCookies.ACCESS).getMaxAge())
                .isZero();

        // The access token itself has not expired, but its session has.
        assertThat(mvc.get().uri("/api/v1/me").cookie(access)).hasStatus(401);
        assertThat(jdbc.sql("select count(*) from identity.sessions where revoked_at is not null")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void acceptsTheAccessTokenAsABearerHeader() {
        String token = signUp("ana@example.com", "ana_reads").getValue();

        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .hasStatus(401);
    }

    @Test
    void meNeedsASignedInUser() {
        assertThat(mvc.get().uri("/api/v1/me"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("unauthorized");
    }

    @Test
    void publicReadsNeverSetCookies() {
        var genres = mvc.get().uri("/api/v1/genres").exchange();
        assertThat(genres).hasStatusOk();
        assertThat(genres.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();

        // Everything else hands out the CSRF cookie, signed in or not.
        assertThat(csrfCookie()).isNotNull();
        assertThat(login("nobody@example.com", PASSWORD).getResponse().getCookie("XSRF-TOKEN"))
                .isNotNull();
    }

    @Test
    void publicPagesIgnoreAStaleAccessCookie() {
        assertThat(mvc.get().uri("/api/v1/genres").cookie(new Cookie(AuthCookies.ACCESS, "expired.or.garbage")))
                .hasStatusOk();
    }

    @Test
    void publishesThePublicSigningKey() {
        var jwks = mvc.get().uri("/.well-known/jwks.json").exchange();
        assertThat(jwks).hasStatusOk();
        assertThat(jwks).bodyJson().extractingPath("$.keys[0].kty").isEqualTo("EC");
        assertThat(jwks).bodyJson().extractingPath("$.keys[0].crv").isEqualTo("P-256");
        assertThat(jwks).bodyJson().extractingPath("$.keys[0].kid").asString().isNotBlank();
        assertThat(jwks).bodyJson().doesNotHavePath("$.keys[0].d");
    }
}
