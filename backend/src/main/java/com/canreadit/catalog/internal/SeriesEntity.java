package com.canreadit.catalog.internal;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A series as staff edit it ({@code catalog.series}). Reads for readers go through {@link SeriesRepository}; this
 * entity is the write side. Columns other jobs maintain ({@code last_published_at}, {@code popularity}) are not
 * mapped, and updates only write changed columns, so an edit never overwrites them.
 */
@Entity
@Table(name = "series", schema = "catalog")
@DynamicUpdate
class SeriesEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alt_titles", nullable = false, columnDefinition = "text[]")
    private List<String> altTitles = new ArrayList<>();

    @Column(nullable = false)
    private String synopsis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeriesRow.Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false)
    private AgeRating ageRating;

    @Column(name = "cover_asset_id")
    private @Nullable UUID coverAssetId;

    @Column(name = "first_released_year")
    private @Nullable Short firstReleasedYear;

    @Column(name = "release_cadence")
    private @Nullable String releaseCadence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "series_genres", schema = "catalog", joinColumns = @JoinColumn(name = "series_id"))
    @Column(name = "genre_id")
    private Set<UUID> genreIds = new HashSet<>();

    @Transient
    private boolean isNew;

    protected SeriesEntity() {}

    SeriesEntity(UUID id, String slug, SeriesType type, Instant now) {
        this.id = id;
        this.slug = slug;
        this.type = type;
        this.title = "";
        this.synopsis = "";
        this.status = SeriesStatus.ONGOING;
        this.visibility = SeriesRow.Visibility.DRAFT;
        this.ageRating = AgeRating.ALL;
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

    /** Everything staff edit at once, with the slug, genres and cover already checked. The type is fixed. */
    void edit(AdminRequests.SeriesForm form, String newSlug, Set<UUID> genres, @Nullable UUID cover, Instant now) {
        slug = newSlug;
        title = form.title().strip();
        altTitles = new ArrayList<>(
                form.altTitles() == null
                        ? List.of()
                        : form.altTitles().stream()
                                .map(String::strip)
                                .filter(t -> !t.isEmpty())
                                .distinct()
                                .toList());
        synopsis = form.synopsis() == null ? "" : form.synopsis().strip();
        status = form.status();
        visibility = form.visibility();
        ageRating = form.ageRating();
        coverAssetId = cover;
        firstReleasedYear = form.firstReleasedYear() == null
                ? null
                : form.firstReleasedYear().shortValue();
        releaseCadence = form.releaseCadence() == null || form.releaseCadence().isBlank()
                ? null
                : form.releaseCadence().strip();
        genreIds = new HashSet<>(genres);
        updatedAt = now;
    }

    String slug() {
        return slug;
    }

    String title() {
        return title;
    }

    List<String> altTitles() {
        return altTitles;
    }

    String synopsis() {
        return synopsis;
    }

    SeriesType type() {
        return type;
    }

    SeriesStatus status() {
        return status;
    }

    SeriesRow.Visibility visibility() {
        return visibility;
    }

    AgeRating ageRating() {
        return ageRating;
    }

    @Nullable
    UUID coverAssetId() {
        return coverAssetId;
    }

    @Nullable
    Short firstReleasedYear() {
        return firstReleasedYear;
    }

    @Nullable
    String releaseCadence() {
        return releaseCadence;
    }

    Set<UUID> genreIds() {
        return genreIds;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
