package com.canreadit.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account. Personal, so nothing here is cached; writes need the CSRF header. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final AccountService accounts;
    private final AccountViews views;
    private final EmailCodeService codes;
    private final SocialSignInService social;
    private final AuthCookieWriter cookies;

    MeController(
            AccountService accounts,
            AccountViews views,
            EmailCodeService codes,
            SocialSignInService social,
            AuthCookieWriter cookies) {
        this.accounts = accounts;
        this.views = views;
        this.codes = codes;
        this.social = social;
        this.cookies = cookies;
    }

    @GetMapping
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return noStore(views.of(accounts.user(userId(jwt))));
    }

    /** Changes the handle and/or display name; omitted fields stay as they are. */
    @PatchMapping
    ResponseEntity<MeResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountRequests.UpdateProfile body,
            HttpServletRequest request) {
        var user = accounts.updateProfile(caller(jwt, request), body.handle(), body.displayName());
        return noStore(views.of(user));
    }

    /**
     * Sets or changes the password and signs out every other device. Needs the current password if there is one,
     * otherwise a sign-in within the last 10 minutes ({@code 403 reauthentication_required}).
     */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccountRequests.ChangePassword body,
            HttpServletRequest request) {
        accounts.changePassword(caller(jwt, request), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    ResponseEntity<List<AccountService.SessionView>> sessions(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return noStore(accounts.sessions(caller(jwt, request)));
    }

    /** Signs one device out; signing out this one also clears its cookies. */
    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (accounts.revokeSession(caller(jwt, request), id)) {
            cookies.clear(response);
        }
        return ResponseEntity.noContent().build();
    }

    /** Signs out every device except this one. */
    @PostMapping("/sessions/revoke-all")
    ResponseEntity<Void> revokeOtherSessions(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        accounts.revokeOtherSessions(caller(jwt, request));
        return ResponseEntity.noContent().build();
    }

    record DeletionResponse(Instant eraseAfter) {}

    /**
     * Starts the 30-day deletion and signs out everywhere. Signing in again before {@code eraseAfter} cancels it.
     * Proves identity like {@link #changePassword}.
     */
    @DeleteMapping
    ResponseEntity<DeletionResponse> deleteAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) AccountRequests.@Nullable DeleteAccount body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Instant eraseAfter =
                accounts.requestDeletion(caller(jwt, request), body == null ? null : body.currentPassword());
        cookies.clear(response);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new DeletionResponse(eraseAfter));
    }

    /** Emails a new code that confirms the account's email ({@code POST /auth/email/verify}). */
    @PostMapping("/email/verification")
    ResponseEntity<Void> resendConfirmation(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        codes.resendConfirmation(userId(jwt), ClientInfo.from(request));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    /** Links Google or Apple to this account, with an ID token and nonce as for signing in. */
    @PostMapping("/identities/{provider}")
    ResponseEntity<Void> linkIdentity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String provider,
            @Valid @RequestBody AuthRequests.OAuthToken body,
            HttpServletRequest request) {
        social.link(
                userId(jwt),
                IdentityProvider.fromPath(provider).orElseThrow(OidcIdTokenVerifier::providerUnavailable),
                body.idToken(),
                body.nonce(),
                ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }

    /** Unlinks a provider; refused ({@code 409 last_sign_in_method}) if it is the only way to sign in. */
    @DeleteMapping("/identities/{provider}")
    ResponseEntity<Void> unlinkIdentity(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String provider, HttpServletRequest request) {
        social.unlink(
                userId(jwt),
                IdentityProvider.fromPath(provider).orElseThrow(OidcIdTokenVerifier::providerUnavailable),
                ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static AccountService.Caller caller(Jwt jwt, HttpServletRequest request) {
        return new AccountService.Caller(
                userId(jwt), UUID.fromString(jwt.getClaimAsString("sid")), ClientInfo.from(request));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
