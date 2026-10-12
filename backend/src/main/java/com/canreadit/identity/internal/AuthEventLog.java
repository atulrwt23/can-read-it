package com.canreadit.identity.internal;

import com.canreadit.shared.Ids;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The append-only audit log of sign-ins and session changes ({@code identity.auth_events}). It joins the caller's
 * transaction, except for failures, which are written even when the request fails.
 */
@Component
class AuthEventLog {

    enum Type {
        REGISTERED,
        SIGNED_IN,
        SIGN_IN_FAILED,
        SIGNED_OUT,
        REFRESH_REUSED,
        CODE_FAILED,
        EMAIL_VERIFIED,
        PASSWORD_RESET,
        /** A code proved the email of an account whose password was set by someone unproven; it was removed. */
        PASSWORD_REMOVED,
        SESSIONS_REVOKED,
        IDENTITY_LINKED,
        IDENTITY_UNLINKED,
        PASSWORD_CHANGED,
        PROFILE_CHANGED,
        DELETION_REQUESTED,
        DELETION_CANCELLED
    }

    private final JdbcClient jdbc;

    AuthEventLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void record(Type type, @Nullable UUID userId, ClientInfo client) {
        jdbc.sql("""
                        insert into identity.auth_events (id, user_id, type, ip, user_agent)
                        values (:id, :user, :type, cast(:ip as inet), :agent)
                        """)
                .param("id", Ids.newId())
                .param("user", userId)
                .param("type", type.name())
                .param("ip", client.ip() == null ? null : client.ip().getHostAddress())
                .param("agent", client.userAgent())
                .update();
    }
}
