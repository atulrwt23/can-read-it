package com.canreadit.identity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user got a role.
 *
 * @param grantedBy who granted it; null when the system did ({@code ADMIN_EMAILS} at sign-in)
 */
public record RoleGranted(
        UUID userId, String role, @Nullable UUID grantedBy, Instant grantedAt) {}
