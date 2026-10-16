package com.canreadit.engagement.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code engagement} rating tables. Every change to a score moves the series' totals by the same amount in the
 * same statement sequence, so callers run these inside a transaction.
 */
@Repository
class RatingRepository {

    private final JdbcClient jdbc;

    RatingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** How many readers rated a series and the sum of their scores. */
    record Totals(int ratings, long scoreSum) {

        static final Totals NONE = new Totals(0, 0);
    }

    Totals totals(UUID seriesId) {
        return jdbc.sql("select ratings, score_sum from engagement.series_ratings where series_id = :series")
                .param("series", seriesId)
                .query((rs, n) -> new Totals(rs.getInt("ratings"), rs.getLong("score_sum")))
                .optional()
                .orElse(Totals.NONE);
    }

    Optional<Integer> score(UUID userId, UUID seriesId) {
        return jdbc.sql("select score from engagement.ratings where user_id = :user and series_id = :series")
                .param("user", userId)
                .param("series", seriesId)
                .query(Integer.class)
                .optional();
    }

    /** Sets the reader's score, replacing an earlier one. */
    void rate(UUID userId, UUID seriesId, int score, Instant now) {
        int inserted = jdbc.sql("""
                        insert into engagement.ratings (user_id, series_id, score, created_at, updated_at)
                        values (:user, :series, :score, :now, :now)
                        on conflict (user_id, series_id) do nothing
                        """)
                .param("user", userId)
                .param("series", seriesId)
                .param("score", score)
                .param("now", utc(now))
                .update();
        if (inserted > 0) {
            addToTotals(seriesId, 1, score, now);
            return;
        }
        // Rated before: lock the row so two changes at once both count.
        int previous = jdbc.sql("""
                        select score from engagement.ratings
                        where user_id = :user and series_id = :series
                        for update
                        """)
                .param("user", userId)
                .param("series", seriesId)
                .query(Integer.class)
                .single();
        if (previous == score) {
            return;
        }
        jdbc.sql("""
                        update engagement.ratings set score = :score, updated_at = :now
                        where user_id = :user and series_id = :series
                        """)
                .param("user", userId)
                .param("series", seriesId)
                .param("score", score)
                .param("now", utc(now))
                .update();
        addToTotals(seriesId, 0, score - previous, now);
    }

    /** Removes the reader's score; false if there was none. */
    boolean remove(UUID userId, UUID seriesId, Instant now) {
        Optional<Integer> removed = jdbc.sql("""
                        delete from engagement.ratings where user_id = :user and series_id = :series
                        returning score
                        """)
                .param("user", userId)
                .param("series", seriesId)
                .query(Integer.class)
                .optional();
        removed.ifPresent(score -> addToTotals(seriesId, -1, -score, now));
        return removed.isPresent();
    }

    /** Removes every score the reader gave (the account was deleted). */
    void forget(UUID userId, Instant now) {
        record Removed(UUID seriesId, int score) {}
        jdbc.sql("delete from engagement.ratings where user_id = :user returning series_id, score")
                .param("user", userId)
                .query((rs, n) -> new Removed(rs.getObject("series_id", UUID.class), rs.getInt("score")))
                .list()
                .forEach(removed -> addToTotals(removed.seriesId(), -1, -removed.score(), now));
    }

    private void addToTotals(UUID seriesId, int ratings, long scoreSum, Instant now) {
        jdbc.sql("""
                        insert into engagement.series_ratings (series_id, ratings, score_sum, updated_at)
                        values (:series, greatest(:ratings, 0), greatest(:sum, 0), :now)
                        on conflict (series_id) do update set
                            ratings = greatest(series_ratings.ratings + :ratings, 0),
                            score_sum = greatest(series_ratings.score_sum + :sum, 0),
                            updated_at = :now
                        """)
                .param("series", seriesId)
                .param("ratings", ratings)
                .param("sum", scoreSum)
                .param("now", utc(now))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
