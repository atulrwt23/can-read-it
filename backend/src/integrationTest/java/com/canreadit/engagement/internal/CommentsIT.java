package com.canreadit.engagement.internal;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Chapter comments: public reading, writing with a confirmed email, deleting your own, account erasure. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class CommentsIT {

    static final String PASSWORD = "correct horse battery";
    static final String COMMENTS = "/api/v1/series/tower/chapters/1/comments";

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
        var tower = fx.series("tower").title("Tower").create();
        fx.published(tower.editionId(), "1", Instant.now().minus(1, ChronoUnit.DAYS));
        fx.chapter(tower.editionId(), "2", "SCHEDULED", Instant.now().plus(1, ChronoUnit.DAYS), "FREE");
        jdbc.sql("truncate engagement.comments").update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void readersWithAConfirmedEmailCommentAndEveryoneReadsThem() {
        var empty = mvc.get().uri(COMMENTS).exchange();
        assertThat(empty)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .isEmpty();
        assertThat(empty.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("public");
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/2/comments")).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/series/nope/chapters/1/comments")).hasStatus(404);

        Cookie csrf = csrf();
        assertThat(mvc.post()
                        .uri(COMMENTS)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .hasStatus(401);
        Cookie unconfirmed = signUp("una@example.com", "una", false);
        assertThat(post(unconfirmed, "hi"))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("email_not_confirmed");

        Cookie ana = signUp("ana@example.com", "ana", true);
        var first = post(ana, "  First!\r\n\r\n\r\n\r\nWhat a cliffhanger.\u0007  ");
        assertThat(first).hasStatus(201).bodyJson().extractingPath("$.body").isEqualTo("First!\n\nWhat a cliffhanger.");
        assertThat(first).bodyJson().extractingPath("$.author.handle").isEqualTo("ana");
        assertThat(post(ana, "Second")).hasStatus(201);
        assertThat(post(ana, "   "))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("comment_empty");
        assertThat(post(ana, "x".repeat(2001)))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("comment_too_long");
        assertThat(write(ana, HttpMethod.POST, "/api/v1/series/tower/chapters/2/comments", Map.of("body", "early")))
                .hasStatus(404);

        // Newest first, one per page.
        var page1 = mvc.get().uri(COMMENTS + "?limit=1").exchange();
        assertThat(page1).bodyJson().extractingPath("$.items[0].body").isEqualTo("Second");
        assertThat(page1)
                .bodyJson()
                .extractingPath("$.items[0].author.displayName")
                .isNotNull();
        String cursor = read(page1, "$.nextCursor");
        var page2 = mvc.get().uri(COMMENTS + "?limit=1&cursor=" + cursor).exchange();
        assertThat(page2).bodyJson().extractingPath("$.items[0].author.handle").isEqualTo("ana");
        assertThat(page2).bodyJson().extractingPath("$.nextCursor").isNull();
        // Nothing identifies the account beyond its public name.
        assertThat(page2).bodyJson().doesNotHavePath("$.items[0].author.userId");
        assertThat(mvc.get().uri(COMMENTS + "?cursor=garbage")).hasStatus(400);
    }

    @Test
    void authorsDeleteTheirOwnComments() {
        Cookie ana = signUp("ana@example.com", "ana", true);
        Cookie ben = signUp("ben@example.com", "ben", true);
        String id = read(post(ana, "Mine"), "$.id");

        assertThat(write(ben, HttpMethod.DELETE, "/api/v1/me/comments/" + id, null))
                .hasStatus(404);
        assertThat(write(ana, HttpMethod.DELETE, "/api/v1/me/comments/" + id, null))
                .hasStatus(204);
        assertThat(mvc.get().uri(COMMENTS))
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .isEmpty();
    }

    @Test
    void commentsLeaveWithTheirAccount() throws InterruptedException {
        Cookie ana = signUp("ana@example.com", "ana", true);
        Cookie ben = signUp("ben@example.com", "ben", true);
        post(ana, "From Ana");
        post(ben, "From Ben");
        UUID anaId = userId("ana@example.com");
        UUID benId = userId("ben@example.com");

        // Hidden while the account waits to be erased...
        jdbc.sql("update identity.users set status = 'PENDING_DELETION' where id = :id")
                .param("id", benId)
                .update();
        assertThat(bodies()).containsExactly("From Ana");

        // ...and gone once it is.
        tx.executeWithoutResult(status -> events.publishEvent(new UserDeleted(anaId, Instant.now())));
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (count(anaId) > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(count(anaId)).isZero();
        assertThat(count(benId)).isEqualTo(1);
    }

    // --- Helpers

    private List<String> bodies() {
        try {
            return JsonPath.read(
                    mvc.get().uri(COMMENTS).exchange().getResponse().getContentAsString(), "$.items[*].body");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(UUID userId) {
        return jdbc.sql("select count(*) from engagement.comments where user_id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }

    private UUID userId(String email) {
        return jdbc.sql("select id from identity.users where email = :email")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult post(Cookie reader, String body) {
        return write(reader, HttpMethod.POST, COMMENTS, Map.of("body", body));
    }

    private Cookie csrf() {
        return mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    private MvcTestResult write(Cookie reader, HttpMethod method, String uri, @Nullable Object body) {
        Cookie csrf = csrf();
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

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
