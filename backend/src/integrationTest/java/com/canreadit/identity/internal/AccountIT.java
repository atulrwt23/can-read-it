package com.canreadit.identity.internal;

import static com.canreadit.RecordingEmailSender.codeIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class AccountIT extends IdentityTestSupport {

    @Autowired
    AccountEraser eraser;

    @Test
    void meListsTheWaysToSignIn() {
        Cookie access = signUp("ana@example.com", "ana_reads");

        var me = me(access);
        assertThat(me).bodyJson().extractingPath("$.hasPassword").isEqualTo(true);
        assertThat(me).bodyJson().extractingPath("$.providers").asArray().isEmpty();
    }

    @Test
    void updatesTheProfile() {
        Cookie access = signUp("ana@example.com", "ana_reads");
        signUp("bo@example.com", "bo_reads");

        var updated =
                write(HttpMethod.PATCH, "/api/v1/me", access, Map.of("handle", "Ana_Reads", "displayName", " Ana "));
        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.handle").isEqualTo("Ana_Reads");
        assertThat(updated).bodyJson().extractingPath("$.displayName").isEqualTo("Ana");

        assertThat(write(HttpMethod.PATCH, "/api/v1/me", access, Map.of("handle", "BO_READS")))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("handle_taken");
        assertThat(write(HttpMethod.PATCH, "/api/v1/me", access, Map.of("displayName", "   ")))
                .hasStatus(400);
        assertThat(write(HttpMethod.PATCH, "/api/v1/me", access, Map.of("displayName", "a\u0000b")))
                .hasStatus(400);
        // Writes need the CSRF header.
        assertThat(mvc.patch()
                        .uri("/api/v1/me")
                        .cookie(access)
                        .contentType("application/json")
                        .content("{\"displayName\":\"x\"}"))
                .hasStatus(403);
    }

    @Test
    void changingThePasswordSignsOutOtherDevices() {
        Cookie here = signUp("ana@example.com", "ana_reads");
        Cookie elsewhere = login("ana@example.com", PASSWORD).getResponse().getCookie(AuthCookies.ACCESS);
        String newPassword = "another long passphrase";

        assertThat(write(
                        HttpMethod.POST,
                        "/api/v1/me/password",
                        here,
                        Map.of("currentPassword", "wrong password!", "newPassword", newPassword)))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("wrong_password");

        assertThat(write(
                        HttpMethod.POST,
                        "/api/v1/me/password",
                        here,
                        Map.of("currentPassword", PASSWORD, "newPassword", newPassword)))
                .hasStatus(204);
        assertThat(me(here)).hasStatusOk();
        assertThat(me(elsewhere)).hasStatus(401);
        assertThat(login("ana@example.com", newPassword)).hasStatusOk();
    }

    @Test
    void settingAFirstPasswordNeedsARecentSignIn() {
        post("/api/v1/auth/email/start", Map.of("email", "new@example.com"));
        Cookie access = post(
                        "/api/v1/auth/email/verify",
                        Map.of("email", "new@example.com", "code", codeIn(mail.await("new@example.com"))))
                .getResponse()
                .getCookie(AuthCookies.ACCESS);

        jdbc.sql("update identity.sessions set created_at = now() - interval '1 hour'")
                .update();
        assertThat(write(HttpMethod.POST, "/api/v1/me/password", access, Map.of("newPassword", "a fresh passphrase")))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("reauthentication_required");

        jdbc.sql("update identity.sessions set created_at = now()").update();
        assertThat(write(HttpMethod.POST, "/api/v1/me/password", access, Map.of("newPassword", "a fresh passphrase")))
                .hasStatus(204);
        assertThat(me(access)).bodyJson().extractingPath("$.hasPassword").isEqualTo(true);
        assertThat(login("new@example.com", "a fresh passphrase")).hasStatusOk();
    }

    @Test
    void listsAndEndsSessions() {
        Cookie a = signUp("ana@example.com", "ana_reads");
        Cookie b = login("ana@example.com", PASSWORD).getResponse().getCookie(AuthCookies.ACCESS);
        Cookie c = login("ana@example.com", PASSWORD).getResponse().getCookie(AuthCookies.ACCESS);
        Cookie bo = signUp("bo@example.com", "bo_reads");

        var list = mvc.get().uri("/api/v1/me/sessions").cookie(a).exchange();
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.length()").isEqualTo(3);
        assertThat(list)
                .bodyJson()
                .extractingPath("$[?(@.current == true)].id")
                .asArray()
                .hasSize(1);

        String bSession = sessionOf(b);
        // Someone else's session is not found, and nothing happens to it.
        assertThat(write(HttpMethod.DELETE, "/api/v1/me/sessions/" + bSession, bo, null))
                .hasStatus(404);
        assertThat(me(b)).hasStatusOk();

        assertThat(write(HttpMethod.DELETE, "/api/v1/me/sessions/" + bSession, a, null))
                .hasStatus(204);
        assertThat(me(b)).hasStatus(401);

        assertThat(write(HttpMethod.POST, "/api/v1/me/sessions/revoke-all", a, null))
                .hasStatus(204);
        assertThat(me(c)).hasStatus(401);
        assertThat(me(a)).hasStatusOk();

        // Signing out this device clears its cookies.
        var self = write(HttpMethod.DELETE, "/api/v1/me/sessions/" + sessionOf(a), a, null);
        assertThat(self).hasStatus(204);
        assertThat(self.getResponse().getCookie(AuthCookies.ACCESS).getMaxAge()).isZero();
        assertThat(me(a)).hasStatus(401);
    }

    @Test
    void deletionWaitsThirtyDaysAndSigningInCancelsIt() {
        Cookie access = signUp("ana@example.com", "ana_reads");

        assertThat(write(HttpMethod.DELETE, "/api/v1/me", access, Map.of("currentPassword", "wrong password!")))
                .hasStatus(403);
        var deleted = write(HttpMethod.DELETE, "/api/v1/me", access, Map.of("currentPassword", PASSWORD));
        assertThat(deleted).hasStatus(202);
        assertThat(deleted).bodyJson().extractingPath("$.eraseAfter").isNotNull();
        assertThat(me(access)).hasStatus(401);
        assertThat(mail.await("ana@example.com", 2).subject()).isEqualTo("Your CanReadIt account will be deleted");

        // Signing in within the grace period keeps the account.
        assertThat(login("ana@example.com", PASSWORD)).hasStatusOk();
        assertThat(status()).isEqualTo("ACTIVE");
    }

    @Test
    void erasesTheAccountAfterTheGracePeriod() {
        Cookie access = signUp("ana@example.com", "ana_reads");
        write(HttpMethod.DELETE, "/api/v1/me", access, Map.of("currentPassword", PASSWORD));

        eraser.run();
        assertThat(status()).isEqualTo("PENDING_DELETION");

        jdbc.sql("update identity.users set deletion_requested_at = now() - interval '31 days'")
                .update();
        eraser.run();

        assertThat(status()).isEqualTo("DELETED");
        assertThat(jdbc.sql("select email from identity.users")
                        .query(String.class)
                        .optional())
                .isEmpty();
        assertThat(count("select count(*) from identity.credentials")).isZero();
        assertThat(count("select count(*) from identity.sessions")).isZero();
        assertThat(count("select count(*) from identity.auth_events")).isZero();
        assertThat(login("ana@example.com", PASSWORD)).hasStatus(401);
        // The email and handle are free again.
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        assertThat(count("select count(*) from identity.users where status = 'ACTIVE'"))
                .isEqualTo(1);
    }

    private String status() {
        return jdbc.sql("select status from identity.users order by created_at limit 1")
                .query(String.class)
                .single();
    }

    private String sessionOf(Cookie access) {
        var list = mvc.get().uri("/api/v1/me/sessions").cookie(access).exchange();
        return json.readTree(list.getResponse().getContentAsByteArray())
                .valueStream()
                .filter(node -> node.get("current").asBoolean())
                .findFirst()
                .orElseThrow()
                .get("id")
                .asString();
    }
}
