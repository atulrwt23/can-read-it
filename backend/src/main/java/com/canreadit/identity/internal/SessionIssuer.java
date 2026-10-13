package com.canreadit.identity.internal;

import com.canreadit.shared.Ids;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Opens, renews and ends sessions. Callers hold the transaction. */
@Component
class SessionIssuer {

    private final AuthSessions sessions;
    private final RefreshTokens refreshTokens;
    private final AccessTokens accessTokens;
    private final SessionDenylist denylist;
    private final AuthProperties properties;

    SessionIssuer(
            AuthSessions sessions,
            RefreshTokens refreshTokens,
            AccessTokens accessTokens,
            SessionDenylist denylist,
            AuthProperties properties) {
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.denylist = denylist;
        this.properties = properties;
    }

    /** A new session with a one-time refresh token and a short access token. */
    SignedIn open(UserAccount user, ClientInfo client, Instant now) {
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        var session = sessions.save(new AuthSession(Ids.newId(), user.getId(), client, now, expiresAt));
        return issue(user, session.getId(), now, expiresAt);
    }

    /** New tokens for an existing session (after a refresh). */
    SignedIn issue(UserAccount user, UUID sessionId, Instant now, Instant refreshExpiresAt) {
        String refresh = OpaqueTokens.generate();
        refreshTokens.save(new RefreshToken(Ids.newId(), sessionId, OpaqueTokens.hash(refresh), now, refreshExpiresAt));
        var access = accessTokens.issue(user.getId(), sessionId, user.roleNames(), user.emailVerified());
        return new SignedIn(user, access, refresh, refreshExpiresAt);
    }

    /** Ends one session: refresh stops working now, and its access tokens are refused from now on. */
    void revoke(AuthSession session, Instant now) {
        session.revoke(now);
        denylist.revoke(session.getId());
    }

    /** Ends every session of the user. Returns how many were still open. */
    int revokeAll(UUID userId, Instant now) {
        return revokeAllExcept(userId, null, now);
    }

    /** Ends every session of the user but one (the caller's own). Returns how many were still open. */
    int revokeAllExcept(UUID userId, @Nullable UUID keep, Instant now) {
        int open = 0;
        for (AuthSession session : sessions.findUnrevokedForUpdate(userId)) {
            if (session.getId().equals(keep)) {
                continue;
            }
            if (session.isActive(now)) {
                open++;
            }
            revoke(session, now);
        }
        return open;
    }
}
