package com.canreadit.identity.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** A reader's account ({@code identity.users} with its roles). The ID is a UUIDv7 made by the application. */
@Entity
@Table(name = "users", schema = "identity")
class UserAccount implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Stored as citext; compare through {@link UserAccounts}, which casts, never in Java. */
    @Column(name = "email")
    private @Nullable String email;

    @Column(name = "email_verified_at")
    private @Nullable Instant emailVerifiedAt;

    @Column(name = "handle", nullable = false)
    private String handle;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deletion_requested_at")
    private @Nullable Instant deletionRequestedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", schema = "identity", joinColumns = @JoinColumn(name = "user_id"))
    private Set<RoleGrant> roles = new HashSet<>();

    @Transient
    private boolean isNew;

    /** For JPA. */
    protected UserAccount() {}

    /** A new reader with an unverified email and the handle as display name (CLAUDE.md section 7). */
    UserAccount(UUID id, String email, String handle, Instant now) {
        this(id, email, handle, now, false);
    }

    /** A new reader whose email may already be proven (they signed up with an emailed code). */
    UserAccount(UUID id, @Nullable String email, String handle, Instant now, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.emailVerifiedAt = emailVerified ? now : null;
        this.handle = handle;
        // TODO(product): a separate display name at sign-up; until then it starts as the handle.
        this.displayName = handle;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.roles.add(new RoleGrant(Role.READER, null, now));
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

    @Nullable
    String email() {
        return email;
    }

    boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    /** Records that the user proved they own the email. Returns whether this changed anything. */
    boolean markEmailVerified(Instant now) {
        if (emailVerifiedAt != null) {
            return false;
        }
        emailVerifiedAt = now;
        updatedAt = now;
        return true;
    }

    void rename(String newHandle, Instant now) {
        handle = newHandle;
        updatedAt = now;
    }

    void changeDisplayName(String name, Instant now) {
        displayName = name;
        updatedAt = now;
    }

    /** Starts the grace period after which the account is erased ({@link #erase}). */
    void requestDeletion(Instant now) {
        status = UserStatus.PENDING_DELETION;
        deletionRequestedAt = now;
        updatedAt = now;
    }

    /** Signing in during the grace period keeps the account. */
    void cancelDeletion(Instant now) {
        status = UserStatus.ACTIVE;
        deletionRequestedAt = null;
        updatedAt = now;
    }

    @Nullable
    Instant deletionRequestedAt() {
        return deletionRequestedAt;
    }

    /**
     * Removes everything personal and keeps a tombstone, so other modules' references to the ID stay valid. The
     * handle becomes a placeholder, which frees the old one.
     */
    void erase(String placeholderHandle, Instant now) {
        status = UserStatus.DELETED;
        email = null;
        emailVerifiedAt = null;
        handle = placeholderHandle;
        displayName = "Deleted reader";
        deletionRequestedAt = null;
        deletedAt = now;
        updatedAt = now;
        roles.clear();
    }

    String handle() {
        return handle;
    }

    String displayName() {
        return displayName;
    }

    UserStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    /** Adds a role granted by the system. Returns whether the user lacked it. */
    boolean grant(Role role, Instant now) {
        if (roles.stream().anyMatch(grant -> grant.role() == role)) {
            return false;
        }
        roles.add(new RoleGrant(role, null, now));
        updatedAt = now;
        return true;
    }

    /** Role names, highest privileges last, for tokens and responses. */
    List<String> roleNames() {
        return roles.stream().map(RoleGrant::role).sorted().map(Role::name).toList();
    }
}
