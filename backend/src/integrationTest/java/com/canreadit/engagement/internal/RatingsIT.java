package com.canreadit.engagement.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.identity.UserDeleted;
import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Series ratings: the public average, and each reader's own score through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class RatingsIT {

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
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        var fx = new CatalogFixtures(jdbc);
        fx.reset();
        fx.series("tower").title("Tower").create();
        fx.series("hidden").visibility("DRAFT").create();
        jdbc.sql("truncate engagement.ratings, engagement.series_ratings").update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void anyoneSeesTheAverageOfReaderScores() {
        var empty = mvc.get().uri("/api/v1/series/tower/rating").exchange();
        assertThat(empty).hasStatusOk().bodyJson().extractingPath("$.count").isEqualTo(0);
        assertThat(empty).bodyJson().extractingPath("$.average").isNull();
        assertThat(empty.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("public");
        assertThat(mvc.get().uri("/api/v1/series/hidden/rating")).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/series/nope/rating")).hasStatus(404);

        Cookie ana = signUp("ana@example.com", "ana", true);
        Cookie ben = signUp("ben@example.com", "ben", true);

        var rated = rate(ana, 4);
        assertThat(rated).hasStatusOk().bodyJson().extractingPath("$.score").isEqualTo(4);
        assertThat(rated).bodyJson().extractingPath("$.series.count").isEqualTo(1);
        assertThat(rated.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        rate(ben, 2);
        assertAverage("3.0", 2);

        // Rating again replaces the score; the same score again changes nothing.
        rate(ana, 5);
        rate(ana, 5);
        assertAverage("3.5", 2);
        assertThat(mvc.get().uri("/api/v1/me/ratings/tower").cookie(ana))
                .bodyJson()
                .extractingPath("$.score")
                .isEqualTo(5);

        var removed = write(ana, HttpMethod.DELETE, "/api/v1/me/ratings/tower", null);
        assertThat(removed).hasStatusOk().bodyJson().extractingPath("$.score").isNull();
        assertAverage("2.0", 1);
        assertThat(mvc.get().uri("/api/v1/me/ratings/tower").cookie(ana))
                .bodyJson()
                .extractingPath("$.score")
                .isNull();
    }

    @Test
    void ratingNeedsAConfirmedEmailAndAScoreFromOneToFive() {
        Cookie csrf = mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(mvc.put()
                        .uri("/api/v1/me/ratings/tower")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4}"))
                .hasStatus(401);

        Cookie unconfirmed = signUp("ana@example.com", "ana", false);
        assertThat(rate(unconfirmed, 4))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("email_not_confirmed");

        Cookie reader = signUp("ben@example.com", "ben", true);
        assertThat(rate(reader, 0)).hasStatus(400);
        assertThat(rate(reader, 6)).hasStatus(400);
        assertThat(write(reader, HttpMethod.PUT, "/api/v1/me/ratings/hidden", Map.of("score", 3)))
                .hasStatus(404);
        assertAverage(null, 0);
    }

    @Test
    void deletedAccountsNoLongerCount() throws InterruptedException {
        Cookie ana = signUp("ana@example.com", "ana", true);
        Cookie ben = signUp("ben@example.com", "ben", true);
        rate(ana, 1);
        rate(ben, 5);
        UUID anaId = jdbc.sql("select id from identity.users where email = 'ana@example.com'")
                .query(UUID.class)
                .single();

        tx.executeWithoutResult(status -> events.publishEvent(new UserDeleted(anaId, Instant.now())));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (count() != 1 && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertAverage("5.0", 1);
    }

    // --- Helpers

    private void assertAverage(@Nullable String average, int count) {
        var result = mvc.get().uri("/api/v1/series/tower/rating").exchange();
        assertThat(result).bodyJson().extractingPath("$.count").isEqualTo(count);
        if (average == null) {
            assertThat(result).bodyJson().extractingPath("$.average").isNull();
        } else {
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.average")
                    .asNumber()
                    .satisfies(n -> assertThat(n.doubleValue()).isEqualTo(Double.parseDouble(average)));
        }
    }

    private long count() {
        return jdbc.sql("select count(*) from engagement.ratings")
                .query(Long.class)
                .single();
    }

    private MvcTestResult rate(Cookie reader, int score) {
        return write(reader, HttpMethod.PUT, "/api/v1/me/ratings/tower", Map.of("score", score));
    }

    private MvcTestResult write(Cookie reader, HttpMethod method, String uri, @Nullable Object body) {
        Cookie csrf = mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        var request = mvc.method(method).uri(uri).cookie(reader, csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return request.exchange();
    }

    private Cookie signUp(String email, String handle, boolean confirmed) {
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
