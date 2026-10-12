package com.canreadit.discovery.internal;

import com.canreadit.catalog.CatalogQueries;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Invented view history so the home page rankings render in local development and the demo beta: 30 days of daily
 * views per series, from which {@link ViewRollup} computes the rankings exactly as it does for real views, plus
 * placeholder follows and ratings. Runs after the catalog seeder and only while there is no view history.
 */
@Component
@Profile({"local", "demo"})
@Order(20)
class LocalDiscoverySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDiscoverySeeder.class);
    private static final int DAYS = 30;

    private final JdbcClient jdbc;
    private final CatalogQueries catalog;
    private final ViewRollup rollup;
    private final Clock clock;

    LocalDiscoverySeeder(JdbcClient jdbc, CatalogQueries catalog, ViewRollup rollup, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.rollup = rollup;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.sql("select count(*) from discovery.series_views_daily")
                .query(Integer.class)
                .single();
        if (existing > 0) {
            return;
        }
        var random = new Random(7);
        LocalDate today = LocalDate.now(clock);
        var ids = catalog.publishedSeriesIds();
        for (UUID id : ids) {
            int perDay = 50 + random.nextInt(4_000);
            for (int ago = 0; ago < DAYS; ago++) {
                // Today is only partly over; older days vary around the series' usual readership.
                double share = ago == 0 ? 0.4 : 0.6 + random.nextDouble() * 0.8;
                jdbc.sql("insert into discovery.series_views_daily (series_id, day, views) values (:id, :day, :views)")
                        .param("id", id)
                        .param("day", today.minusDays(ago))
                        .param("views", Math.round(perDay * share))
                        .update();
            }
            long allTime = (long) perDay * DAYS;
            // Keeps follows and ratings a database may already have (stats seeded before view history existed).
            jdbc.sql("""
                            insert into discovery.series_stats (series_id, follows, rating_avg, rating_count)
                            values (:id, :follows, :rating, :ratings)
                            on conflict (series_id) do nothing
                            """)
                    .param("id", id)
                    .param("follows", allTime / (20 + random.nextInt(30)))
                    .param("rating", BigDecimal.valueOf(3.2 + random.nextInt(18) / 10.0))
                    .param("ratings", 10 + random.nextInt(2_000))
                    .update();
        }
        rollup.recomputeStats(today);
        log.info("Seeded {} days of placeholder views for {} series", DAYS, ids.size());
    }
}
