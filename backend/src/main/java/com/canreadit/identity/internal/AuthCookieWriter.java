package com.canreadit.identity.internal;

import com.canreadit.shared.AuthCookies;
import com.canreadit.shared.CookieSettings;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The session cookies (CLAUDE.md section 7): {@code httpOnly}, {@code SameSite=Lax}, {@code Secure} except locally.
 * The access cookie lives exactly as long as its token; the refresh cookie is sent only to the refresh endpoint.
 */
@Component
class AuthCookieWriter {

    private final CookieSettings settings;
    private final Clock clock;

    AuthCookieWriter(CookieSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    void write(HttpServletResponse response, AuthService.SignedIn signedIn) {
        Instant now = clock.instant();
        add(
                response,
                AuthCookies.ACCESS,
                signedIn.access().token(),
                "/",
                Duration.between(now, signedIn.access().expiresAt()));
        add(
                response,
                AuthCookies.REFRESH,
                signedIn.refreshToken(),
                AuthCookies.REFRESH_PATH,
                Duration.between(now, signedIn.refreshExpiresAt()));
    }

    void clear(HttpServletResponse response) {
        add(response, AuthCookies.ACCESS, "", "/", Duration.ZERO);
        add(response, AuthCookies.REFRESH, "", AuthCookies.REFRESH_PATH, Duration.ZERO);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
