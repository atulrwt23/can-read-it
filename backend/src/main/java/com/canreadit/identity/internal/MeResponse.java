package com.canreadit.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The signed-in user, as the account menu and account page show them. */
record MeResponse(
        UUID id,
        String handle,
        String displayName,
        @Nullable String email,
        boolean emailVerified,
        List<String> roles,
        Instant createdAt) {

    static MeResponse of(UserAccount user) {
        return new MeResponse(
                user.getId(),
                user.handle(),
                user.displayName(),
                user.email(),
                user.emailVerified(),
                user.roleNames(),
                user.createdAt());
    }
}
