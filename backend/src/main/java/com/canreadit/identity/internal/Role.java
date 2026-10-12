package com.canreadit.identity.internal;

/**
 * Account roles (CLAUDE.md section 7). Permissions per role arrive with the first endpoints that need them.
 * TODO(identity): staff roles (MODERATOR, ADMIN) require TOTP 2FA and recent re-authentication for destructive
 * actions; the seam is the role check on those endpoints.
 */
enum Role {
    READER,
    CREATOR,
    MODERATOR,
    ADMIN
}
