package com.canreadit.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomeApiIT {

    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    CatalogFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        jdbc.sql("truncate discovery.series_stats").update();
    }

    @Test
    void ranksPublishedSeriesPerPeriod() {
        var a = fx.series("alpha")
                .lastPublishedAt(NOW.minus(3, ChronoUnit.HOURS))
                .create();
        var b = fx.series("beta")
                .lastPublishedAt(NOW.minus(1, ChronoUnit.HOURS))
                .create();
        var hidden =
                fx.series("hidden").visibility("UNLISTED").lastPublishedAt(NOW).create();
        stats(a.id(), 10, 500, 9000);
        stats(b.id(), 50, 100, 20000);
        stats(hidden.id(), 999, 999, 999999);

        assertThat(mvc.get().uri("/api/v1/home"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.popular.today[*].series.slug")
                        .asArray()
                        .containsExactly("beta", "alpha"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.popular.week[*].series.slug")
                        .asArray()
                        .containsExactly("alpha", "beta"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.popular.allTime[*].rank")
                        .asArray()
                        .containsExactly(1, 2))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.popular.allTime[0].views")
                        .isEqualTo(20000))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.featured[*].slug")
                        .asArray()
                        .containsExactly("alpha", "beta"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.latestUpdates[*].slug")
                        .asArray()
                        .containsExactly("beta", "alpha"));
    }

    @Test
    void featuresLatestUpdatesUntilThereAreStats() {
        fx.series("only").lastPublishedAt(NOW).create();

        assertThat(mvc.get().uri("/api/v1/home"))
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.featured[*].slug")
                        .asArray()
                        .containsExactly("only"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.popular.today")
                        .asArray()
                        .isEmpty());
    }

    private void stats(UUID seriesId, long today, long week, long all) {
        jdbc.sql("""
                        insert into discovery.series_stats (series_id, views_today, views_7d, views_all)
                        values (:id, :t, :w, :a)
                        """)
                .param("id", seriesId)
                .param("t", today)
                .param("w", week)
                .param("a", all)
                .update();
    }
}
