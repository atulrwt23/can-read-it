package com.canreadit.analytics.internal;

import com.canreadit.shared.Ids;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code analytics} schema: raw events and their daily counts. */
@Repository
class AnalyticsRepository {

    private final JdbcClient jdbc;

    AnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One event as stored. */
    record Event(
            EventName name,
            Instant at,
            @Nullable String visitor,
            @Nullable String path,
            @Nullable UUID seriesId,
            @Nullable String chapter,
            @Nullable Integer value) {}

    record DayCount(LocalDate day, String name, long events, long visitors) {}

    record SeriesCount(UUID seriesId, long events, long visitors) {}

    void insert(List<Event> events) {
        for (Event event : events) {
            jdbc.sql("""
                            insert into analytics.events
                                (id, occurred_at, name, visitor, path, series_id, chapter, value)
                            values (:id, :at, :name, :visitor, :path, :series, :chapter, :value)
                            """)
                    .param("id", Ids.newId())
                    .param("at", utc(event.at()))
                    .param("name", event.name().name())
                    .param("visitor", event.visitor())
                    .param("path", event.path())
                    .param("series", event.seriesId())
                    .param("chapter", event.chapter())
                    .param("value", event.value())
                    .update();
        }
    }

    /**
     * Recounts the days from {@code from} (UTC) onwards into {@code daily_counts}. Absolute values, so running it
     * again, late or on several instances changes nothing.
     */
    int rollUp(LocalDate from) {
        return jdbc.sql("""
                        insert into analytics.daily_counts (day, name, events, visitors)
                        select (occurred_at at time zone 'UTC')::date, name, count(*), count(distinct visitor)
                        from analytics.events
                        where occurred_at >= :from
                        group by 1, 2
                        on conflict (day, name) do update
                            set events = excluded.events, visitors = excluded.visitors
                        """)
                .param("from", utc(from.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .update();
    }

    /** Daily counts from {@code from} (inclusive) to {@code to} (exclusive). */
    List<DayCount> dailyCounts(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select day, name, events, visitors from analytics.daily_counts
                        where day >= :from and day < :to
                        order by day, name
                        """)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query((rs, n) -> new DayCount(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("name"),
                        rs.getLong("events"),
                        rs.getLong("visitors")))
                .list();
    }

    /** Counts for one day, straight from the raw events (for today, which isn't rolled up yet). */
    List<DayCount> liveCounts(LocalDate day) {
        Instant start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        return jdbc.sql("""
                        select name, count(*) as events, count(distinct visitor) as visitors
                        from analytics.events
                        where occurred_at >= :start and occurred_at < :end
                        group by name
                        order by name
                        """)
                .param("start", utc(start))
                .param("end", utc(start.plusSeconds(86_400)))
                .query((rs, n) -> new DayCount(day, rs.getString("name"), rs.getLong("events"), rs.getLong("visitors")))
                .list();
    }

    /** The series with the most events of this kind since {@code since}, most first. */
    List<SeriesCount> topSeries(EventName name, Instant since, int limit) {
        return jdbc.sql("""
                        select series_id, count(*) as events, count(distinct visitor) as visitors
                        from analytics.events
                        where name = :name and occurred_at >= :since and series_id is not null
                        group by series_id
                        order by events desc, series_id
                        limit :limit
                        """)
                .param("name", name.name())
                .param("since", utc(since))
                .param("limit", limit)
                .query((rs, n) -> new SeriesCount(
                        rs.getObject("series_id", UUID.class), rs.getLong("events"), rs.getLong("visitors")))
                .list();
    }

    /** Deletes raw events older than {@code before}; daily counts stay. */
    int deleteBefore(Instant before) {
        return jdbc.sql("delete from analytics.events where occurred_at < :before")
                .param("before", utc(before))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
