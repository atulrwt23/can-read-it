package com.canreadit.engagement.internal;

import com.canreadit.catalog.CatalogQueries;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Placeholder rating totals for the seeded series (local development and the demo beta), so series pages show an
 * average. Only totals are seeded, not individual ratings; real ratings add to them. Runs after the catalog seeder
 * and only while no series has ratings.
 */
@Component
@Profile({"local", "demo"})
@Order(30)
class LocalEngagementSeeder implements ApplicationRunner {

    private final JdbcClient jdbc;
    private final CatalogQueries catalog;
    private final Clock clock;

    LocalEngagementSeeder(JdbcClient jdbc, CatalogQueries catalog, Clock clock) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.sql("select count(*) from engagement.series_ratings")
                .query(Integer.class)
                .single();
        if (existing > 0) {
            return;
        }
        var random = new Random(11);
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (UUID id : catalog.publishedSeriesIds()) {
            int ratings = 10 + random.nextInt(2_000);
            // Averages between 3.2 and 4.9.
            double average = 3.2 + random.nextInt(18) / 10.0;
            jdbc.sql("""
                            insert into engagement.series_ratings (series_id, ratings, score_sum, updated_at)
                            values (:id, :ratings, :sum, :now)
                            on conflict (series_id) do nothing
                            """)
                    .param("id", id)
                    .param("ratings", ratings)
                    .param("sum", Math.round(average * ratings))
                    .param("now", now)
                    .update();
        }
    }
}
