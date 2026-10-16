package com.canreadit.identity.internal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What a role may do (CLAUDE.md section 7): a fixed map in code. Each permission becomes an authority of the same
 * name, checked with {@code @PreAuthorize("hasAuthority('...')")} on the endpoint.
 */
enum Permission {
    /** Upload covers and chapter pages. */
    MEDIA_UPLOAD,
    /** Create and edit series and chapters, and schedule releases. */
    CATALOG_MANAGE,
    /** See product analytics: daily event counts and the most read series. */
    ANALYTICS_VIEW,
    /** Work the moderation queue: remove reported comments or keep them. */
    CONTENT_MODERATE;

    // TODO(product): creators manage their own series (series_members) in the Creator Studio (phase 3).
    private static final Map<Role, Set<Permission>> BY_ROLE = Map.of(
            Role.READER, EnumSet.noneOf(Permission.class),
            Role.CREATOR, EnumSet.noneOf(Permission.class),
            Role.MODERATOR, EnumSet.of(CONTENT_MODERATE),
            Role.ADMIN, EnumSet.allOf(Permission.class));

    static Set<Permission> of(Role role) {
        return BY_ROLE.get(role);
    }
}
