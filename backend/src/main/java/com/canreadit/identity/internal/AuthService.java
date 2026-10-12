package com.canreadit.identity.internal;

import com.canreadit.identity.UserRegistered;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Password sign-in and sessions (CLAUDE.md section 7). Every success opens a session with a one-time refresh token
 * and a short access token. Failures are logged in their own commit, so the audit trail survives the error.
 */
@Service
class AuthService {

    static final RateLimit REGISTER_PER_IP = new RateLimit("register-ip", 10, Duration.ofHours(1));
    static final RateLimit LOGIN_PER_IP = new RateLimit("login-ip", 30, Duration.ofMinutes(15));
    static final RateLimit LOGIN_PER_EMAIL = new RateLimit("login-email", 10, Duration.ofMinutes(15));
    static final RateLimit REFRESH_PER_IP = new RateLimit("refresh-ip", 120, Duration.ofMinutes(1));

    /**
     * Two tabs can refresh with the same token at nearly the same moment. A reuse this soon after the first use is
     * that race, not theft: it gets no tokens, but the session survives and the client retries with the new cookie.
     */
    static final Duration REFRESH_RACE_GRACE = Duration.ofSeconds(20);

    private final UserAccounts users;
    private final Credentials credentials;
    private final AuthSessions sessions;
    private final RefreshTokens refreshTokens;
    private final PasswordHasher passwords;
    private final AccessTokens accessTokens;
    private final SessionDenylist denylist;
    private final LoginThrottle throttle;
    private final RateLimiter rateLimiter;
    private final BotChallengeVerifier botChallenge;
    private final AuthEventLog events;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final AuthProperties properties;
    private final Clock clock;

