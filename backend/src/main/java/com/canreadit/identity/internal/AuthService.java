package com.canreadit.identity.internal;

import com.canreadit.identity.AccountEmailRequested;
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
 * Password sign-up and sign-in, refresh and sign-out (CLAUDE.md section 7). Every sign-in opens a session with a
 * one-time refresh token and a short access token. Failures are logged in their own commit, so the audit trail
 * survives the error.
 */
@Service
class AuthService {

    static final RateLimit REGISTER_PER_IP = new RateLimit("register-ip", 10, Duration.ofHours(1));
    static final RateLimit REGISTER_PER_EMAIL = new RateLimit("register-email", 5, Duration.ofHours(1));
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
    private final SessionIssuer sessionIssuer;
    private final VerificationCodes codes;
    private final PasswordHasher passwords;
    private final AccessTokens accessTokens;
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
            SessionIssuer sessionIssuer,
            VerificationCodes codes,
            PasswordHasher passwords,
            AccessTokens accessTokens,
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
        this.sessionIssuer = sessionIssuer;
        this.codes = codes;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.throttle = throttle;
        this.rateLimiter = rateLimiter;
        this.botChallenge = botChallenge;
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates an account with a password and emails a code that confirms the email and signs in. The answer is the
     * same whether or not the email already has an account (CLAUDE.md section 7): an existing owner gets a notice
     * by email instead, and both paths hash the password, so they take as long. Only a taken handle is reported,
     * because handles are public anyway.
     */
    void register(String email, String password, String handle, @Nullable String botToken, ClientInfo client) {
        String cleanEmail = email.strip();
        rateLimiter.check(REGISTER_PER_IP, client.ipText());
        rateLimiter.check(REGISTER_PER_EMAIL, LoginThrottle.normalize(cleanEmail));
        if (!botChallenge.verify(botToken, client.ipText())) {
            throw AuthErrors.botCheckFailed();
        }
        if (users.handleTaken(handle)) {
            throw AuthErrors.handleTaken();
        }
        String hash = passwords.hash(password);
        AccountEmailRequested message;
        try {
            message = Objects.requireNonNull(tx.execute(status -> {
                if (users.emailTaken(cleanEmail)) {
                    return new AccountEmailRequested(
                            cleanEmail, AccountEmailRequested.Kind.ACCOUNT_EXISTS, null, Duration.ZERO);
                }
                Instant now = clock.instant();
                var user = users.save(new UserAccount(Ids.newId(), cleanEmail, handle, now));
                credentials.save(new Credential(user.getId(), hash, now));
                events.record(AuthEventLog.Type.REGISTERED, user.getId(), client);
                publisher.publishEvent(new UserRegistered(user.getId(), user.handle(), now));
                String code = codes.issue(cleanEmail, VerificationCodes.Purpose.VERIFY_EMAIL, now);
                return new AccountEmailRequested(
                        cleanEmail, AccountEmailRequested.Kind.CONFIRM_EMAIL_CODE, code, VerificationCodes.LIFETIME);
            }));
        } catch (DataIntegrityViolationException e) {
            // Someone took the handle or the email between the checks and the insert.
            if (users.handleTaken(handle)) {
                throw AuthErrors.handleTaken();
            }
            return;
        }
        publisher.publishEvent(message);
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
            throw AuthErrors.invalidCredentials();
        }
        UserAccount account = user.orElseThrow();
        if (account.status() != UserStatus.ACTIVE) {
            // The password was right, so saying why reveals nothing to a guesser.
            throw AuthErrors.accountUnavailable();
        }
        throttle.succeeded(cleanEmail);
        return Objects.requireNonNull(tx.execute(status -> {
            events.record(AuthEventLog.Type.SIGNED_IN, account.getId(), client);
            return sessionIssuer.open(account, client, clock.instant());
        }));
    }

    /**
     * Rotates a refresh token: the presented one is spent and a new one issued. Presenting a spent token again
     * (outside the short race window) means it was copied, so the whole session ends.
     */
    SignedIn refresh(String presentedToken, ClientInfo client) {
        rateLimiter.check(REFRESH_PER_IP, client.ipText());
        String hash = OpaqueTokens.hash(presentedToken);
        return Objects.requireNonNull(tx.execute(status -> rotate(hash, client)))
                .get();
    }

    private Outcome<SignedIn> rotate(String hash, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByHashForUpdate(hash).orElse(null);
        if (token == null) {
            return Outcome.failed(AuthErrors.invalidRefresh());
        }
        AuthSession session = sessions.findForUpdate(token.sessionId()).orElse(null);
        if (session == null || !session.isActive(now)) {
            return Outcome.failed(AuthErrors.invalidRefresh());
        }
        Instant usedAt = token.usedAt();
        if (usedAt != null) {
            if (usedAt.plus(REFRESH_RACE_GRACE).isAfter(now)) {
                return Outcome.failed(new ApiException(
                        HttpStatus.CONFLICT, "refresh_superseded", "This session was just refreshed. Try again."));
            }
            sessionIssuer.revoke(session, now);
            events.record(AuthEventLog.Type.REFRESH_REUSED, session.userId(), client);
            return Outcome.failed(AuthErrors.invalidRefresh());
        }
        if (token.isExpired(now)) {
            return Outcome.failed(AuthErrors.invalidRefresh());
        }
        UserAccount user = users.findById(session.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            sessionIssuer.revoke(session, now);
            return Outcome.failed(AuthErrors.invalidRefresh());
        }
        token.markUsed(now);
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        session.extend(now, expiresAt);
        return Outcome.ok(sessionIssuer.issue(user, session.getId(), now, expiresAt));
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
                    sessionIssuer.revoke(session, clock.instant());
                    events.record(AuthEventLog.Type.SIGNED_OUT, session.userId(), client);
                }));
    }

    Optional<UserAccount> user(UUID id) {
        return users.findById(id);
    }
}
