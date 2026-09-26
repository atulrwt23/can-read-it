package com.canreadit.discovery.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SeriesStatsRepository {

    private final JdbcClient jdbc;

    SeriesStatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Series with views in the period, most viewed first. */
    List<Ranked> top(RankingPeriod period, int limit) {
        return jdbc.sql("""
                        select series_id, %1$s as views from discovery.series_stats
                        where %1$s > 0
                        order by %1$s desc, series_id
                        limit :limit
                        """.formatted(period.column))
                .param("limit", limit)
                .query((rs, n) -> new Ranked(rs.getObject("series_id", UUID.class), rs.getLong("views")))
                .list();
    }

    record Ranked(UUID seriesId, long views) {}
}
