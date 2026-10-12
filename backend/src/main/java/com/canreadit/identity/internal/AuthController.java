package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and sign-in (password, emailed code, Google or Apple), password reset, token refresh and sign-out. Tokens travel only in cookies; the body is the user.
 * TODO(identity): mobile clients (phase 4) get the tokens in the body and send the refresh token in a header.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;
    private final EmailCodeService codes;
    private final SocialSignInService social;
    private final AuthCookieWriter cookies;
    private final AccountViews views;

    AuthController(
            AuthService auth,
            EmailCodeService codes,
            SocialSignInService social,
            AuthCookieWriter cookies,
            AccountViews views) {
        this.views = views;
        this.auth = auth;
        this.codes = codes;
        this.social = social;
        this.cookies = cookies;
    }

    /**
     * Always {@code 202}: the email gets a code that confirms it and signs in, or a notice if it already has an
     * account. The new account can also sign in with its password straight away.
     */
    @PostMapping("/password/register")
    ResponseEntity<Void> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        auth.register(body.email(), body.password(), body.handle(), body.botToken(), ClientInfo.from(request));
        return accepted();
    }

    @PostMapping("/password/login")
    ResponseEntity<MeResponse> login(
            @Valid @RequestBody AuthRequests.Login body, HttpServletRequest request, HttpServletResponse response) {
        return signedIn(response, auth.login(body.email(), body.password(), ClientInfo.from(request)));
    }

    /** Always {@code 202}: emails a code if the email has an account. */
    @PostMapping("/password/forgot")
    ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody AuthRequests.ForgotPassword body, HttpServletRequest request) {
        codes.forgotPassword(body.email(), body.botToken(), ClientInfo.from(request));
        return accepted();
    }

    /** Sets a new password with the emailed code, ends every other session and signs in here. */
    @PostMapping("/password/reset")
    ResponseEntity<MeResponse> resetPassword(
            @Valid @RequestBody AuthRequests.ResetPassword body,
            HttpServletRequest request,
            HttpServletResponse response) {
        var signedIn = codes.resetPassword(body.email(), body.code(), body.newPassword(), ClientInfo.from(request));
        return signedIn(response, signedIn);
    }

    /** Always {@code 202}: emails a code that signs in, or creates the account if the email has none. */
    @PostMapping("/email/start")
    ResponseEntity<Void> startEmailSignIn(
            @Valid @RequestBody AuthRequests.EmailStart body, HttpServletRequest request) {
        codes.start(body.email(), body.botToken(), ClientInfo.from(request));
        return accepted();
    }

    /** Signs in with an emailed code (from {@code /email/start} or registration). */
    @PostMapping("/email/verify")
    ResponseEntity<MeResponse> verifyEmailCode(
            @Valid @RequestBody AuthRequests.EmailVerify body,
            HttpServletRequest request,
            HttpServletResponse response) {
        var signedIn = codes.verify(body.email(), body.code(), body.handle(), ClientInfo.from(request));
        return signedIn(response, signedIn);
    }

    record NonceResponse(String nonce) {}

    /** A one-time nonce to pass to the Google or Apple SDK before signing in with it. */
    @PostMapping("/oauth/nonce")
    ResponseEntity<NonceResponse> oauthNonce(HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NonceResponse(social.nonce(ClientInfo.from(request))));
    }

    /**
     * Signs in with an ID token from Google or Apple, creating the account on first use. {@code 404} when the provider
     * is not configured.
     */
    @PostMapping("/oauth/{provider}")
    ResponseEntity<MeResponse> oauthSignIn(
            @PathVariable String provider,
            @Valid @RequestBody AuthRequests.OAuthToken body,
            HttpServletRequest request,
            HttpServletResponse response) {
        var signedIn = social.signIn(
                IdentityProvider.fromPath(provider).orElseThrow(OidcIdTokenVerifier::providerUnavailable),
                body.idToken(),
                body.nonce(),
                ClientInfo.from(request));
        return signedIn(response, signedIn);
    }

    /** Swaps the refresh cookie for new cookies. A failed refresh clears them, so the client shows "signed out". */
    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request, AuthCookies.REFRESH);
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Sign in again.");
        }
        try {
            cookies.write(response, auth.refresh(token, ClientInfo.from(request)));
        } catch (ApiException e) {
            if (e.status() == HttpStatus.UNAUTHORIZED) {
                cookies.clear(response);
            }
            throw e;
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(cookie(request, AuthCookies.ACCESS), ClientInfo.from(request));
        cookies.clear(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<MeResponse> signedIn(HttpServletResponse response, SignedIn signedIn) {
        cookies.write(response, signedIn);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(views.of(signedIn.user()));
    }

    private static ResponseEntity<Void> accepted() {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    private static @Nullable String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
