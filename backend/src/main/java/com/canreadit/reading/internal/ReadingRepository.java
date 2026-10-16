package com.canreadit.reading.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code reading} schema. Writes are single-statement upserts ("the latest progress wins", "a chapter is read
 * once"), which SQL states more plainly than an entity would.
 */
@Repository
class ReadingRepository {

    private final JdbcClient jdbc;

    ReadingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A library row or a progress row, with the key to continue after it. */
    record Entry(UUID seriesId, Instant at) {}

    record Progress(UUID seriesId, UUID chapterId, double position, Instant updatedAt) {}

    // --- Library

    /** Returns whether the series was newly added. */
    boolean follow(UUID userId, UUID seriesId, Instant now) {
        return jdbc.sql("""
                        insert into reading.library_entries (user_id, series_id, added_at)
                        values (:user, :series, :now)
                        on conflict do nothing
                        """)
                        .param("user", userId)
                        .param("series", seriesId)
                        .param("now", utc(now))
                        .update()
                > 0;
    }

    /** Returns whether the series was in the library. */
    boolean unfollow(UUID userId, UUID seriesId) {
        return jdbc.sql("delete from reading.library_entries where user_id = :user and series_id = :series")
                        .param("user", userId)
                        .param("series", seriesId)
                        .update()
                > 0;
    }

    List<UUID> followers(UUID seriesId) {
        return jdbc.sql("select user_id from reading.library_entries where series_id = :series")
                .param("series", seriesId)
                .query(UUID.class)
                .list();
    }

    boolean follows(UUID userId, UUID seriesId) {
        return jdbc.sql(
                        "select exists (select 1 from reading.library_entries where user_id = :user and series_id = :series)")
                .param("user", userId)
                .param("series", seriesId)
                .query(Boolean.class)
                .single();
    }

    /** Newest first; {@code after} continues after an entry. */
    List<Entry> library(UUID userId, @Nullable Entry after, int limit) {
        return page("library_entries", "added_at", userId, after, limit);
    }

    // --- Progress

    /** Saves progress unless the stored one is newer. */
    void saveProgress(UUID userId, UUID seriesId, UUID chapterId, double position, Instant at) {
        jdbc.sql("""
                        insert into reading.reading_progress (user_id, series_id, chapter_id, position, updated_at)
                        values (:user, :series, :chapter, :position, :at)
                        on conflict (user_id, series_id) do update
                            set chapter_id = excluded.chapter_id, position = excluded.position,
                                updated_at = excluded.updated_at
                            where reading.reading_progress.updated_at < excluded.updated_at
                        """)
                .param("user", userId)
                .param("series", seriesId)
                .param("chapter", chapterId)
                .param("position", BigDecimal.valueOf(position).setScale(4, java.math.RoundingMode.HALF_UP))
                .param("at", utc(at))
                .update();
    }

    Optional<Progress> progress(UUID userId, UUID seriesId) {
        return progress(userId, List.of(seriesId)).stream().findFirst();
    }

    List<Progress> progress(UUID userId, Collection<UUID> seriesIds) {
        if (seriesIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select series_id, chapter_id, position, updated_at from reading.reading_progress
                        where user_id = :user and series_id in (:series)
                        """)
                .param("user", userId)
                .param("series", List.copyOf(seriesIds))
                .query(ReadingRepository::progress)
                .list();
    }

    /** The most recently read series first; {@code after} continues after an entry. */
    List<Entry> history(UUID userId, @Nullable Entry after, int limit) {
        return page("reading_progress", "updated_at", userId, after, limit);
    }

    // --- Read chapters

    /** Marks chapters read; ones already marked keep their first date. */
    void markRead(UUID userId, UUID seriesId, Collection<UUID> chapterIds, Instant at) {
        for (UUID chapterId : chapterIds) {
            jdbc.sql("""
                            insert into reading.read_chapters (user_id, chapter_id, series_id, read_at)
                            values (:user, :chapter, :series, :at)
                            on conflict do nothing
                            """)
                    .param("user", userId)
                    .param("chapter", chapterId)
                    .param("series", seriesId)
                    .param("at", utc(at))
                    .update();
        }
    }

    List<UUID> readChapters(UUID userId, UUID seriesId) {
        return jdbc.sql("select chapter_id from reading.read_chapters where user_id = :user and series_id = :series")
                .param("user", userId)
                .param("series", seriesId)
                .query(UUID.class)
                .list();
    }

    // --- Accounts

    /** Erases everything kept about the user (the account was deleted). */
    void forget(UUID userId) {
        for (String table : List.of("library_entries", "reading_progress", "read_chapters")) {
            jdbc.sql("delete from reading." + table + " where user_id = :user")
                    .param("user", userId)
                    .update();
        }
    }

    private List<Entry> page(String table, String column, UUID userId, @Nullable Entry after, int limit) {
        String keyset = after == null ? "" : " and (%s, series_id) < (:afterAt, :afterId)".formatted(column);
        var spec = jdbc.sql("""
                        select series_id, %1$s as at from reading.%2$s
                        where user_id = :user%3$s
                        order by %1$s desc, series_id desc
                        limit :limit
                        """.formatted(column, table, keyset))
                .param("user", userId)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("afterAt", utc(after.at())).param("afterId", after.seriesId());
        }
        return spec.query((rs, n) -> new Entry(rs.getObject("series_id", UUID.class), instant(rs, "at")))
                .list();
    }

    private static Progress progress(ResultSet rs, int row) throws SQLException {
        return new Progress(
                rs.getObject("series_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getBigDecimal("position").doubleValue(),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
