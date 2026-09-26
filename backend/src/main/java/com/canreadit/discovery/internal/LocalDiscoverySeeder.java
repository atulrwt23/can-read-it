package com.canreadit.discovery.internal;

import com.canreadit.catalog.CatalogQueries;
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
 * Fake view stats so the home page rankings render in local development. Runs after the catalog
 * seeder and only when there are no stats yet.
 */
@Component
@Profile("local")
@Order(20)
class LocalDiscoverySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDiscoverySeeder.class);

    private final JdbcClient jdbc;
    private final CatalogQueries catalog;

    LocalDiscoverySeeder(JdbcClient jdbc, CatalogQueries catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.sql("select count(*) from discovery.series_stats")
                .query(Integer.class)
                .single();
        if (existing > 0) {
            return;
        }
        var random = new Random(7);
        var ids = catalog.publishedSeriesIds();
        for (UUID id : ids) {
            long today = 50L + random.nextInt(5_000);
            long week = today * (3 + random.nextInt(5)) + random.nextInt(2_000);
            long all = week * (4 + random.nextInt(40));
            jdbc.sql("""
                            insert into discovery.series_stats
                                (series_id, views_today, views_7d, views_all, follows, rating_avg, rating_count)
                            values (:id, :today, :week, :all, :follows, :rating, :ratings)
                            """)
                    .param("id", id)
                    .param("today", today)
                    .param("week", week)
                    .param("all", all)
                    .param("follows", all / (20 + random.nextInt(30)))
                    .param("rating", java.math.BigDecimal.valueOf(3.2 + random.nextInt(18) / 10.0))
                    .param("ratings", 10 + random.nextInt(2_000))
                    .update();
        }
        log.info("Seeded placeholder view stats for {} series", ids.size());
    }
}
