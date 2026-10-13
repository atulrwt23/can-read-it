package com.canreadit.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.RecordingEmailSender;
import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.catalog.ChapterPublished;
import com.canreadit.media.internal.TestImages;
import com.canreadit.shared.AuthCookies;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import tools.jackson.databind.json.JsonMapper;

/** Staff editing end to end: who may use it, then series, uploads, chapters and releases as readers see them. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, RecordingEmailSender.Config.class})
class CatalogAdminIT {

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
    S3Client s3;

    @Autowired
    ApplicationEvents events;

    CatalogFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        jdbc.sql("truncate identity.users, identity.credentials, identity.user_roles, identity.sessions,"
                        + " identity.refresh_tokens, identity.verification_codes, identity.auth_events cascade")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        try {
            s3.headBucket(b -> b.bucket(TestcontainersConfiguration.BUCKET));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(TestcontainersConfiguration.BUCKET));
        }
    }

    @Test
    void onlyStaffCanEditTheCatalog() {
        assertThat(mvc.get().uri("/api/v1/admin/series")).hasStatus(401);

        Cookie reader = signIn("reader@example.com", "a_reader", true);
        assertThat(mvc.get().uri("/api/v1/admin/series").cookie(reader))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");
        assertThat(upload(reader, "covers", TestImages.encode("png", 30, 40, 0x00D1B2)))
                .hasStatus(403);

        // The admin address only counts once it is confirmed.
        Cookie unconfirmed = signIn("admin@example.com", "the_admin", false);
        assertThat(mvc.get().uri("/api/v1/admin/series").cookie(unconfirmed)).hasStatus(403);

        Cookie admin = signIn("admin@example.com", "the_admin", true);
        assertThat(mvc.get().uri("/api/v1/admin/series").cookie(admin)).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/me").cookie(admin))
                .bodyJson()
                .extractingPath("$.roles")
                .asArray()
                .containsExactlyInAnyOrder("READER", "ADMIN");
        assertThat(jdbc.sql("select count(*) from identity.auth_events where type = 'ROLE_GRANTED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void releasesAManhwaChapter() {
        Cookie admin = admin();
        fx.genre("action", "Action");
        String cover = uploadedId(upload(admin, "covers", TestImages.encode("png", 900, 1200, 0x485FC7)));

        var created =
                write(admin, HttpMethod.POST, "/api/v1/admin/series", seriesForm("Tower of Ash", "MANHWA", cover));
        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.slug").isEqualTo("tower-of-ash");
        assertThat(created).bodyJson().extractingPath("$.visibility").isEqualTo("DRAFT");
        assertThat(created).bodyJson().extractingPath("$.genres").asArray().containsExactly("action");
        assertThat(created).bodyJson().extractingPath("$.cover.width").isEqualTo(600);
        String seriesId = read(created, "$.id");

        // A draft series stays hidden from readers.
        assertThat(mvc.get().uri("/api/v1/series/tower-of-ash")).hasStatus(404);

        var chapter = write(
                admin,
                HttpMethod.POST,
                "/api/v1/admin/series/" + seriesId + "/chapters",
                Map.of("number", "1", "title", "Embers"));
        assertThat(chapter).hasStatus(201);
        String chapterId = read(chapter, "$.id");
        String chapterUri = "/api/v1/admin/chapters/" + chapterId;

        // Nothing to read yet.
        assertThat(write(admin, HttpMethod.POST, chapterUri + "/schedule", Map.of()))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("chapter_empty");

        String page1 = uploadedId(upload(admin, "pages", TestImages.encode("png", 1600, 2400, 0x111111)));
        String page2 = uploadedId(upload(admin, "pages", TestImages.encode("jpg", 800, 1000, 0x222222)));
        assertThat(write(admin, HttpMethod.PUT, chapterUri + "/pages", Map.of("assetIds", List.of(cover))))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_page");
        var paged = write(admin, HttpMethod.PUT, chapterUri + "/pages", Map.of("assetIds", List.of(page1, page2)));
        assertThat(paged).hasStatusOk();
        assertThat(paged).bodyJson().extractingPath("$.pages[0].width").isEqualTo(1080);
        assertThat(paged).bodyJson().extractingPath("$.pages[0].height").isEqualTo(1620);
        assertThat(paged).bodyJson().extractingPath("$.pages[1].width").isEqualTo(800);

        var series = seriesForm("Tower of Ash", "MANHWA", cover);
        series.put("visibility", "PUBLISHED");
        assertThat(write(admin, HttpMethod.PUT, "/api/v1/admin/series/" + seriesId, series))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/series/tower-of-ash")).hasStatusOk();

        // Scheduled for tomorrow: not readable yet, and it can still go back to draft.
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var scheduled =
                write(admin, HttpMethod.POST, chapterUri + "/schedule", Map.of("publishAt", tomorrow.toString()));
        assertThat(scheduled).hasStatusOk();
        assertThat(scheduled).bodyJson().extractingPath("$.status").isEqualTo("SCHEDULED");
        assertThat(scheduled).bodyJson().extractingPath("$.live").isEqualTo(false);
        assertThat(mvc.get().uri("/api/v1/series/tower-of-ash/chapters/1")).hasStatus(404);
        assertThat(write(admin, HttpMethod.POST, chapterUri + "/unschedule", null))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("DRAFT");

        // Released now: readable at once, recorded and announced without waiting for the job.
        var released = write(admin, HttpMethod.POST, chapterUri + "/schedule", Map.of());
        assertThat(released).hasStatusOk();
        assertThat(released).bodyJson().extractingPath("$.status").isEqualTo("PUBLISHED");
        assertThat(released).bodyJson().extractingPath("$.live").isEqualTo(true);
        var reading = mvc.get().uri("/api/v1/series/tower-of-ash/chapters/1").exchange();
        assertThat(reading).hasStatusOk();
        assertThat(reading).bodyJson().extractingPath("$.pages").asArray().hasSize(2);
        assertThat(events.stream(ChapterPublished.class))
                .singleElement()
                .satisfies(e -> assertThat(e.seriesSlug()).isEqualTo("tower-of-ash"));

        // Released chapters stay put.
        assertThat(write(admin, HttpMethod.DELETE, chapterUri, null))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("chapter_live");
        assertThat(write(admin, HttpMethod.PUT, chapterUri, Map.of("number", "2")))
                .hasStatus(409);
        assertThat(write(admin, HttpMethod.PUT, chapterUri, Map.of("number", "1", "title", "Cinders")))
                .hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/admin/series").cookie(admin))
                .bodyJson()
                .extractingPath("$.items[0].chapterCount")
                .isEqualTo(1);
    }

    @Test
    void editsANovelAndKeepsOldLinksWorking() {
        Cookie admin = admin();
        Map<String, Object> form = seriesForm("Quiet Library", "NOVEL", null);
        form.put("visibility", "PUBLISHED");
        String seriesId = read(write(admin, HttpMethod.POST, "/api/v1/admin/series", form), "$.id");

        // A second series with the same title gets the next free slug.
        assertThat(write(admin, HttpMethod.POST, "/api/v1/admin/series", seriesForm("Quiet Library", "NOVEL", null)))
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo("quiet-library-2");
        assertThat(write(admin, HttpMethod.POST, "/api/v1/admin/series", seriesFormWithSlug("quiet-library")))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("slug_taken");

        String chapterId = read(
                write(admin, HttpMethod.POST, "/api/v1/admin/series/" + seriesId + "/chapters", Map.of("number", "1")),
                "$.id");
        assertThat(write(
                        admin,
                        HttpMethod.POST,
                        "/api/v1/admin/series/" + seriesId + "/chapters",
                        Map.of("number", "1.0")))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("chapter_number_taken");
        String chapterUri = "/api/v1/admin/chapters/" + chapterId;
        assertThat(write(admin, HttpMethod.PUT, chapterUri + "/pages", Map.of("assetIds", List.of())))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("not_manhwa");

        var body = write(
                admin, HttpMethod.PUT, chapterUri + "/body", Map.of("markdown", "The lamps hum.\n\nShe reads on."));
        assertThat(body).hasStatusOk();
        assertThat(body).bodyJson().extractingPath("$.body").isEqualTo("The lamps hum.\n\nShe reads on.");
        assertThat(write(admin, HttpMethod.POST, chapterUri + "/schedule", Map.of()))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/series/quiet-library/chapters/1"))
                .bodyJson()
                .extractingPath("$.novel.markdown")
                .asString()
                .contains("The lamps hum.");

        Map<String, Object> renamed = new HashMap<>(form);
        renamed.put("slug", "the-quiet-library");
        assertThat(write(admin, HttpMethod.PUT, "/api/v1/admin/series/" + seriesId, renamed))
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo("the-quiet-library");
        assertThat(mvc.get().uri("/api/v1/series/quiet-library/chapters/1"))
                .hasStatus(301)
                .headers()
                .hasValue("Location", "/api/v1/series/the-quiet-library/chapters/1");

        Map<String, Object> retyped = new HashMap<>(renamed);
        retyped.put("type", "MANHWA");
        assertThat(write(admin, HttpMethod.PUT, "/api/v1/admin/series/" + seriesId, retyped))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("series_type_fixed");
    }

    @Test
    void draftsCanBeDeleted() {
        Cookie admin = admin();
        String seriesId =
                read(write(admin, HttpMethod.POST, "/api/v1/admin/series", seriesForm("Tide", "NOVEL", null)), "$.id");
        String chapterId = read(
                write(
                        admin,
                        HttpMethod.POST,
                        "/api/v1/admin/series/" + seriesId + "/chapters",
                        Map.of("number", "3.5")),
                "$.id");

        assertThat(write(admin, HttpMethod.DELETE, "/api/v1/admin/chapters/" + chapterId, null))
                .hasStatus(204);
        assertThat(mvc.get().uri("/api/v1/admin/chapters/" + chapterId).cookie(admin))
                .hasStatus(404);
    }

    // --- Helpers

    private Cookie admin() {
        return signIn("admin@example.com", "the_admin", true);
    }

    /** Registers (once), optionally confirms the email, and signs in with the password. */
    private Cookie signIn(String email, String handle, boolean confirmed) {
        if (jdbc.sql("select count(*) from identity.users where email = :email")
                        .param("email", email)
                        .query(Long.class)
                        .single()
                == 0) {
            assertThat(post(
                            "/api/v1/auth/password/register",
                            Map.of("email", email, "password", PASSWORD, "handle", handle)))
                    .hasStatus(202);
        }
        if (confirmed) {
            jdbc.sql("update identity.users set email_verified_at = now() where email = :email")
                    .param("email", email)
                    .update();
        }
        var signedIn = post("/api/v1/auth/password/login", Map.of("email", email, "password", PASSWORD));
        assertThat(signedIn).hasStatusOk();
        return signedIn.getResponse().getCookie(AuthCookies.ACCESS);
    }

    private Map<String, Object> seriesForm(String title, String type, @Nullable String cover) {
        Map<String, Object> form = new HashMap<>();
        form.put("title", title);
        form.put("type", type);
        form.put("status", "ONGOING");
        form.put("visibility", "DRAFT");
        form.put("ageRating", "TEEN");
        form.put("synopsis", "An invented placeholder story.");
        form.put("genres", type.equals("MANHWA") ? List.of("action") : List.of());
        if (cover != null) {
            form.put("coverAssetId", cover);
        }
        return form;
    }

    private Map<String, Object> seriesFormWithSlug(String slug) {
        Map<String, Object> form = seriesForm("Another", "NOVEL", null);
        form.put("slug", slug);
        return form;
    }

    private MvcTestResult upload(Cookie access, String kind, byte[] content) {
        Cookie csrf = csrfCookie();
        return mvc.post()
                .uri("/api/v1/admin/media/" + kind)
                .multipart()
                .file(new MockMultipartFile("file", "image", "application/octet-stream", content))
                .cookie(access, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    private String uploadedId(MvcTestResult result) {
        assertThat(result).hasStatus(201);
        return read(result, "$.id");
    }

    private MvcTestResult write(Cookie access, HttpMethod method, String uri, @Nullable Object body) {
        Cookie csrf = csrfCookie();
        var request = mvc.method(method).uri(uri).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return request.exchange();
    }

    private MvcTestResult post(String uri, Map<String, String> body) {
        return mvc.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    private Cookie csrfCookie() {
        return mvc.get().uri("/api/v1/me").exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
