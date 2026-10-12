package com.canreadit.identity.internal;

import static com.canreadit.RecordingEmailSender.codeIn;
import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class EmailCodeIT extends IdentityTestSupport {

    @Autowired
    IdentityCleanup cleanup;

    @Test
    void startAnswersTheSameForNewAndExistingEmails() {
        signUp("ana@example.com", "ana_reads");

        assertThat(start("ana@example.com")).hasStatus(202);
        assertThat(start("new@example.com")).hasStatus(202);

        assertThat(mail.await("ana@example.com", 2).subject()).isEqualTo("Your CanReadIt sign-in code");
        assertThat(mail.await("new@example.com").subject()).isEqualTo("Your CanReadIt sign-up code");
        assertThat(jdbc.sql("select code_hash from identity.verification_codes")
                        .query(String.class)
                        .list())
                .allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void aSignUpCodeCreatesAVerifiedAccount() {
        start("new@example.com");
        String code = codeIn(mail.await("new@example.com"));

        var signedIn = verify("new@example.com", code, "new_reader");
        assertThat(signedIn).hasStatusOk();
        assertThat(signedIn).bodyJson().extractingPath("$.handle").isEqualTo("new_reader");
        assertThat(signedIn).bodyJson().extractingPath("$.emailVerified").isEqualTo(true);
        assertThat(me(signedIn.getResponse().getCookie(AuthCookies.ACCESS))).hasStatusOk();
        assertThat(count("select count(*) from identity.credentials")).isZero();

        // A code works once.
        assertThat(verify("new@example.com", code, null)).hasStatus(400);
    }

    @Test
    void generatesAHandleThatDoesNotComeFromTheEmail() {
        start("Private.Name@example.com");
        String code = codeIn(mail.await("private.name@example.com"));

        assertThat(verify("private.name@example.com", code, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.handle")
                .asString()
                .matches("reader_[a-z2-9]{8}");
    }

    @Test
    void aTakenHandleKeepsTheCodeUsable() {
        signUp("ana@example.com", "ana_reads");
        start("new@example.com");
        String code = codeIn(mail.await("new@example.com"));

        assertThat(verify("new@example.com", code, "ana_reads"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("handle_taken");
        assertThat(verify("new@example.com", code, "another_one")).hasStatusOk();
    }

    @Test
    void wrongGuessesUseUpTheCode() {
        start("new@example.com");
        String code = codeIn(mail.await("new@example.com"));
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < VerificationCodes.MAX_ATTEMPTS; i++) {
            assertThat(verify("new@example.com", wrong, null))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("invalid_code");
        }
        assertThat(verify("new@example.com", code, null)).hasStatus(400);
        assertThat(count("select count(*) from identity.auth_events where type = 'CODE_FAILED'"))
                .isEqualTo(VerificationCodes.MAX_ATTEMPTS + 1);
    }

    @Test
    void onlyTheNewestCodeWorks() {
        start("new@example.com");
        String first = codeIn(mail.await("new@example.com"));
        start("new@example.com");
        String second = codeIn(mail.await("new@example.com", 2));

        if (!first.equals(second)) {
            assertThat(verify("new@example.com", first, null)).hasStatus(400);
        }
        assertThat(verify("new@example.com", second, null)).hasStatusOk();
    }

    @Test
    void expiredCodesDoNotWork() {
        start("new@example.com");
        String code = codeIn(mail.await("new@example.com"));
        jdbc.sql("update identity.verification_codes set expires_at = now() - interval '1 second'")
                .update();

        assertThat(verify("new@example.com", code, null)).hasStatus(400);
    }

    @Test
    void theConfirmationCodeVerifiesTheEmailAndKeepsThePassword() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        String code = codeIn(mail.await("ana@example.com"));

        assertThat(verify("ana@example.com", code, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.emailVerified")
                .isEqualTo(true);
        assertThat(login("ana@example.com", PASSWORD)).hasStatusOk();
    }

    @Test
    void aSignInCodeTakesBackAnAccountRegisteredBySomeoneElse() {
        // Someone registers Ana's email before she does, and keeps a session open.
        Cookie squatter = signUp("ana@example.com", "not_ana");

        start("ana@example.com");
        String code = codeIn(mail.await("ana@example.com", 2));
        assertThat(verify("ana@example.com", code, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.emailVerified")
                .isEqualTo(true);

        assertThat(login("ana@example.com", PASSWORD)).hasStatus(401);
        assertThat(me(squatter)).hasStatus(401);
        assertThat(jdbc.sql("select type from identity.auth_events order by created_at")
                        .query(String.class)
                        .list())
                .contains("PASSWORD_REMOVED", "SESSIONS_REVOKED", "EMAIL_VERIFIED");
    }

    @Test
    void aSignInCodeLeavesAVerifiedAccountAlone() {
        assertThat(register("ana@example.com", "ana_reads")).hasStatus(202);
        verify("ana@example.com", codeIn(mail.await("ana@example.com")), null);
        Cookie other = login("ana@example.com", PASSWORD).getResponse().getCookie(AuthCookies.ACCESS);

        start("ana@example.com");
        assertThat(verify("ana@example.com", codeIn(mail.await("ana@example.com", 2)), null))
                .hasStatusOk();

        assertThat(login("ana@example.com", PASSWORD)).hasStatusOk();
        assertThat(me(other)).hasStatusOk();
    }

    @Test
    void resetsThePasswordAndEndsEverySession() {
        Cookie before = signUp("ana@example.com", "ana_reads");

        assertThat(forgot("nobody@example.com")).hasStatus(202);
        assertThat(forgot("ana@example.com")).hasStatus(202);
        String code = codeIn(mail.await("ana@example.com", 2));
        assertThat(mail.sentTo("nobody@example.com")).isEmpty();

        String newPassword = "a brand new passphrase";
        var reset = post(
                "/api/v1/auth/password/reset",
                Map.of("email", "ana@example.com", "code", code, "newPassword", newPassword));
        assertThat(reset).hasStatusOk();
        assertThat(reset).bodyJson().extractingPath("$.emailVerified").isEqualTo(true);
        assertThat(me(reset.getResponse().getCookie(AuthCookies.ACCESS))).hasStatusOk();

        assertThat(me(before)).hasStatus(401);
        assertThat(login("ana@example.com", PASSWORD)).hasStatus(401);
        assertThat(login("ana@example.com", newPassword)).hasStatusOk();

        // The reset code is spent.
        assertThat(post(
                        "/api/v1/auth/password/reset",
                        Map.of("email", "ana@example.com", "code", code, "newPassword", PASSWORD)))
                .hasStatus(400);
    }

    @Test
    void aSignInCodeDoesNotResetAPassword() {
        signUp("ana@example.com", "ana_reads");
        start("ana@example.com");
        String code = codeIn(mail.await("ana@example.com", 2));

        assertThat(post(
                        "/api/v1/auth/password/reset",
                        Map.of("email", "ana@example.com", "code", code, "newPassword", "a brand new passphrase")))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_code");
    }

    @Test
    void resendsTheConfirmationToASignedInUser() {
        Cookie access = signUp("ana@example.com", "ana_reads");

        assertThat(mvc.post().uri("/api/v1/me/email/verification").cookie(access))
                .hasStatus(403);

        Cookie csrf = csrfCookie();
        assertThat(mvc.post()
                        .uri("/api/v1/me/email/verification")
                        .cookie(access, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .hasStatus(202);
        var resent = mail.await("ana@example.com", 2);
        assertThat(resent.subject()).isEqualTo("Confirm your email for CanReadIt");

        assertThat(verify("ana@example.com", codeIn(resent), null)).hasStatusOk();
    }

    @Test
    void cleanupDeletesExpiredCodesTokensAndOldEvents() {
        signUp("ana@example.com", "ana_reads");
        jdbc.sql("update identity.verification_codes set expires_at = now() - interval '2 days'")
                .update();
        jdbc.sql("update identity.refresh_tokens set expires_at = now() - interval '1 minute'")
                .update();
        jdbc.sql("update identity.auth_events set created_at = now() - interval '181 days' where type = 'REGISTERED'")
                .update();

        cleanup.run();

        assertThat(count("select count(*) from identity.verification_codes")).isZero();
        assertThat(count("select count(*) from identity.refresh_tokens")).isZero();
        assertThat(jdbc.sql("select type from identity.auth_events")
                        .query(String.class)
                        .list())
                .containsExactly("SIGNED_IN");
    }

    private MvcTestResult start(String email) {
        return post("/api/v1/auth/email/start", Map.of("email", email));
    }

    private MvcTestResult forgot(String email) {
        return post("/api/v1/auth/password/forgot", Map.of("email", email));
    }

    private MvcTestResult verify(String email, String code, @Nullable String handle) {
        var body = new HashMap<String, String>(Map.of("email", email, "code", code));
        if (handle != null) {
            body.put("handle", handle);
        }
        return post("/api/v1/auth/email/verify", body);
    }
}
