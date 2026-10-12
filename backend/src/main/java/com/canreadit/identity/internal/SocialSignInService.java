package com.canreadit.identity.internal;

import com.canreadit.identity.EmailVerified;
import com.canreadit.identity.UserRegistered;
import com.canreadit.identity.internal.IdTokenVerifier.VerifiedIdToken;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-in with Google or Apple, and linking them to an account (CLAUDE.md section 7). Users are matched on
 * (provider, subject), never on email alone. An email links an existing account only when the provider says it is
 * verified, and then:
 *
 * <ul>
 *   <li>if the account's email is verified too, the provider is linked;
 *   <li>if not, the provider wins: it is linked, the email becomes verified, and the account's password and sessions
 *       are removed, since whoever set them never proved they own the email (account pre-hijacking).
 * </ul>
 */
@Service
class SocialSignInService {

    static final RateLimit NONCE_PER_IP = new RateLimit("oauth-nonce-ip", 60, Duration.ofMinutes(15));
    static final RateLimit SIGN_IN_PER_IP = new RateLimit("oauth-ip", 30, Duration.ofMinutes(15));

    private final IdTokenVerifier verifier;
    private final OAuthNonces nonces;
    private final UserAccounts users;
    private final UserIdentities identities;
    private final Credentials credentials;
    private final AccountGuard guard;
    private final SessionIssuer sessionIssuer;
    private final RateLimiter rateLimiter;
    private final AuthEventLog events;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    SocialSignInService(
            IdTokenVerifier verifier,
            OAuthNonces nonces,
            UserAccounts users,
            UserIdentities identities,
            Credentials credentials,
            AccountGuard guard,
            SessionIssuer sessionIssuer,
            RateLimiter rateLimiter,
            AuthEventLog events,
            ApplicationEventPublisher publisher,
            TransactionTemplate tx,
            Clock clock) {
        this.verifier = verifier;
        this.nonces = nonces;
        this.users = users;
        this.identities = identities;
        this.credentials = credentials;
        this.guard = guard;
        this.sessionIssuer = sessionIssuer;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.publisher = publisher;
        this.tx = tx;
        this.clock = clock;
    }

    /** A nonce for the provider's SDK, valid once for {@link OAuthNonces#LIFETIME}. */
    String nonce(ClientInfo client) {
        rateLimiter.check(NONCE_PER_IP, client.ipText());
        return nonces.issue();
    }

    SignedIn signIn(IdentityProvider provider, String idToken, String nonce, ClientInfo client) {
        rateLimiter.check(SIGN_IN_PER_IP, client.ipText());
        VerifiedIdToken token = verified(provider, idToken, nonce);
        try {
            return Objects.requireNonNull(tx.execute(status -> signInInTransaction(provider, token, client)))
                    .get();
        } catch (DataIntegrityViolationException e) {
            // Two first sign-ins with the same account at once; the other one won.
            throw new ApiException(HttpStatus.CONFLICT, "try_again", "Sign-in collided with another one. Try again.");
        }
    }

    private Outcome<SignedIn> signInInTransaction(IdentityProvider provider, VerifiedIdToken token, ClientInfo client) {
        Instant now = clock.instant();
        Optional<UserIdentity> linked = identities.findByProviderAndSubject(provider, token.subject());
        if (linked.isPresent()) {
            UserIdentity identity = linked.get();
            identity.seen(token.email());
            UserAccount user = users.findById(identity.userId()).orElseThrow();
            return admit(user, client, now);
        }
        if (token.email() != null && token.emailVerified()) {
            Optional<UserAccount> sameEmail = users.findByEmail(token.email());
            if (sameEmail.isPresent()) {
                UserAccount user = sameEmail.get();
                if (!guard.admit(user, client, now)) {
                    return Outcome.failed(AuthErrors.accountUnavailable());
                }
                if (!user.emailVerified()) {
                    guard.secureUnproven(user, client, now);
                    verify(user, client, now);
                }
                link(user, provider, token, client, now);
                return admit(user, client, now);
            }
        }
        // A new account. An unverified provider email is not ours to claim, so the account gets none.
        boolean verifiedEmail = token.email() != null && token.emailVerified();
        var user = users.save(new UserAccount(
                Ids.newId(), verifiedEmail ? token.email() : null, guard.newHandle(), now, verifiedEmail));
        events.record(AuthEventLog.Type.REGISTERED, user.getId(), client);
        publisher.publishEvent(new UserRegistered(user.getId(), user.handle(), now));
        if (verifiedEmail) {
            publisher.publishEvent(new EmailVerified(user.getId(), now));
        }
        link(user, provider, token, client, now);
        return admit(user, client, now);
    }

