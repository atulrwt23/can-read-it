package com.canreadit.identity.internal;

import com.canreadit.shared.ApiException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final AuthService auth;

    MeController(AuthService auth) {
        this.auth = auth;
    }

    /** The signed-in user. Personal, so never cached. */
    @GetMapping("/api/v1/me")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        var user = auth.user(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Sign in again."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MeResponse.of(user));
    }
}
