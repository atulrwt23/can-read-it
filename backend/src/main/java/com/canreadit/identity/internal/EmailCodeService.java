package com.canreadit.identity.internal;

import com.canreadit.identity.AccountEmailRequested;
import com.canreadit.identity.AccountEmailRequested.Kind;
import com.canreadit.identity.EmailVerified;
import com.canreadit.identity.PasswordChanged;
import com.canreadit.identity.UserRegistered;
import com.canreadit.identity.internal.VerificationCodes.Purpose;
import com.canreadit.shared.Ids;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-in with an emailed one-time code, email confirmation and password reset (CLAUDE.md section 7). Requests
 * that start a flow always answer the same, whether or not the email has an account; the code goes by email after
 * the commit, so timing doesn't tell either.
 *
 * <p>A valid code proves the caller owns the email. Signing in with one to an account whose email was never
 * proven therefore removes that account's password and ends its sessions: whoever set them may have registered
 * someone else's email in advance (account pre-hijacking, section 7's linking rule applied to codes).
 */
@Service
class EmailCodeService {

    static final RateLimit START_PER_IP = new RateLimit("code-start-ip", 20, Duration.ofHours(1));
    static final RateLimit START_PER_EMAIL = new RateLimit("code-start-email", 5, Duration.ofHours(1));
    static final RateLimit VERIFY_PER_IP = new RateLimit("code-verify-ip", 30, Duration.ofMinutes(15));
    static final RateLimit RESEND_PER_USER = new RateLimit("code-resend-user", 5, Duration.ofHours(1));

