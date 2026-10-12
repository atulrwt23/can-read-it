package com.canreadit.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A role held by a user ({@code identity.user_roles}). */
@Embeddable
record RoleGrant(
        @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false)
        Role role,

        @Column(name = "granted_by") @Nullable UUID grantedBy,
        @Column(name = "granted_at", nullable = false) Instant grantedAt) {}
