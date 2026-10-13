package com.canreadit.catalog.internal;

import com.canreadit.catalog.ChapterAccess;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A chapter as staff edit it ({@code catalog.chapters} with its pages). The publish job flips {@code SCHEDULED} to
 * {@code PUBLISHED} in SQL; updates here only write changed columns, so they don't undo it.
 */
@Entity
@Table(name = "chapters", schema = "catalog")
@DynamicUpdate
class ChapterEntity implements Persistable<UUID> {

    enum Status {
        DRAFT,
        SCHEDULED,
        PUBLISHED
    }

    /** One manhwa page, in reading order ({@code catalog.chapter_pages}). */
    @Embeddable
    record Page(
            @Column(name = "asset_id", nullable = false) UUID assetId,
            @Column(nullable = false) int width,
            @Column(nullable = false) int height) {}

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false, updatable = false)
    private UUID editionId;

    @Column(nullable = false)
    private BigDecimal number;

    private @Nullable String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "publish_at")
    private @Nullable Instant publishAt;

    @Column(name = "published_at", insertable = false, updatable = false)
    private @Nullable Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChapterAccess access;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "chapter_pages", schema = "catalog", joinColumns = @JoinColumn(name = "chapter_id"))
    @OrderColumn(name = "page_index")
    private List<Page> pages = new ArrayList<>();

    @Transient
    private boolean isNew;

    protected ChapterEntity() {}

    ChapterEntity(UUID id, UUID editionId, BigDecimal number, @Nullable String title, Instant now) {
        this.id = id;
        this.editionId = editionId;
        this.number = number;
        this.title = title;
        this.status = Status.DRAFT;
        this.access = ChapterAccess.FREE;
        this.createdAt = now;
        this.updatedAt = now;
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

    /** Readers can see it: not a draft and its time has come (the visibility rule, ADR 0006). */
    boolean isLive(Instant now) {
        return status != Status.DRAFT && publishAt != null && !publishAt.isAfter(now);
    }

    void rename(BigDecimal newNumber, @Nullable String newTitle, Instant now) {
        number = newNumber;
        title = newTitle;
        updatedAt = now;
    }

    void replacePages(List<Page> newPages, Instant now) {
        pages.clear();
        pages.addAll(newPages);
        updatedAt = now;
    }

    /** Marks an edit to content stored elsewhere (the novel text). */
    void touch(Instant now) {
        updatedAt = now;
    }

    void schedule(Instant at, Instant now) {
        status = Status.SCHEDULED;
        publishAt = at;
        updatedAt = now;
    }

    void unschedule(Instant now) {
        status = Status.DRAFT;
        publishAt = null;
        updatedAt = now;
    }

    UUID editionId() {
        return editionId;
    }

    BigDecimal number() {
        return number;
    }

    @Nullable
    String title() {
        return title;
    }

    Status status() {
        return status;
    }

    @Nullable
    Instant publishAt() {
        return publishAt;
    }

    @Nullable
    Instant publishedAt() {
        return publishedAt;
    }

    List<Page> pages() {
        return pages;
    }
}
