package com.canreadit.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** A social sign-in linked to a user ({@code identity.user_identities}), matched on (provider, subject). */
@Entity
@Table(name = "user_identities", schema = "identity")
class UserIdentity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private IdentityProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String subject;

    /** The email the provider last reported, verified or not. Never used to match users. */
    @Column(name = "provider_email")
    private @Nullable String providerEmail;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Transient
    private boolean isNew;

    protected UserIdentity() {}

    UserIdentity(UUID id, UUID userId, IdentityProvider provider, String subject, @Nullable String email, Instant now) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.providerEmail = email;
        this.linkedAt = now;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    UUID userId() {
        return userId;
    }

    IdentityProvider provider() {
        return provider;
    }

    /** Apple sends the email only on the first sign-in, so a missing one never erases what we have. */
    void seen(@Nullable String email) {
        if (email != null) {
            providerEmail = email;
        }
    }
}