    private static final List<Purpose> SIGN_IN_CODES = List.of(Purpose.SIGN_IN, Purpose.VERIFY_EMAIL);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HANDLE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private final UserAccounts users;
    private final Credentials credentials;
    private final VerificationCodes codes;
    private final SessionIssuer sessionIssuer;
    private final PasswordHasher passwords;
    private final LoginThrottle throttle;
    private final RateLimiter rateLimiter;
    private final BotChallengeVerifier botChallenge;
    private final AuthEventLog events;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    EmailCodeService(
            UserAccounts users,
            Credentials credentials,
            VerificationCodes codes,
            SessionIssuer sessionIssuer,
            PasswordHasher passwords,
            LoginThrottle throttle,
            RateLimiter rateLimiter,
            BotChallengeVerifier botChallenge,
            AuthEventLog events,
            ApplicationEventPublisher publisher,
            TransactionTemplate tx,
            Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.codes = codes;
        this.sessionIssuer = sessionIssuer;
        this.passwords = passwords;
        this.throttle = throttle;
        this.rateLimiter = rateLimiter;
        this.botChallenge = botChallenge;
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    /** Emails a sign-in code, or a sign-up code when the email has no account. */
    void start(String email, @Nullable String botToken, ClientInfo client) {
        String cleanEmail = beginPublicRequest(email, botToken, client);
        Optional<AccountEmailRequested> message = Objects.requireNonNull(tx.execute(status -> {
            Optional<UserAccount> user = users.findByEmail(cleanEmail);
            if (user.isPresent() && user.get().status() != UserStatus.ACTIVE) {
                return Optional.<AccountEmailRequested>empty();
            }
            String code = codes.issue(cleanEmail, Purpose.SIGN_IN, clock.instant());
            Kind kind = user.isPresent() ? Kind.SIGN_IN_CODE : Kind.SIGN_UP_CODE;
            return Optional.of(new AccountEmailRequested(cleanEmail, kind, code, VerificationCodes.LIFETIME));
        }));
        message.ifPresent(publisher::publishEvent);
    }

    /**
     * Signs in with a code from {@link #start} or from registration. A sign-up code creates the account, with
     * {@code handle} if given (otherwise a generated one, changeable later).
     */
    SignedIn verify(String email, String code, @Nullable String handle, ClientInfo client) {
        String cleanEmail = email.strip();
        rateLimiter.check(VERIFY_PER_IP, client.ipText());
        try {
            return Objects.requireNonNull(
                            tx.execute(status -> verifyInTransaction(cleanEmail, code, handle, client, status)))
                    .get();
        } catch (DataIntegrityViolationException e) {
            // The handle (or the email) was taken between the check and the insert; the code stays unused.
            throw AuthErrors.handleTaken();
        }
    }

    private Outcome<SignedIn> verifyInTransaction(
            String email, String code, @Nullable String handle, ClientInfo client, TransactionStatus status) {
        Instant now = clock.instant();
        Optional<UserAccount> existing = users.findByEmail(email);
        Optional<Purpose> purpose = codes.consume(email, SIGN_IN_CODES, code, now);
        if (purpose.isEmpty()) {
            events.record(
                    AuthEventLog.Type.CODE_FAILED,
                    existing.map(UserAccount::getId).orElse(null),
                    client);
            return Outcome.failed(AuthErrors.invalidCode());
        }
        if (existing.isEmpty()) {
            if (purpose.get() != Purpose.SIGN_IN) {
                // A confirmation code whose account has since been deleted.
                return Outcome.failed(AuthErrors.invalidCode());
            }
            String newHandle = handle != null ? handle : generateHandle();
            if (handle != null && users.handleTaken(handle)) {
                // Keep the code usable, so the reader can pick another handle.
                status.setRollbackOnly();
                return Outcome.failed(AuthErrors.handleTaken());
            }
            var user = users.save(new UserAccount(Ids.newId(), email, newHandle, now, true));
            events.record(AuthEventLog.Type.REGISTERED, user.getId(), client);
            publisher.publishEvent(new UserRegistered(user.getId(), user.handle(), now));
            publisher.publishEvent(new EmailVerified(user.getId(), now));
            return Outcome.ok(signIn(user, client, now));
        }
        UserAccount user = existing.get();
        if (user.status() != UserStatus.ACTIVE) {
            return Outcome.failed(AuthErrors.accountUnavailable());
        }
        if (!user.emailVerified() && purpose.get() == Purpose.SIGN_IN) {
            secureUnprovenAccount(user, client, now);
        }
        if (user.markEmailVerified(now)) {
            events.record(AuthEventLog.Type.EMAIL_VERIFIED, user.getId(), client);
            publisher.publishEvent(new EmailVerified(user.getId(), now));
        }
        return Outcome.ok(signIn(user, client, now));
    }

    /**
     * The account was set up by someone who never proved they own the email, and the owner just did. Whatever that
     * someone holds stops working: the password and every session.
     */
    private void secureUnprovenAccount(UserAccount user, ClientInfo client, Instant now) {
        if (credentials.existsById(user.getId())) {
            credentials.deleteById(user.getId());
            events.record(AuthEventLog.Type.PASSWORD_REMOVED, user.getId(), client);
        }
        if (sessionIssuer.revokeAll(user.getId(), now) > 0) {
            events.record(AuthEventLog.Type.SESSIONS_REVOKED, user.getId(), client);
        }
    }

    /** Emails a password reset code if the email has an active account. Always answers the same. */
    void forgotPassword(String email, @Nullable String botToken, ClientInfo client) {
        String cleanEmail = beginPublicRequest(email, botToken, client);
        Optional<AccountEmailRequested> message =
                Objects.requireNonNull(tx.execute(status -> users.findByEmail(cleanEmail)
                        .filter(user -> user.status() == UserStatus.ACTIVE)
                        .map(user -> new AccountEmailRequested(
                                cleanEmail,
                                Kind.PASSWORD_RESET_CODE,
                                codes.issue(cleanEmail, Purpose.PASSWORD_RESET, clock.instant()),
                                VerificationCodes.LIFETIME))));
        message.ifPresent(publisher::publishEvent);
    }

    /**
     * Sets a new password with a reset code, ends every session (CLAUDE.md section 7) and signs in on this device.
     * The code also proves the email.
     */
    SignedIn resetPassword(String email, String code, String newPassword, ClientInfo client) {
        String cleanEmail = email.strip();
        rateLimiter.check(VERIFY_PER_IP, client.ipText());
        String hash = passwords.hash(newPassword);
        SignedIn signedIn = Objects.requireNonNull(tx.execute(status -> {
                    Instant now = clock.instant();
                    Optional<UserAccount> existing = users.findByEmail(cleanEmail);
                    if (codes.consume(cleanEmail, List.of(Purpose.PASSWORD_RESET), code, now)
                                    .isEmpty()
                            || existing.isEmpty()) {
                        events.record(
                                AuthEventLog.Type.CODE_FAILED,
                                existing.map(UserAccount::getId).orElse(null),
                                client);
                        return Outcome.<SignedIn>failed(AuthErrors.invalidCode());
                    }
                    UserAccount user = existing.get();
                    if (user.status() != UserStatus.ACTIVE) {
                        return Outcome.<SignedIn>failed(AuthErrors.accountUnavailable());
                    }
                    credentials
                            .findById(user.getId())
                            .ifPresentOrElse(
                                    credential -> credential.change(hash, now),
                                    () -> credentials.save(new Credential(user.getId(), hash, now)));
                    sessionIssuer.revokeAll(user.getId(), now);
                    events.record(AuthEventLog.Type.PASSWORD_RESET, user.getId(), client);
                    publisher.publishEvent(new PasswordChanged(user.getId(), now));
                    if (user.markEmailVerified(now)) {
                        events.record(AuthEventLog.Type.EMAIL_VERIFIED, user.getId(), client);
                        publisher.publishEvent(new EmailVerified(user.getId(), now));
                    }
                    return Outcome.ok(signIn(user, client, now));
                }))
                .get();
        throttle.succeeded(cleanEmail);
        return signedIn;
    }

    /** Emails a new confirmation code to a signed-in user whose email is not yet proven. */
    void resendConfirmation(UUID userId, ClientInfo client) {
        rateLimiter.check(RESEND_PER_USER, userId.toString());
        Optional<AccountEmailRequested> message = Objects.requireNonNull(tx.execute(status -> users.findById(userId)
                .filter(user -> user.status() == UserStatus.ACTIVE && !user.emailVerified() && user.email() != null)
                .map(user -> {
                    String email = Objects.requireNonNull(user.email());
                    return new AccountEmailRequested(
                            email,
                            Kind.CONFIRM_EMAIL_CODE,
                            codes.issue(email, Purpose.VERIFY_EMAIL, clock.instant()),
                            VerificationCodes.LIFETIME);
                })));
        message.ifPresent(publisher::publishEvent);
    }

    private String beginPublicRequest(String email, @Nullable String botToken, ClientInfo client) {
        String cleanEmail = email.strip();
        rateLimiter.check(START_PER_IP, client.ipText());
        rateLimiter.check(START_PER_EMAIL, LoginThrottle.normalize(cleanEmail));
        if (!botChallenge.verify(botToken, client.ipText())) {
            throw AuthErrors.botCheckFailed();
        }
        return cleanEmail;
    }

    private SignedIn signIn(UserAccount user, ClientInfo client, Instant now) {
        events.record(AuthEventLog.Type.SIGNED_IN, user.getId(), client);
        return sessionIssuer.open(user, client, now);
    }

    /**
     * A random handle for accounts made with a code. It never comes from the email, which would make part of a
     * private address public. TODO(product): let readers choose it at sign-up (the web form can send one).
     */
    private String generateHandle() {
        for (int i = 0; i < 5; i++) {
            var handle = new StringBuilder("reader_");
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
