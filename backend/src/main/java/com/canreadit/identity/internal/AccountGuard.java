package com.canreadit.identity.internal;

import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Account rules shared by every way of signing in. Callers hold the transaction. */
@Component
class AccountGuard {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HANDLE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private final UserAccounts users;
    private final Credentials credentials;
    private final SessionIssuer sessionIssuer;
    private final AuthEventLog events;

    AccountGuard(UserAccounts users, Credentials credentials, SessionIssuer sessionIssuer, AuthEventLog events) {
        this.users = users;
        this.credentials = credentials;
        this.sessionIssuer = sessionIssuer;
        this.events = events;
    }

    /**
     * The account was set up by someone who never proved they own its email, and the owner just did (an emailed
     * code, or a provider vouching for the email). Whatever that someone holds stops working: the password and
     * every session. This prevents account pre-hijacking (CLAUDE.md section 7, account linking).
     */
    void secureUnproven(UserAccount user, ClientInfo client, Instant now) {
        if (credentials.existsById(user.getId())) {
            credentials.deleteById(user.getId());
            events.record(AuthEventLog.Type.PASSWORD_REMOVED, user.getId(), client);
        }
        if (sessionIssuer.revokeAll(user.getId(), now) > 0) {
            events.record(AuthEventLog.Type.SESSIONS_REVOKED, user.getId(), client);
        }
    }

    /**
     * Whether the user may sign in. Signing in while the account waits for deletion cancels the deletion (the
     * grace period exists for exactly that). Suspended, banned and deleted accounts may not.
     */
    boolean admit(UserAccount user, ClientInfo client, Instant now) {
        return switch (user.status()) {
            case ACTIVE -> true;
            case PENDING_DELETION -> {
                user.cancelDeletion(now);
                events.record(AuthEventLog.Type.DELETION_CANCELLED, user.getId(), client);
                yield true;
            }
            case SUSPENDED, BANNED, DELETED -> false;
        };
    }

    /** Whether emails that lead to a sign-in (codes, resets) may go to this account. */
    static boolean mayReturn(UserAccount user) {
        return user.status() == UserStatus.ACTIVE || user.status() == UserStatus.PENDING_DELETION;
    }

    /**
     * A random handle for accounts made without a sign-up form. It never comes from the email, which would make part
     * of a private address public. TODO(product): let readers choose it at sign-up (the web form can send one).
     */
    String newHandle() {
        return randomHandle("reader_");
    }

    String randomHandle(String prefix) {
        for (int i = 0; i < 5; i++) {
            var handle = new StringBuilder(prefix);
            for (int j = 0; j < 8; j++) {
                handle.append(HANDLE_ALPHABET.charAt(RANDOM.nextInt(HANDLE_ALPHABET.length())));
            }
            if (!users.handleTaken(handle.toString())) {
                return handle.toString();
            }
        }
        throw new IllegalStateException("Could not find a free handle");
    }
}
