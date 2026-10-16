package com.canreadit.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.catalog.ChapterPublished;
import com.canreadit.identity.UserDeleted;
import com.canreadit.notifications.EmailMessage;
import com.canreadit.shared.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
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

/** New chapters of followed series, queued per reader and sent as one throttled email; settings and unsubscribing. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class NotificationsIT {

    static final String PASSWORD = "correct horse battery";
    static final String READER = "reader@example.com";

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

    @Autowired
    ChapterDigests digests;

    @Autowired
    RecordingEmailSender emails;

    CatalogFixtures fx;
    CatalogFixtures.Created tower;
    CatalogFixtures.Created moon;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        tower = fx.series("tower").title("Tower of Ink").create();
        moon = fx.series("moon").title("Moon Garden").create();
        fx.published(tower.editionId(), "1", Instant.now().minus(2, ChronoUnit.DAYS));
        fx.published(moon.editionId(), "1", Instant.now().minus(2, ChronoUnit.DAYS));
        jdbc.sql("truncate notifications.preferences, notifications.pending_chapters")
                .update();
        jdbc.sql("truncate reading.library_entries, reading.reading_progress, reading.read_chapters")
                .update();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        emails.clear();
    }

    @Test
    void emailsFollowersOneDigestPerGapGroupedBySeries() {
        Cookie reader = signUp(READER, "a_reader", true);
        follow(reader, "tower");
        follow(reader, "moon");

        release(tower, "2");
        release(tower, "3");
        release(moon, "2");
        awaitPending(3);

        assertThat(digests.sendDue()).isEqualTo(1);
        EmailMessage email = only(READER);
        assertThat(email.subject()).isEqualTo("New chapters in 2 series you follow on CanReadIt");
        assertThat(email.text())
                .contains("Tower of Ink\n  Chapter 2: https://canreadit.test/series/tower/chapter/2\n"
                        + "  Chapter 3: https://canreadit.test/series/tower/chapter/3\n")
                .contains("Moon Garden\n  Chapter 2: https://canreadit.test/series/moon/chapter/2\n")
                .contains("https://canreadit.test/unsubscribe?token=" + token());
        assertThat(email.headers())
                .containsEntry(
                        "List-Unsubscribe",
                        "<https://canreadit.test/api/v1/notifications/unsubscribe?token=" + token() + ">")
                .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(pending()).isZero();

        // The next chapter waits for the gap, then goes out on its own.
        release(tower, "4");
        awaitPending(1);
        assertThat(digests.sendDue()).isZero();
        jdbc.sql("update notifications.preferences set last_digest_at = now() - interval '4 hours'")
                .update();
        assertThat(digests.sendDue()).isEqualTo(1);
        assertThat(digestsTo(READER)).hasSize(2);
        assertThat(digestsTo(READER).get(1).subject()).isEqualTo("Tower of Ink: chapter 4 is out");
    }

    @Test
    void neverEmailsUnconfirmedAddressesOrNonFollowers() {
        Cookie unconfirmed = signUp(READER, "a_reader", false);
        follow(unconfirmed, "tower");
        signUp("other@example.com", "other", true);

        release(tower, "2");
        awaitPending(1);
        assertThat(digests.sendDue()).isZero();
        assertThat(digestsTo(READER)).isEmpty();
        assertThat(digestsTo("other@example.com")).isEmpty();
        assertThat(pending()).isZero();
    }

    @Test
    void translationsAndUnfollowedSeriesSendNothing() {
        Cookie reader = signUp(READER, "a_reader", true);
        follow(reader, "tower");
        follow(reader, "moon");

        UUID translation = UUID.randomUUID();
        jdbc.sql(
                        "insert into catalog.editions (id, series_id, language, is_original) values (:id, :series, 'es', false)")
                .param("id", translation)
                .param("series", tower.id())
                .update();
        UUID spanish = fx.published(translation, "2", Instant.now().minusSeconds(60));
        publish(new ChapterPublished(tower.id(), "tower", spanish, "2", "es", Instant.now()));
        release(moon, "2");
        awaitQueueListeners();
        assertThat(pending()).isEqualTo(1);

        unfollow(reader, "moon");
        await(() -> pending() == 0);
        assertThat(digests.sendDue()).isZero();
        assertThat(digestsTo(READER)).isEmpty();
    }

    @Test
    void readersTurnEmailsOffInTheAccountOrWithTheLink() {
        Cookie reader = signUp(READER, "a_reader", true);
        follow(reader, "tower");

        assertThat(mvc.get().uri("/api/v1/me/notifications")).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me/notifications").cookie(reader))
                .bodyJson()
                .extractingPath("$.newChapterEmails")
                .isEqualTo(true);
        assertThat(write(reader, HttpMethod.PATCH, "/api/v1/me/notifications", Map.of("newChapterEmails", false)))
                .hasStatusOk();
        release(tower, "2");
        awaitQueueListeners();
        assertThat(pending()).isZero();
        assertThat(digests.sendDue()).isZero();

        assertThat(write(reader, HttpMethod.PATCH, "/api/v1/me/notifications", Map.of("newChapterEmails", true)))
                .hasStatusOk();
        release(tower, "3");
        awaitPending(1);
        assertThat(digests.sendDue()).isEqualTo(1);

        // The link in the email works without signing in, and without the CSRF token.
        assertThat(mvc.post().uri("/api/v1/notifications/unsubscribe?token=" + "x".repeat(32)))
                .hasStatus(404);
        assertThat(mvc.post()
                        .uri("/api/v1/notifications/unsubscribe?token=" + token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("List-Unsubscribe=One-Click"))
                .hasStatus(204);
        assertThat(mvc.get().uri("/api/v1/me/notifications").cookie(reader))
                .bodyJson()
                .extractingPath("$.newChapterEmails")
                .isEqualTo(false);
        assertThat(mvc.post().uri("/api/v1/notifications/unsubscribe?token=short"))
                .hasStatus(400);
    }

    @Test
    void forgetsDeletedAccounts() {
        Cookie reader = signUp(READER, "a_reader", true);
        follow(reader, "tower");
        release(tower, "2");
        awaitPending(1);
        UUID userId =
                jdbc.sql("select id from identity.users").query(UUID.class).single();

        publish(new UserDeleted(userId, Instant.now()));
        await(() -> pending() == 0 && preferences() == 0);
    }

    // --- Helpers

    /** Publishes a chapter now and announces it, as catalog's scheduler does. */
    private void release(CatalogFixtures.Created series, String number) {
        Instant at = Instant.now().minusSeconds(1);
        UUID chapterId = fx.published(series.editionId(), number, at);
        String slug = series.id().equals(tower.id()) ? "tower" : "moon";
        publish(new ChapterPublished(series.id(), slug, chapterId, number, "en", at));
    }

    private void publish(Object event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    private void awaitPending(int count) {
        await(() -> pending() == count);
    }

    /** Waits until this module's listeners handled every event published so far. */
    private void awaitQueueListeners() {
        await(() -> jdbc.sql("select count(*) from modulith.event_publication"
                                + " where listener_id like '%NewChapterQueue%' and completion_date is null")
                        .query(Long.class)
                        .single()
                == 0);
    }

    private static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private long pending() {
        return jdbc.sql("select count(*) from notifications.pending_chapters")
                .query(Long.class)
                .single();
    }

    private long preferences() {
        return jdbc.sql("select count(*) from notifications.preferences")
                .query(Long.class)
                .single();
    }

    private String token() {
        return jdbc.sql("select unsubscribe_token from notifications.preferences")
                .query(String.class)
                .single();
    }

    /** New-chapter emails only; signing up sends a confirmation email too. */
    private List<EmailMessage> digestsTo(String to) {
        return emails.sentTo(to).stream()
                .filter(m -> m.headers().containsKey("List-Unsubscribe"))
                .toList();
    }

    private EmailMessage only(String to) {
        assertThat(digestsTo(to)).hasSize(1);
        return digestsTo(to).getFirst();
    }

    private void follow(Cookie reader, String slug) {
        assertThat(write(reader, HttpMethod.PUT, "/api/v1/me/library/" + slug, null))
                .hasStatus(204);
    }

    private void unfollow(Cookie reader, String slug) {
        assertThat(write(reader, HttpMethod.DELETE, "/api/v1/me/library/" + slug, null))
                .hasStatus(204);
    }

    private MvcTestResult write(Cookie reader, HttpMethod method, String uri, Object body) {
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
            jdbc.sql("update identity.users set email_verified_at = :now where email = :email")
                    .param("now", OffsetDateTime.now(ZoneOffset.UTC))
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
