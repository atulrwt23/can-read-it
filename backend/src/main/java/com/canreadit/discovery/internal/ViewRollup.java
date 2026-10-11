package com.canreadit.discovery.internal;

import com.canreadit.catalog.SeriesPopularity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves view counts from Redis into Postgres (ADR 0005) and recomputes the rankings read model. Every step writes
 * absolute values, so running it twice, late, or on several instances changes nothing. A day's count only ever
 * goes up here: if Redis loses its data, the views already stored stay.
 */
@Component
class ViewRollup {

    private static final Logger log = LoggerFactory.getLogger(ViewRollup.class);

    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final SeriesPopularity popularity;

    ViewRollup(JdbcClient jdbc, StringRedisTemplate redis, Clock clock, SeriesPopularity popularity) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.clock = clock;
        this.popularity = popularity;
    }

    @Scheduled(fixedDelayString = "${app.discovery.rollup-interval:PT5M}", initialDelayString = "PT1M")
    void run() {
        rollUp();
    }

    void rollUp() {
        LocalDate today = LocalDate.now(clock);
        // Yesterday too, so the last views before midnight are not lost.
        for (LocalDate day : List.of(today.minusDays(1), today)) {
            copyDay(day);
        }
        recomputeStats(today);
    }

    private void copyDay(LocalDate day) {
        Set<TypedTuple<String>> counts;
        try {
            counts = redis.opsForZSet().rangeWithScores(ViewCounter.dayKey(day), 0, -1);
        } catch (DataAccessException e) {
            log.warn("Views for {} not rolled up, Redis unavailable: {}", day, e.getMessage());
            return;
        }
        if (counts == null) {
            return;
        }
        for (TypedTuple<String> count : counts) {
            if (count.getValue() == null || count.getScore() == null) {
                continue;
            }
            jdbc.sql("""
                            insert into discovery.series_views_daily (series_id, day, views)
                            values (:series, :day, :views)
                            on conflict (series_id, day)
                            do update set views = greatest(series_views_daily.views, excluded.views)
                            """)
                    .param("series", UUID.fromString(count.getValue()))
                    .param("day", day)
                    .param("views", count.getScore().longValue())
                    .update();
        }
    }

    /** Today, last 7 days (today included) and all time, from the daily counts. Follows and ratings are kept. */
    void recomputeStats(LocalDate today) {
        jdbc.sql("""
                        insert into discovery.series_stats (series_id, views_today, views_7d, views_all, updated_at)
                        select series_id,
                               coalesce(sum(views) filter (where day = :today), 0),
                               coalesce(sum(views) filter (where day >= :weekStart and day <= :today), 0),
                               sum(views),
                               now()
                        from discovery.series_views_daily
                        group by series_id
                        on conflict (series_id) do update
                        set views_today = excluded.views_today,
                            views_7d = excluded.views_7d,
                            views_all = excluded.views_all,
                            updated_at = excluded.updated_at
                        """)
                .param("today", today)
                .param("weekStart", today.minusDays(6))
                .update();
        pushPopularity();
    }

    /** Browse's "Popular" sort is a catalog query, so the catalog keeps its own copy of the 7-day views. */
    private void pushPopularity() {
        Map<UUID, Long> views = new HashMap<>();
        jdbc.sql("select series_id, views_7d from discovery.series_stats").query(rs -> {
            views.put(rs.getObject("series_id", UUID.class), rs.getLong("views_7d"));
        });
        popularity.update(views);
    }
}
