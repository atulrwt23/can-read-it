package com.canreadit.identity.internal;

/**
 * Account roles (CLAUDE.md section 7). What each may do is {@link Permission}.
 * TODO(identity): staff roles (MODERATOR, ADMIN) require TOTP 2FA and recent re-authentication for destructive
 * actions; the seam is the role check on those endpoints.
 */
enum Role {
    READER,
    CREATOR,
    MODERATOR,
    ADMIN
}
