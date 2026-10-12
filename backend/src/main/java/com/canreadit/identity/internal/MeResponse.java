package com.canreadit.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in user, as the account menu and account page show them.
 *
 * @param hasPassword whether the account can sign in with a password
 * @param providers linked social sign-ins, lower case ({@code google}, {@code apple})
 */
record MeResponse(
        UUID id,
        String handle,
        String displayName,
        @Nullable String email,
        boolean emailVerified,
        List<String> roles,
        boolean hasPassword,
        List<String> providers,
        Instant createdAt) {}
