package com.canreadit.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ViewsIT {

    static final String BROWSER = "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ViewRollup rollup;

    CatalogFixtures fx;
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        jdbc.sql("truncate discovery.series_stats, discovery.series_views_daily")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void countsOncePerVisitorSeriesAndDay() {
        var series = fx.series("ember-road").create();

        assertThat(view("ember-road", "203.0.113.1", BROWSER)).hasStatus(204);
        assertThat(view("ember-road", "203.0.113.1", BROWSER)).hasStatus(204);
        assertThat(score(series.id())).isEqualTo(1);

        assertThat(view("ember-road", "203.0.113.1", "Mozilla/5.0 (iPhone) Safari/605.1"))
                .hasStatus(204);
        assertThat(view("ember-road", "203.0.113.2", BROWSER)).hasStatus(204);
        assertThat(score(series.id())).isEqualTo(3);
    }

    @Test
    void ignoresCrawlers() {
        var series = fx.series("ember-road").create();

        assertThat(view("ember-road", "203.0.113.1", "Mozilla/5.0 (compatible; Googlebot/2.1)"))
                .hasStatus(204);
        assertThat(view("ember-road", "203.0.113.1", "")).hasStatus(204);
        assertThat(score(series.id())).isZero();
    }

    @Test
    void countsOnlySeriesReadersCanOpen() {
        var unlisted = fx.series("by-link").visibility("UNLISTED").create();
        fx.series("secret-draft").visibility("DRAFT").create();

        assertThat(view("by-link", "203.0.113.1", BROWSER)).hasStatus(204);
        assertThat(score(unlisted.id())).isEqualTo(1);
        assertThat(view("secret-draft", "203.0.113.1", BROWSER)).hasStatus(404);
        assertThat(view("no-such-series", "203.0.113.1", BROWSER)).hasStatus(404);
    }

    @Test
    void rollsUpIntoDailyCountsAndRankings() {
        var series = fx.series("ember-road").create();
        var other = fx.series("tide-house").create();
        view("ember-road", "203.0.113.1", BROWSER);
        view("ember-road", "203.0.113.2", BROWSER);
        // Older history already in Postgres, and a count Redis no longer has (it only ever goes up).
        daily(series.id(), today.minusDays(3), 40);
        daily(series.id(), today.minusDays(20), 500);
        daily(other.id(), today, 9);

        rollup.rollUp();
        rollup.rollUp();

        assertThat(dailyViews(series.id(), today)).isEqualTo(2);
        assertThat(dailyViews(other.id(), today)).isEqualTo(9);
        assertThat(stats(series.id())).containsExactly(2L, 42L, 542L);
        assertThat(stats(other.id())).containsExactly(9L, 9L, 9L);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult view(String slug, String ip, String agent) {
        var request = mvc.post().uri("/api/v1/series/{slug}/views", slug).header("CF-Connecting-IP", ip);
        if (!agent.isEmpty()) {
            request = request.header("User-Agent", agent);
        }
        return request.exchange();
    }

    private long score(UUID series) {
        Double score = redis.opsForZSet().score(ViewCounter.dayKey(today), series.toString());
        return score == null ? 0 : score.longValue();
    }

    private void daily(UUID series, LocalDate day, long views) {
        jdbc.sql("insert into discovery.series_views_daily (series_id, day, views) values (:s, :d, :v)")
                .param("s", series)
                .param("d", day)
                .param("v", views)
                .update();
    }

    private long dailyViews(UUID series, LocalDate day) {
        return jdbc.sql("select views from discovery.series_views_daily where series_id = :s and day = :d")
                .param("s", series)
                .param("d", day)
                .query(Long.class)
                .single();
    }

    private java.util.List<Long> stats(UUID series) {
        return jdbc.sql("select views_today, views_7d, views_all from discovery.series_stats where series_id = :s")
                .param("s", series)
                .query((rs, n) ->
                        java.util.List.of(rs.getLong("views_today"), rs.getLong("views_7d"), rs.getLong("views_all")))
                .single();
    }
}
