package com.canreadit.moderation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.shared.AuthCookies;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Readers report comments; moderators work the queue, and every decision lands in the audit log. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class ModerationIT {

    static final String PASSWORD = "correct horse battery";
    static final String COMMENTS = "/api/v1/series/tower/chapters/1/comments";
    static final String QUEUE = "/api/v1/admin/moderation/comments";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JsonMapper json;

    Cookie ana;
    Cookie ben;
    Cookie cy;
    String commentId;

    @BeforeEach
    void setUp() {
        var fx = new CatalogFixtures(jdbc);
        fx.reset();
        var tower = fx.series("tower").title("Tower").create();
        fx.published(tower.editionId(), "1", Instant.now().minus(1, ChronoUnit.DAYS));
        jdbc.sql("truncate engagement.comments, engagement.reports, moderation.actions")
                .update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        ana = signUp("ana@example.com", "ana");
        ben = signUp("ben@example.com", "ben");
        cy = signUp("cy@example.com", "cyd");
        commentId =
                read(write(ana, HttpMethod.POST, COMMENTS, Map.of("body", "Buy cheap coins at example.test")), "$.id");
    }

    @Test
    void readersReportCommentsAndModeratorsKeepOrRemoveThem() throws UnsupportedEncodingException {
        String reports = "/api/v1/comments/" + commentId + "/reports";
        assertThat(write(ben, HttpMethod.POST, reports, Map.of("reason", "SPAM", "note", " Selling coins ")))
                .hasStatus(204);
        // Reporting twice counts once.
        assertThat(write(ben, HttpMethod.POST, reports, Map.of("reason", "SPAM")))
                .hasStatus(204);
        assertThat(write(cy, HttpMethod.POST, reports, Map.of("reason", "HARASSMENT")))
                .hasStatus(204);
        assertThat(write(ana, HttpMethod.POST, reports, Map.of("reason", "SPAM")))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("cannot_report_own_comment");
        assertThat(write(
                        ben,
                        HttpMethod.POST,
                        "/api/v1/comments/" + java.util.UUID.randomUUID() + "/reports",
                        Map.of("reason", "SPAM")))
                .hasStatus(404);
        assertThat(write(ben, HttpMethod.POST, reports, Map.of("reason", "RUDE")))
                .hasStatus(400);

        // Readers can't see the queue; moderators can.
        assertThat(mvc.get().uri(QUEUE).cookie(ben)).hasStatus(403);
        Cookie moderator = staff("mod@example.com", "a_mod", "MODERATOR");
        var queue = mvc.get().uri(QUEUE).cookie(moderator).exchange();
        assertThat(queue).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(1);
        assertThat(queue).bodyJson().extractingPath("$.items[0].reports").isEqualTo(2);
        assertThat(queue).bodyJson().extractingPath("$.items[0].reasons.SPAM").isEqualTo(1);
        assertThat(queue)
                .bodyJson()
                .extractingPath("$.items[0].reasons.HARASSMENT")
                .isEqualTo(1);
        assertThat(queue).bodyJson().extractingPath("$.items[0].notes[0]").isEqualTo("Selling coins");
        assertThat(queue).bodyJson().extractingPath("$.items[0].author.handle").isEqualTo("ana");
        assertThat(queue)
                .bodyJson()
                .extractingPath("$.items[0].place.seriesSlug")
                .isEqualTo("tower");
        assertThat(queue)
                .bodyJson()
                .extractingPath("$.items[0].place.chapterNumber")
                .isEqualTo("1");
        // Who reported stays private.
        assertThat(queue.getResponse().getContentAsString())
                .doesNotContain("ben")
                .doesNotContain("cyd");

        // Keeping it closes the reports; the comment stays up.
        assertThat(write(moderator, HttpMethod.POST, QUEUE + "/" + commentId + "/keep", Map.of("note", "Not spam")))
                .hasStatus(204);
        assertThat(mvc.get().uri(QUEUE).cookie(moderator))
                .bodyJson()
                .extractingPath("$.total")
                .isEqualTo(0);
        assertThat(write(moderator, HttpMethod.POST, QUEUE + "/" + commentId + "/keep", Map.of()))
                .hasStatus(404);
        assertThat(mvc.get().uri(COMMENTS))
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .hasSize(1);

        // A new report reopens it; removing hides it from readers.
        assertThat(write(cy, HttpMethod.POST, reports, Map.of("reason", "SPAM")))
                .hasStatus(204);
        Cookie admin = staff("admin@example.com", "the_admin", null);
        assertThat(write(admin, HttpMethod.POST, QUEUE + "/" + commentId + "/remove", Map.of()))
                .hasStatus(204);
        assertThat(mvc.get().uri(COMMENTS))
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .isEmpty();
        assertThat(mvc.get().uri(QUEUE).cookie(admin))
                .bodyJson()
                .extractingPath("$.total")
                .isEqualTo(0);
        assertThat(write(admin, HttpMethod.POST, QUEUE + "/" + commentId + "/remove", Map.of()))
                .hasStatus(404);
        assertThat(write(cy, HttpMethod.POST, reports, Map.of("reason", "SPAM")))
                .hasStatus(404);

        // The audit log, newest first.
        var actions =
                mvc.get().uri("/api/v1/admin/moderation/actions").cookie(admin).exchange();
        assertThat(actions).bodyJson().extractingPath("$[0].action").isEqualTo("REMOVE_COMMENT");
        assertThat(actions).bodyJson().extractingPath("$[0].moderator.handle").isEqualTo("the_admin");
        assertThat(actions).bodyJson().extractingPath("$[1].action").isEqualTo("KEEP_COMMENT");
        assertThat(actions).bodyJson().extractingPath("$[1].note").isEqualTo("Not spam");
    }

    @Test
    void authorsDeletingTheirCommentClearsItsReports() {
        write(ben, HttpMethod.POST, "/api/v1/comments/" + commentId + "/reports", Map.of("reason", "SPOILER"));
        assertThat(write(ana, HttpMethod.DELETE, "/api/v1/me/comments/" + commentId, null))
                .hasStatus(204);
        assertThat(jdbc.sql("select count(*) from engagement.reports")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    // --- Helpers

    /** A confirmed account with a staff role, signed in again so its token carries the role. */
    private Cookie staff(String email, String handle, @Nullable String role) {
        signUp(email, handle);
        if (role != null) {
            jdbc.sql("""
                            insert into identity.user_roles (user_id, role, granted_at)
                            select id, :role, now() from identity.users where email = :email
                            """).param("role", role).param("email", email).update();
        }
        return login(email);
    }

    private MvcTestResult write(Cookie reader, HttpMethod method, String uri, @Nullable Object body) {
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
        jdbc.sql("update identity.users set email_verified_at = now() where email = :email")
                .param("email", email)
                .update();
        return login(email);
    }

    private Cookie login(String email) {
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
