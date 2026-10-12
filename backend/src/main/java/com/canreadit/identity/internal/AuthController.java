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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password sign-up and sign-in, token refresh and sign-out. Tokens travel only in cookies; the body is the user.
 * TODO(identity): mobile clients (phase 4) get the tokens in the body and send the refresh token in a header.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;
    private final AuthCookieWriter cookies;

    AuthController(AuthService auth, AuthCookieWriter cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/password/register")
    ResponseEntity<MeResponse> register(
            @Valid @RequestBody AuthRequests.Register body, HttpServletRequest request, HttpServletResponse response) {
        var signedIn =
                auth.register(body.email(), body.password(), body.handle(), body.botToken(), ClientInfo.from(request));
        cookies.write(response, signedIn);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(MeResponse.of(signedIn.user()));
    }

    @PostMapping("/password/login")
    ResponseEntity<MeResponse> login(
            @Valid @RequestBody AuthRequests.Login body, HttpServletRequest request, HttpServletResponse response) {
        var signedIn = auth.login(body.email(), body.password(), ClientInfo.from(request));
        cookies.write(response, signedIn);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MeResponse.of(signedIn.user()));
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
