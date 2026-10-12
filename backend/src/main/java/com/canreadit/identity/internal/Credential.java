package com.canreadit.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A user's password hash ({@code identity.credentials}). Accounts from social sign-in may have none. */
@Entity
@Table(name = "credentials", schema = "identity")
class Credential implements Persistable<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_updated_at", nullable = false)
    private Instant passwordUpdatedAt;

    @Transient
    private boolean isNew;

    protected Credential() {}

    Credential(UUID userId, String passwordHash, Instant now) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = now;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return userId;
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

    String passwordHash() {
        return passwordHash;
    }
}
