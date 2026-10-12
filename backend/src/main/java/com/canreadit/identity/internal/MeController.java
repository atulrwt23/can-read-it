package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final AuthService auth;
    private final EmailCodeService codes;

    MeController(AuthService auth, EmailCodeService codes) {
        this.auth = auth;
        this.codes = codes;
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
}