    AuthService(
            UserAccounts users,
            Credentials credentials,
            AuthSessions sessions,
            RefreshTokens refreshTokens,
            PasswordHasher passwords,
            AccessTokens accessTokens,
            SessionDenylist denylist,
            LoginThrottle throttle,
            RateLimiter rateLimiter,
            BotChallengeVerifier botChallenge,
            AuthEventLog events,
            ApplicationEventPublisher publisher,
            TransactionTemplate tx,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.denylist = denylist;
        this.throttle = throttle;
        this.rateLimiter = rateLimiter;
        this.botChallenge = botChallenge;
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /** A signed-in session: what the cookies (or a mobile client) carry. */
    record SignedIn(UserAccount user, AccessTokens.Issued access, String refreshToken, Instant refreshExpiresAt) {}

    SignedIn register(String email, String password, String handle, @Nullable String botToken, ClientInfo client) {
        rateLimiter.check(REGISTER_PER_IP, client.ipText());
        if (!botChallenge.verify(botToken, client.ipText())) {
            throw ApiException.badRequest("bot_check_failed", "The bot check failed. Reload the page and try again.");
        }
        String cleanEmail = email.strip();
        // TODO(product): this tells whether an email has an account, which CLAUDE.md section 7 rules out. Once
        // email codes exist (step 2b), registering answers the same for new and existing emails and the owner of an
        // existing account gets an email instead.
        if (users.emailTaken(cleanEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists.");
        }
        if (users.handleTaken(handle)) {
            throw new ApiException(HttpStatus.CONFLICT, "handle_taken", "This handle is taken.");
        }
        String hash = passwords.hash(password);
        try {
            return tx.execute(status -> {
                Instant now = clock.instant();
                var user = users.save(new UserAccount(Ids.newId(), cleanEmail, handle, now));
                credentials.save(new Credential(user.getId(), hash, now));
                events.record(AuthEventLog.Type.REGISTERED, user.getId(), client);
                publisher.publishEvent(new UserRegistered(user.getId(), user.handle(), now));
                return openSession(user, client, now);
            });
        } catch (DataIntegrityViolationException e) {
            // Someone took the email or handle between the checks and the insert.
            throw new ApiException(HttpStatus.CONFLICT, "account_conflict", "This email or handle was just taken.");
        }
    }

    SignedIn login(String email, String password, ClientInfo client) {
        String cleanEmail = email.strip();
        rateLimiter.check(LOGIN_PER_IP, client.ipText());
        rateLimiter.check(LOGIN_PER_EMAIL, LoginThrottle.normalize(cleanEmail));
        throttle.check(cleanEmail);

        Optional<UserAccount> user = users.findByEmail(cleanEmail);
        Optional<Credential> credential = user.flatMap(u -> credentials.findById(u.getId()));
        boolean valid = credential.isPresent()
                ? passwords.matches(password, credential.get().passwordHash())
                : passwords.matchesNothing(password);
        if (!valid) {
            throttle.failed(cleanEmail);
            events.record(
                    AuthEventLog.Type.SIGN_IN_FAILED,
                    user.map(UserAccount::getId).orElse(null),
                    client);
            throw invalidCredentials();
        }
        UserAccount account = user.orElseThrow();
        if (account.status() != UserStatus.ACTIVE) {
            // The password was right, so saying why reveals nothing to a guesser.
            throw new ApiException(HttpStatus.FORBIDDEN, "account_unavailable", "This account can't sign in.");
        }
        throttle.succeeded(cleanEmail);
        return tx.execute(status -> {
            Instant now = clock.instant();
            events.record(AuthEventLog.Type.SIGNED_IN, account.getId(), client);
            return openSession(account, client, now);
        });
    }

    /**
     * Rotates a refresh token: the presented one is spent and a new one issued. Presenting a spent token again
     * (outside the short race window) means it was copied, so the whole session ends.
     */
    SignedIn refresh(String presentedToken, ClientInfo client) {
        rateLimiter.check(REFRESH_PER_IP, client.ipText());
        String hash = OpaqueTokens.hash(presentedToken);
        // A failure still commits (a reused token revokes its session), so it comes back as a value, not a throw.
        RefreshOutcome outcome = tx.execute(status -> rotate(hash, client));
        if (outcome.error() != null) {
            throw outcome.error();
        }
        return Objects.requireNonNull(outcome.signedIn());
    }

    private record RefreshOutcome(
            @Nullable SignedIn signedIn, @Nullable ApiException error) {

        static RefreshOutcome succeeded(SignedIn signedIn) {
            return new RefreshOutcome(signedIn, null);
        }

        static RefreshOutcome failed(ApiException error) {
            return new RefreshOutcome(null, error);
        }
    }

    private RefreshOutcome rotate(String hash, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByHashForUpdate(hash).orElse(null);
        if (token == null) {
            return RefreshOutcome.failed(invalidRefresh());
        }
        AuthSession session = sessions.findForUpdate(token.sessionId()).orElse(null);
        if (session == null || !session.isActive(now)) {
            return RefreshOutcome.failed(invalidRefresh());
        }
        Instant usedAt = token.usedAt();
        if (usedAt != null) {
            if (usedAt.plus(REFRESH_RACE_GRACE).isAfter(now)) {
                return RefreshOutcome.failed(new ApiException(
                        HttpStatus.CONFLICT, "refresh_superseded", "This session was just refreshed. Try again."));
            }
            session.revoke(now);
            denylist.revoke(session.getId());
            events.record(AuthEventLog.Type.REFRESH_REUSED, session.userId(), client);
            return RefreshOutcome.failed(invalidRefresh());
        }
        if (token.isExpired(now)) {
            return RefreshOutcome.failed(invalidRefresh());
        }
        UserAccount user = users.findById(session.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            session.revoke(now);
            denylist.revoke(session.getId());
            return RefreshOutcome.failed(invalidRefresh());
        }
        token.markUsed(now);
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        session.extend(now, expiresAt);
        return RefreshOutcome.succeeded(issue(user, session.getId(), now, expiresAt));
    }

    /** Ends the session the access token belongs to. Signing out always succeeds, even without a session. */
    void logout(@Nullable String accessToken, ClientInfo client) {
        if (accessToken == null) {
            return;
        }
        Optional<UUID> sessionId = accessTokens.sessionIgnoringExpiry(accessToken);
        if (sessionId.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(status -> sessions.findForUpdate(sessionId.get())
                .ifPresent(session -> {
                    session.revoke(clock.instant());
                    events.record(AuthEventLog.Type.SIGNED_OUT, session.userId(), client);
                }));
        denylist.revoke(sessionId.get());
    }

    Optional<UserAccount> user(UUID id) {
        return users.findById(id);
    }

    private SignedIn openSession(UserAccount user, ClientInfo client, Instant now) {
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        var session = sessions.save(new AuthSession(Ids.newId(), user.getId(), client, now, expiresAt));
        return issue(user, session.getId(), now, expiresAt);
    }

    private SignedIn issue(UserAccount user, UUID sessionId, Instant now, Instant refreshExpiresAt) {
        String refresh = OpaqueTokens.generate();
        refreshTokens.save(new RefreshToken(Ids.newId(), sessionId, OpaqueTokens.hash(refresh), now, refreshExpiresAt));
        var access = accessTokens.issue(user.getId(), sessionId, user.roleNames(), user.emailVerified());
        return new SignedIn(user, access, refresh, refreshExpiresAt);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "The email or password is wrong.");
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again.");
    }
}
