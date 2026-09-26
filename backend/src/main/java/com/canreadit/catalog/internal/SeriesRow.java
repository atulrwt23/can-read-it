package com.canreadit.catalog.internal;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record SeriesRow(
        UUID id,
        String slug,
        String title,
        List<String> altTitles,
        String synopsis,
        SeriesType type,
        SeriesStatus status,
        Visibility visibility,
        AgeRating ageRating,
        @Nullable UUID coverAssetId,
        @Nullable Integer firstReleasedYear,
        @Nullable String releaseCadence,
        @Nullable Instant lastPublishedAt,
        Instant createdAt) {

    enum Visibility {
        DRAFT,
        PUBLISHED,
        UNLISTED,
        REMOVED
    }

    static final String COLUMNS = """
            s.id, s.slug, s.title, s.alt_titles, s.synopsis, s.type, s.status, s.visibility, s.age_rating,
            s.cover_asset_id, s.first_released_year, s.release_cadence, s.last_published_at, s.created_at
            """;

    static SeriesRow map(ResultSet rs, int rowNum) throws SQLException {
        String[] altTitles = (String[]) rs.getArray("alt_titles").getArray();
        return new SeriesRow(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                rs.getString("title"),
                List.of(altTitles),
                rs.getString("synopsis"),
                SeriesType.valueOf(rs.getString("type")),
                SeriesStatus.valueOf(rs.getString("status")),
                Visibility.valueOf(rs.getString("visibility")),
                AgeRating.valueOf(rs.getString("age_rating")),
                rs.getObject("cover_asset_id", UUID.class),
                rs.getObject("first_released_year", Integer.class),
                rs.getString("release_cadence"),
                instant(rs, "last_published_at"),
                Instant.from(rs.getObject("created_at", OffsetDateTime.class)));
    }

    static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
