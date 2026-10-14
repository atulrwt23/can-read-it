package com.canreadit.reading.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.identity.UserDeleted;
import com.canreadit.shared.AuthCookies;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The signed-in reader's library, progress, read marks and history import, through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class ReadingIT {

    static final String PASSWORD = "correct horse battery";
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JsonMapper json;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    CatalogFixtures fx;
    Cookie reader;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        jdbc.sql("truncate reading.library_entries, reading.reading_progress, reading.read_chapters")
                .update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        reader = signUp("reader@example.com", "a_reader");

        var tower = fx.series("tower").title("Tower").create();
        for (String number : List.of("1", "2", "10")) {
            fx.published(tower.editionId(), number, NOW.minus(3, ChronoUnit.DAYS));
        }
        fx.chapter(tower.editionId(), "11", "SCHEDULED", NOW.plus(1, ChronoUnit.DAYS), "FREE");
        var garden = fx.series("garden").title("Garden").type("NOVEL").create();
        fx.published(garden.editionId(), "1", NOW.minus(1, ChronoUnit.DAYS));
        fx.series("hidden").visibility("DRAFT").create();
    }

    @Test
    void followsSeriesIntoTheLibrary() {
        assertThat(write(HttpMethod.PUT, "/api/v1/me/library/tower", null)).hasStatus(204);
        assertThat(write(HttpMethod.PUT, "/api/v1/me/library/tower", null)).hasStatus(204);
        assertThat(write(HttpMethod.PUT, "/api/v1/me/library/garden", null)).hasStatus(204);
        assertThat(write(HttpMethod.PUT, "/api/v1/me/library/hidden", null))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("series_not_found");

        // Newest first, one per page.
        var first = get("/api/v1/me/library?limit=1");
        assertThat(first).hasStatusOk();
        assertThat(first.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(first).bodyJson().extractingPath("$.items[0].series.slug").isEqualTo("garden");
        String cursor = read(first, "$.nextCursor");
        var second = get("/api/v1/me/library?limit=1&cursor=" + cursor);
        assertThat(second).bodyJson().extractingPath("$.items[0].series.slug").isEqualTo("tower");
        assertThat(second).bodyJson().extractingPath("$.nextCursor").isNull();

        assertThat(get("/api/v1/me/series/tower"))
                .bodyJson()
                .extractingPath("$.following")
                .isEqualTo(true);
        assertThat(write(HttpMethod.DELETE, "/api/v1/me/library/tower", null)).hasStatus(204);
        assertThat(get("/api/v1/me/series/tower"))
                .bodyJson()
                .extractingPath("$.following")
                .isEqualTo(false);
        assertThat(get("/api/v1/me/library"))
                .bodyJson()
                .extractingPath("$.items[*].series.slug")
                .asArray()
                .containsExactly("garden");
    }

    @Test
    void keepsProgressAndReadChapters() {
        assertThat(progress("tower", "1", 0.5, false)).hasStatus(204);
        var state = get("/api/v1/me/series/tower");
        assertThat(state).bodyJson().extractingPath("$.progress.number").isEqualTo("1");
        assertThat(state).bodyJson().extractingPath("$.progress.position").isEqualTo(0.5);
        assertThat(state).bodyJson().extractingPath("$.readChapters").asArray().isEmpty();

        assertThat(progress("tower", "10", 1.0, true)).hasStatus(204);
        assertThat(progress("tower", "2", 1.0, true)).hasStatus(204);
        state = get("/api/v1/me/series/tower");
        assertThat(state).bodyJson().extractingPath("$.progress.number").isEqualTo("2");
        // Numeric order, not text order.
        assertThat(state).bodyJson().extractingPath("$.readChapters").asArray().containsExactly("2", "10");

        // Scheduled chapters can't be read yet, so they can't be in progress either.
        assertThat(progress("tower", "11", 0.1, false))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("chapter_not_found");
        assertThat(progress("tower", "1.234", 0.1, false)).hasStatus(400);

        assertThat(progress("garden", "1", 0.2, false)).hasStatus(204);
        var history = get("/api/v1/me/history");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].series.slug")
                .asArray()
                .containsExactly("garden", "tower");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[1].progress.number")
                .isEqualTo("2");
    }

    @Test
    void importsGuestHistoryWithoutOverwritingNewerProgress() {
        assertThat(progress("tower", "10", 0.3, false)).hasStatus(204);

        var result = write(
                HttpMethod.POST,
                "/api/v1/me/history/import",
                Map.of(
                        "series",
                        List.of(
                                // Older than the account's own progress: only its read marks count.
                                Map.of(
                                        "slug",
                                        "tower",
                                        "number",
                                        "2",
                                        "position",
                                        0.9,
                                        "updatedAt",
                                        NOW.minus(2, ChronoUnit.DAYS).toString(),
                                        "read",
                                        List.of("1", "2.0", "99")),
                                Map.of(
                                        "slug",
                                        "garden",
                                        "number",
                                        "1",
                                        "position",
                                        0.4,
                                        "updatedAt",
                                        NOW.minus(1, ChronoUnit.HOURS).toString()),
                                Map.of("slug", "gone", "number", "1", "position", 0, "updatedAt", NOW.toString()))));
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.imported").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.skipped").isEqualTo(1);

        var tower = get("/api/v1/me/series/tower");
        assertThat(tower).bodyJson().extractingPath("$.progress.number").isEqualTo("10");
        assertThat(tower).bodyJson().extractingPath("$.readChapters").asArray().containsExactly("1", "2");
        assertThat(get("/api/v1/me/series/garden"))
                .bodyJson()
                .extractingPath("$.progress.position")
                .isEqualTo(0.4);
    }

    @Test
    void needsASignedInReaderAndTheCsrfHeader() {
        assertThat(mvc.get().uri("/api/v1/me/library")).hasStatus(401);
        assertThat(mvc.put().uri("/api/v1/me/library/tower").cookie(reader)).hasStatus(403);
    }

    @Test
    void forgetsEverythingWhenTheAccountIsErased() throws InterruptedException {
        write(HttpMethod.PUT, "/api/v1/me/library/tower", null);
        progress("tower", "1", 1.0, true);
        UUID userId =
                jdbc.sql("select id from identity.users").query(UUID.class).single();

        tx.executeWithoutResult(status -> events.publishEvent(new UserDeleted(userId, Instant.now())));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (rows() > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(rows()).isZero();
    }

    // --- Helpers

    private long rows() {
        return jdbc.sql("""
                        select (select count(*) from reading.library_entries)
                             + (select count(*) from reading.reading_progress)
                             + (select count(*) from reading.read_chapters)
                        """).query(Long.class).single();
    }

    private MvcTestResult progress(String slug, String number, double position, boolean finished) {
        return write(
                HttpMethod.PUT,
                "/api/v1/me/progress/" + slug,
                Map.of("number", number, "position", position, "finished", finished));
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(reader).exchange();
    }

    private MvcTestResult write(HttpMethod method, String uri, @Nullable Object body) {
        Cookie csrf = mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        var request = mvc.method(method).uri(uri).cookie(reader, csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return request.exchange();
    }

    private Cookie signUp(String email, String handle) {
        mvc.post()
                .uri("/api/v1/auth/password/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "handle", handle)))
                .exchange();
        var login = mvc.post()
                .uri("/api/v1/auth/password/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))
                .exchange();
        assertThat(login).hasStatusOk();
        return login.getResponse().getCookie(AuthCookies.ACCESS);
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
