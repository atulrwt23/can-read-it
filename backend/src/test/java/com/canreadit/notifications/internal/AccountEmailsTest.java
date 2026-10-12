package com.canreadit.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.identity.AccountEmailRequested;
import com.canreadit.identity.AccountEmailRequested.Kind;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountEmailsTest {

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = "ACCOUNT_EXISTS", mode = EnumSource.Mode.EXCLUDE)
    void codeEmailsShowTheCodeAndItsLifetime(Kind kind) {
        var email = AccountEmails.render(
                new AccountEmailRequested("ana@example.com", kind, "042917", Duration.ofMinutes(10)), "CanReadIt");

        assertThat(email.to()).isEqualTo("ana@example.com");
        assertThat(email.subject()).contains("CanReadIt").doesNotContain("042917");
        assertThat(email.text()).contains("\n042917\n").contains("10 minutes");
        assertThat(email.toString()).doesNotContain("042917");
    }

    @Test
    void theAccountExistsNoticeHasNoCode() {
        var email = AccountEmails.render(
                new AccountEmailRequested("ana@example.com", Kind.ACCOUNT_EXISTS, null, Duration.ZERO), "CanReadIt");

        assertThat(email.subject()).isEqualTo("You already have a CanReadIt account");
        assertThat(email.text()).doesNotContainPattern("\\d{6}").contains("reset it");
    }

    @Test
    void requestsNeverPrintTheirCode() {
        assertThat(new AccountEmailRequested("ana@example.com", Kind.SIGN_IN_CODE, "042917", Duration.ofMinutes(10))
                        .toString())
                .doesNotContain("042917")
                .doesNotContain("ana@example.com");
    }
}
