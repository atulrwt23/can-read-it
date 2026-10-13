package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final AuthService auth;
    private final EmailCodeService codes;
    private final SocialSignInService social;

    MeController(AuthService auth, EmailCodeService codes, SocialSignInService social) {
        this.auth = auth;
        this.codes = codes;
        this.social = social;
    }

    /** The signed-in user. Personal, so never cached. */
    @GetMapping("/api/v1/me")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        var user = auth.user(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in again."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MeResponse.of(user));
    }

    /** Emails a new code that confirms the account's email ({@code POST /auth/email/verify}). */
    @PostMapping("/api/v1/me/email/verification")
    ResponseEntity<Void> resendConfirmation(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        codes.resendConfirmation(UUID.fromString(jwt.getSubject()), ClientInfo.from(request));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    /** Links Google or Apple to this account, with an ID token and nonce as for signing in. */
    @PostMapping("/api/v1/me/identities/{provider}")
    ResponseEntity<Void> linkIdentity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String provider,
            @Valid @RequestBody AuthRequests.OAuthToken body,
            HttpServletRequest request) {
        social.link(
                UUID.fromString(jwt.getSubject()),
                IdentityProvider.fromPath(provider).orElseThrow(OidcIdTokenVerifier::providerUnavailable),
                body.idToken(),
                body.nonce(),
                ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }

    /** Unlinks a provider; refused ({@code 409 last_sign_in_method}) if it is the only way to sign in. */
    @DeleteMapping("/api/v1/me/identities/{provider}")
    ResponseEntity<Void> unlinkIdentity(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String provider, HttpServletRequest request) {
        social.unlink(
                UUID.fromString(jwt.getSubject()),
                IdentityProvider.fromPath(provider).orElseThrow(OidcIdTokenVerifier::providerUnavailable),
                ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }
}
