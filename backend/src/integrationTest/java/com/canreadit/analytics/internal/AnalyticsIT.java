package com.canreadit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Anonymous events in, daily counts and the staff summary out. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class AnalyticsIT {

    static final String BROWSER = "Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0";
    static final String PASSWORD = "correct horse battery";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JsonMapper json;

    @Autowired
    AnalyticsJobs jobs;

    @BeforeEach
    void setUp() {
        var fx = new CatalogFixtures(jdbc);
        fx.reset();
        var tower = fx.series("tower").title("Tower").create();
        fx.published(tower.editionId(), "1", Instant.now().minus(1, ChronoUnit.DAYS));
        fx.series("draft").visibility("DRAFT").create();
        jdbc.sql("truncate analytics.events, analytics.daily_counts").update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void keepsOnlyWhatBrowsersMaySend() {
        var sent = send(
                BROWSER,
                List.of(
                        Map.of("name", "PAGE_VIEW", "path", "/series/[slug]"),
                        Map.of("name", "CHAPTER_OPEN", "series", "tower", "chapter", "1"),
                        Map.of("name", "SEARCH", "value", 3, "series", "tower"),
                        // Only the API counts sign-ups; drafts and unknown series aren't readable.
                        Map.of("name", "SIGN_UP"),
                        Map.of("name", "SERIES_VIEW", "series", "draft"),
                        Map.of("name", "FOLLOW", "series", "nope")));
        assertThat(sent).hasStatus(204);

        var rows = jdbc.sql("select name, path, series_id is not null as has_series, chapter, value, visitor"
                        + " from analytics.events where name <> 'SIGN_UP' order by name")
                .query()
                .listOfRows();
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("CHAPTER_OPEN", "PAGE_VIEW", "SEARCH");
        assertThat(rows.get(0)).containsEntry("has_series", true).containsEntry("chapter", "1");
        assertThat(rows.get(1)).containsEntry("path", "/series/[slug]");
        // A search keeps its result count, nothing about what was searched.
        assertThat(rows.get(2)).containsEntry("value", 3).containsEntry("has_series", false);
        // One visitor, kept as an opaque daily fingerprint.
        assertThat(rows).extracting(row -> row.get("visitor")).allMatch(v -> v instanceof String s && s.length() == 32);
        assertThat(rows.stream().map(row -> row.get("visitor")).distinct()).hasSize(1);
    }

    @Test
    void ignoresCrawlersAndRejectsMalformedBatches() {
        assertThat(send("Mozilla/5.0 (compatible; Googlebot/2.1)", List.of(Map.of("name", "PAGE_VIEW", "path", "/"))))
                .hasStatus(204);
        assertThat(count()).isZero();

        assertThat(send(BROWSER, List.of(Map.of("name", "SOMETHING_ELSE")))).hasStatus(400);
        assertThat(send(BROWSER, List.of(Map.of("name", "PAGE_VIEW", "path", "/browse?q=my+email@x.com"))))
                .hasStatus(400);
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= AnalyticsRequests.MAX_BATCH; i++) {
            tooMany.add(Map.of("name", "PAGE_VIEW", "path", "/"));
        }
        assertThat(send(BROWSER, tooMany)).hasStatus(400);
        assertThat(count()).isZero();
    }

    @Test
    void countsSignUpsWithoutRecordingWho() throws InterruptedException {
        signIn("reader@example.com", "a_reader", false);

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (signUps() == 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(signUps()).isPositive();
        assertThat(jdbc.sql("select count(*) from analytics.events where name = 'SIGN_UP' and visitor is not null")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void rollsUpDaysAndForgetsOldEvents() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        insert("PAGE_VIEW", today.minusDays(1).atTime(10, 0), "a");
        insert("PAGE_VIEW", today.minusDays(1).atTime(11, 0), "a");
        insert("PAGE_VIEW", today.minusDays(1).atTime(12, 0), "b");
        insert("PAGE_VIEW", today.minusDays(100).atTime(12, 0), "c");

        jobs.rollUp();
        var yesterday = jdbc.sql(
                        "select events, visitors from analytics.daily_counts where day = :day and name = 'PAGE_VIEW'")
                .param("day", java.sql.Date.valueOf(today.minusDays(1)))
                .query()
                .singleRow();
        assertThat(yesterday).containsEntry("events", 3L).containsEntry("visitors", 2L);

        jobs.cleanUp();
        assertThat(count()).isEqualTo(3);
        assertThat(jdbc.sql("select count(*) from analytics.daily_counts where name = 'PAGE_VIEW'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void showsStaffTheDailyCountsAndTopSeries() {
        send(
                BROWSER,
                List.of(
                        Map.of("name", "PAGE_VIEW", "path", "/"),
                        Map.of("name", "PAGE_VIEW", "path", "/browse"),
                        Map.of("name", "CHAPTER_OPEN", "series", "tower", "chapter", "1")));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Yesterday's events are counted by the hourly roll-up; today's are counted live.
        insert("PAGE_VIEW", today.minusDays(1).atTime(9, 0), "x");
        jobs.rollUp();

        assertThat(mvc.get().uri("/api/v1/admin/analytics")).hasStatus(401);
        Cookie reader = signIn("reader@example.com", "a_reader", true);
        assertThat(mvc.get().uri("/api/v1/admin/analytics").cookie(reader)).hasStatus(403);

        Cookie admin = signIn("admin@example.com", "the_admin", true);
        var summary =
                mvc.get().uri("/api/v1/admin/analytics?days=3").cookie(admin).exchange();
        assertThat(summary).hasStatusOk();
        assertThat(summary).bodyJson().extractingPath("$.days").asArray().hasSize(3);
        assertThat(summary)
                .bodyJson()
                .extractingPath("$.days[1].counts.PAGE_VIEW.events")
                .isEqualTo(1);
        assertThat(summary)
                .bodyJson()
                .extractingPath("$.days[2].counts.PAGE_VIEW.events")
                .isEqualTo(2);
        assertThat(summary)
                .bodyJson()
                .extractingPath("$.days[2].counts.PAGE_VIEW.visitors")
                .isEqualTo(1);
        assertThat(summary).bodyJson().extractingPath("$.topSeries[0].slug").isEqualTo("tower");
        assertThat(summary).bodyJson().extractingPath("$.topSeries[0].opens").isEqualTo(1);
        assertThat(summary).bodyJson().extractingPath("$.names").asArray().contains("SIGN_UP", "PAGE_VIEW");
    }

    // --- Helpers

    private MvcTestResult send(String userAgent, List<Map<String, Object>> events) {
        return mvc.post()
                .uri("/api/v1/analytics/events")
                .header(HttpHeaders.USER_AGENT, userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("events", events)))
                .exchange();
    }

    private void insert(String name, java.time.LocalDateTime at, String visitor) {
        jdbc.sql("insert into analytics.events (id, occurred_at, name, visitor) values (uuidv7(), :at, :name, :v)")
                .param("at", OffsetDateTime.of(at, ZoneOffset.UTC))
                .param("name", name)
                .param("v", visitor)
                .update();
    }

    /** Browser events; sign-ups are counted asynchronously and may arrive from an earlier test's accounts. */
    private long count() {
        return jdbc.sql("select count(*) from analytics.events where name <> 'SIGN_UP'")
                .query(Long.class)
                .single();
    }

    private long signUps() {
        return jdbc.sql("select count(*) from analytics.events where name = 'SIGN_UP'")
                .query(Long.class)
                .single();
    }

    private Cookie signIn(String email, String handle, boolean confirmed) {
        mvc.post()
                .uri("/api/v1/auth/password/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "handle", handle)))
                .exchange();
        if (confirmed) {
            jdbc.sql("update identity.users set email_verified_at = now() where email = :email")
                    .param("email", email)
                    .update();
        }
        var login = mvc.post()
                .uri("/api/v1/auth/password/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))
                .exchange();
        assertThat(login).hasStatusOk();
        return login.getResponse().getCookie(AuthCookies.ACCESS);
    }
}
