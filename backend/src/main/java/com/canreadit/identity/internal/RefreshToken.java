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
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** One refresh token of a session ({@code identity.refresh_tokens}), stored as a hash and good for one use. */
@Entity
@Table(name = "refresh_tokens", schema = "identity")
class RefreshToken implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Transient
    private boolean isNew;

    protected RefreshToken() {}

    RefreshToken(UUID id, UUID sessionId, String tokenHash, Instant now, Instant expiresAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.issuedAt = now;
        this.expiresAt = expiresAt;
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

    UUID sessionId() {
        return sessionId;
    }

    @Nullable
    Instant usedAt() {
        return usedAt;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    void markUsed(Instant now) {
        usedAt = now;
    }
}
