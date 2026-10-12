package com.canreadit.identity.internal;

import com.canreadit.identity.AccountEmailRequested;
import com.canreadit.identity.PasswordChanged;
import com.canreadit.identity.UserDeletionRequested;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The signed-in user's own account (CLAUDE.md section 7, {@code /me}): profile, password, sessions and deletion.
 *
 * <p>Changing the password or deleting the account proves who is asking: with the current password when there is
 * one, otherwise with a sign-in from the last {@link #RECENT_SIGN_IN} (a stolen session alone must not be able to
 * lock the owner out). This is also the seam for staff re-authentication.
 */
@Service
class AccountService {

    static final Duration RECENT_SIGN_IN = Duration.ofMinutes(10);
    static final Duration DELETION_GRACE = Duration.ofDays(30);
    static final RateLimit PROFILE_PER_USER = new RateLimit("profile-user", 20, Duration.ofHours(1));
    static final RateLimit PASSWORD_CHECK_PER_USER = new RateLimit("password-check-user", 10, Duration.ofMinutes(15));

    private final UserAccounts users;
    private final Credentials credentials;
    private final AuthSessions sessions;
    private final SessionIssuer sessionIssuer;
    private final PasswordHasher passwords;
    private final RateLimiter rateLimiter;
    private final AuthEventLog events;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    AccountService(
            UserAccounts users,
            Credentials credentials,
            AuthSessions sessions,
            SessionIssuer sessionIssuer,
            PasswordHasher passwords,
            RateLimiter rateLimiter,
            AuthEventLog events,
            ApplicationEventPublisher publisher,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.sessionIssuer = sessionIssuer;
        this.passwords = passwords;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    /** Who is calling: the user and the session of their access token. */
    record Caller(UUID userId, UUID sessionId, ClientInfo client) {}

    UserAccount user(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in again."));
    }

    /** Changes the handle and/or the display name. */
    UserAccount updateProfile(Caller caller, @Nullable String handle, @Nullable String displayName) {
        rateLimiter.check(PROFILE_PER_USER, caller.userId().toString());
        try {
            return Objects.requireNonNull(tx.execute(status -> {
                        Instant now = clock.instant();
                        UserAccount user = user(caller.userId());
                        if (handle != null && !handle.equalsIgnoreCase(user.handle())) {
                            if (users.handleTaken(handle)) {
                                return Outcome.<UserAccount>failed(AuthErrors.handleTaken());
                            }
                            user.rename(handle, now);
                        } else if (handle != null) {
                            // Only the letter case changed.
                            user.rename(handle, now);
                        }
                        if (displayName != null) {
                            user.changeDisplayName(displayName.strip(), now);
                        }
                        events.record(AuthEventLog.Type.PROFILE_CHANGED, user.getId(), caller.client());
                        return Outcome.ok(user);
                    }))
                    .get();
        } catch (DataIntegrityViolationException e) {
            // The handle was taken between the check and the update.
            if (handle != null && users.handleTaken(handle)) {
                throw AuthErrors.handleTaken();
            }
            throw e;
        }
    }

    /** Sets or changes the password and ends every other session (CLAUDE.md section 7). */
    void changePassword(Caller caller, @Nullable String currentPassword, String newPassword) {
        String hash = passwords.hash(newPassword);
        Objects.requireNonNull(tx.execute(status -> {
                    Instant now = clock.instant();
                    Optional<ApiException> denied = proveIdentity(caller, currentPassword, now);
                    if (denied.isPresent()) {
                        return Outcome.<Boolean>failed(denied.get());
                    }
                    credentials
                            .findById(caller.userId())
                            .ifPresentOrElse(
                                    credential -> credential.change(hash, now),
                                    () -> credentials.save(new Credential(caller.userId(), hash, now)));
                    sessionIssuer.revokeAllExcept(caller.userId(), caller.sessionId(), now);
                    events.record(AuthEventLog.Type.PASSWORD_CHANGED, caller.userId(), caller.client());
                    publisher.publishEvent(new PasswordChanged(caller.userId(), now));
                    return Outcome.ok(Boolean.TRUE);
                }))
                .get();
    }

    /** A signed-in device, as the account page lists it. */
    record SessionView(
            UUID id,
            boolean current,
            @Nullable String userAgent,
            @Nullable String ip,
            Instant createdAt,
            Instant lastUsedAt) {}

    List<SessionView> sessions(Caller caller) {
        Instant now = clock.instant();
        return sessions.findUnrevokedByUser(caller.userId()).stream()
                .filter(session -> session.isActive(now))
                .sorted(Comparator.comparing(AuthSession::lastUsedAt).reversed())
                .map(session -> new SessionView(
                        session.getId(),
                        session.getId().equals(caller.sessionId()),
                        session.userAgent(),
                        session.ip() == null ? null : session.ip().getHostAddress(),
                        session.createdAt(),
                        session.lastUsedAt()))
                .toList();
    }

    /** Signs one of the user's sessions out. Returns whether it was the caller's own. */
    boolean revokeSession(Caller caller, UUID sessionId) {
        return Objects.requireNonNull(tx.execute(status -> {
                    AuthSession session = sessions.findForUpdate(sessionId)
                            .filter(s -> s.userId().equals(caller.userId()))
                            .orElse(null);
                    if (session == null) {
                        return Outcome.<Boolean>failed(ApiException.notFound("session_not_found", "No such session."));
                    }
                    sessionIssuer.revoke(session, clock.instant());
                    events.record(AuthEventLog.Type.SIGNED_OUT, caller.userId(), caller.client());
                    return Outcome.ok(sessionId.equals(caller.sessionId()));
                }))
                .get();
    }

    /** Signs out every other device. */
    void revokeOtherSessions(Caller caller) {
        tx.executeWithoutResult(status -> {
            if (sessionIssuer.revokeAllExcept(caller.userId(), caller.sessionId(), clock.instant()) > 0) {
                events.record(AuthEventLog.Type.SESSIONS_REVOKED, caller.userId(), caller.client());
            }
        });
    }

    /**
     * Starts the deletion: every session ends now, and the account is erased after {@link #DELETION_GRACE} unless the
     * user signs in again before then. TODO(product): confirm the 30-day grace period and what is kept.
     */
    Instant requestDeletion(Caller caller, @Nullable String currentPassword) {
        Deletion deletion = Objects.requireNonNull(tx.execute(status -> {
                    Instant now = clock.instant();
                    Optional<ApiException> denied = proveIdentity(caller, currentPassword, now);
                    if (denied.isPresent()) {
                        return Outcome.<Deletion>failed(denied.get());
                    }
                    UserAccount user = user(caller.userId());
                    user.requestDeletion(now);
                    sessionIssuer.revokeAll(user.getId(), now);
                    events.record(AuthEventLog.Type.DELETION_REQUESTED, user.getId(), caller.client());
                    Instant eraseAfter = now.plus(DELETION_GRACE);
                    publisher.publishEvent(new UserDeletionRequested(user.getId(), now, eraseAfter));
                    return Outcome.ok(new Deletion(eraseAfter, user.email()));
                }))
                .get();
        if (deletion.email() != null) {
            publisher.publishEvent(new AccountEmailRequested(
                    deletion.email(), AccountEmailRequested.Kind.DELETION_SCHEDULED, null, DELETION_GRACE));
        }
        return deletion.eraseAfter();
    }

    private record Deletion(Instant eraseAfter, @Nullable String email) {}

    /**
     * The current password if the account has one; otherwise a sign-in within {@link #RECENT_SIGN_IN}. Wrong
     * passwords are rate limited per user, so a stolen session can't be used to guess.
     */
    private Optional<ApiException> proveIdentity(Caller caller, @Nullable String currentPassword, Instant now) {
        Optional<Credential> credential = credentials.findById(caller.userId());
        if (credential.isPresent()) {
            rateLimiter.check(PASSWORD_CHECK_PER_USER, caller.userId().toString());
            if (currentPassword == null
                    || !passwords.matches(currentPassword, credential.get().passwordHash())) {
                return Optional.of(
                        new ApiException(HttpStatus.FORBIDDEN, "wrong_password", "The current password is wrong."));
            }
            return Optional.empty();
        }
        boolean recent = sessions.findById(caller.sessionId())
                .map(session -> session.createdAt().isAfter(now.minus(RECENT_SIGN_IN)))
                .orElse(false);
        return recent
                ? Optional.empty()
                : Optional.of(new ApiException(
                        HttpStatus.FORBIDDEN,
                        "reauthentication_required",
                        "For your security, sign in again and retry within 10 minutes."));
    }
}