    /** Links the provider to a signed-in user. */
    void link(UUID userId, IdentityProvider provider, String idToken, String nonce, ClientInfo client) {
        rateLimiter.check(SIGN_IN_PER_IP, client.ipText());
        VerifiedIdToken token = verified(provider, idToken, nonce);
        try {
            Objects.requireNonNull(tx.execute(status -> {
                        Instant now = clock.instant();
                        Optional<UserIdentity> linked = identities.findByProviderAndSubject(provider, token.subject());
                        if (linked.isPresent()) {
                            return linked.get().userId().equals(userId)
                                    ? Outcome.ok(Boolean.TRUE)
                                    : Outcome.<Boolean>failed(identityInUse());
                        }
                        if (identities.findByUserId(userId).stream().anyMatch(i -> i.provider() == provider)) {
                            return Outcome.<Boolean>failed(new ApiException(
                                    HttpStatus.CONFLICT,
                                    "provider_already_linked",
                                    "Another account from this provider is linked. Unlink it first."));
                        }
                        UserAccount user = users.findById(userId).orElseThrow();
                        link(user, provider, token, client, now);
                        return Outcome.ok(Boolean.TRUE);
                    }))
                    .get();
        } catch (DataIntegrityViolationException e) {
            throw identityInUse();
        }
    }

    /** Unlinks the provider, if another way to sign in remains (CLAUDE.md section 7). */
    void unlink(UUID userId, IdentityProvider provider, ClientInfo client) {
        Objects.requireNonNull(tx.execute(status -> {
                    List<UserIdentity> all = identities.findByUserId(userId);
                    Optional<UserIdentity> target =
                            all.stream().filter(i -> i.provider() == provider).findFirst();
                    if (target.isEmpty()) {
                        return Outcome.<Boolean>failed(
                                ApiException.notFound("not_linked", "This sign-in method is not linked."));
                    }
                    UserAccount user = users.findById(userId).orElseThrow();
                    boolean otherWayIn = all.size() > 1
                            || credentials.existsById(userId)
                            // A verified email can always get a sign-in code.
                            || (user.email() != null && user.emailVerified());
                    if (!otherWayIn) {
                        return Outcome.<Boolean>failed(new ApiException(
                                HttpStatus.CONFLICT,
                                "last_sign_in_method",
                                "This is your only way to sign in. Add a password or another method first."));
                    }
                    identities.delete(target.get());
                    events.record(AuthEventLog.Type.IDENTITY_UNLINKED, userId, client);
                    return Outcome.ok(Boolean.TRUE);
                }))
                .get();
    }

    private VerifiedIdToken verified(IdentityProvider provider, String idToken, String nonce) {
        if (!verifier.enabled(provider)) {
            throw OidcIdTokenVerifier.providerUnavailable();
        }
        // Spend the nonce first: a token with a guessed or replayed nonce never gets a second try.
        if (!nonces.consume(nonce)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_id_token", "Sign-in with this provider failed.");
        }
        return verifier.verify(provider, idToken, nonce);
    }

    private void link(
            UserAccount user, IdentityProvider provider, VerifiedIdToken token, ClientInfo client, Instant now) {
        identities.save(new UserIdentity(Ids.newId(), user.getId(), provider, token.subject(), token.email(), now));
        events.record(AuthEventLog.Type.IDENTITY_LINKED, user.getId(), client);
    }

    private void verify(UserAccount user, ClientInfo client, Instant now) {
        if (user.markEmailVerified(now)) {
            events.record(AuthEventLog.Type.EMAIL_VERIFIED, user.getId(), client);
            publisher.publishEvent(new EmailVerified(user.getId(), now));
        }
    }

    private Outcome<SignedIn> admit(UserAccount user, ClientInfo client, Instant now) {
        if (!guard.admit(user, client, now)) {
            return Outcome.failed(AuthErrors.accountUnavailable());
        }
        events.record(AuthEventLog.Type.SIGNED_IN, user.getId(), client);
        return Outcome.ok(sessionIssuer.open(user, client, now));
    }

    private static ApiException identityInUse() {
        return new ApiException(
                HttpStatus.CONFLICT, "identity_in_use", "This account from the provider is linked to another user.");
    }
}
