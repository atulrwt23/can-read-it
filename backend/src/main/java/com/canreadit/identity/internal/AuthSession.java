package com.canreadit.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A signed-in device ({@code identity.sessions}). It lives as long as its refresh tokens keep being used: every
 * refresh pushes {@code expiresAt} out again (sliding lifetime).
 */
@Entity
@Table(name = "sessions", schema = "identity")
class AuthSession implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ip", columnDefinition = "inet")
    private @Nullable InetAddress ip;

    @Column(name = "user_agent")
    private @Nullable String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    @Transient
    private boolean isNew;

    protected AuthSession() {}

    AuthSession(UUID id, UUID userId, ClientInfo client, Instant now, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.ip = client.ip();
        this.userAgent = client.userAgent();
        this.createdAt = now;
        this.lastUsedAt = now;
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

    UUID userId() {
        return userId;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant lastUsedAt() {
        return lastUsedAt;
    }

    @Nullable
    String userAgent() {
        return userAgent;
    }

    @Nullable
    InetAddress ip() {
        return ip;
    }

    boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    void extend(Instant now, Instant newExpiry) {
        lastUsedAt = now;
        expiresAt = newExpiry;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
