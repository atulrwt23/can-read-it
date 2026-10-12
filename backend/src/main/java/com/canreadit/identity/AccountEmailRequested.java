package com.canreadit.identity;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Identity asks for an account email (a one-time code or a notice). It is published after the change commits and
 * handled asynchronously, so a response never takes longer because an email was sent. It is deliberately not a
 * persisted module event: it carries the plain code, which must not be written to the event publication table.
 *
 * @param to the recipient
 * @param code the 6-digit code, for the kinds that carry one
 * @param codeValidFor how long the code works
 */
public record AccountEmailRequested(
        String to, Kind kind, @Nullable String code, Duration codeValidFor) {

    public enum Kind {
        /** A code to sign in to an existing account. */
        SIGN_IN_CODE,
        /** A code that creates an account for an email that has none. */
        SIGN_UP_CODE,
        /** A code that confirms the email of an account registered with a password. */
        CONFIRM_EMAIL_CODE,
        /** A code to choose a new password. */
        PASSWORD_RESET_CODE,
        /** Someone tried to register with this email, which already has an account. No code. */
        ACCOUNT_EXISTS,
        /** The account will be erased after the grace period unless they sign in. No code. */
        DELETION_SCHEDULED
    }

    /** Keeps the code out of logs. */
    @Override
    public String toString() {
        return "AccountEmailRequested[kind=" + kind + ", code=" + (code == null ? "none" : "redacted") + "]";
    }
}
