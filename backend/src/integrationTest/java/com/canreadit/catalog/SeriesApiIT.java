package com.canreadit.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeriesApiIT {

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
    }

    @Test
    void listsGenresAlphabetically() {
        fx.genre("romance", "Romance");
        fx.genre("action", "Action");

        assertThat(mvc.get().uri("/api/v1/genres"))
                .hasStatusOk()
                .hasHeader("Cache-Control", "max-age=0, public, s-maxage=3600, stale-while-revalidate=18000")
                .bodyJson()
                .extractingPath("$[*].slug")
                .asArray()
                .containsExactly("action", "romance");
    }

    @Test
    void browseShowsOnlyPublishedSeriesNewestUpdateFirst() {
        fx.series("older").lastPublishedAt(NOW.minus(2, ChronoUnit.DAYS)).create();
        fx.series("newer").lastPublishedAt(NOW.minus(1, ChronoUnit.HOURS)).create();
        fx.series("draft").visibility("DRAFT").create();
        fx.series("unlisted").visibility("UNLISTED").create();
        fx.series("removed").visibility("REMOVED").create();

        assertThat(mvc.get().uri("/api/v1/series"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("newer", "older");
    }

    @Test
    void browseFiltersByTypeStatusAndAllGenres() {
        var action = fx.genre("action", "Action");
        var fantasy = fx.genre("fantasy", "Fantasy");
        fx.series("both").genres(action, fantasy).create();
        fx.series("action-only").genres(action).create();
        fx.series("novel").type("NOVEL").genres(action, fantasy).create();
        fx.series("done").status("COMPLETED").genres(action, fantasy).create();

        assertThat(mvc.get().uri("/api/v1/series?type=MANHWA&status=ONGOING&genres=action,fantasy"))
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("both");
    }

    @Test
    void searchMatchesTitlesAltTitlesAndSynopsisWords() {
        fx.series("a").title("The Tower Climber").create();
        fx.series("b").title("Other").altTitles("탑 등반가", "Tower Ascent").create();
        fx.series("c")
                .title("Third")
                .synopsis("A quiet story about a lighthouse keeper.")
                .create();
        fx.series("d").title("Unrelated").create();

        assertThat(mvc.get().uri("/api/v1/series?q=tower&sort=TITLE"))
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("b", "a");
        assertThat(mvc.get().uri("/api/v1/series?q=lighthouse"))
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("c");
        assertThat(mvc.get().uri("/api/v1/series?q=50%25_off"))
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .isEmpty();
    }

    @Test
    void paginatesWithOpaqueCursors() throws Exception {
        for (String slug : new String[] {"s-e", "s-a", "s-d", "s-b", "s-c"}) {
            fx.series(slug).title(slug.toUpperCase()).create();
        }

        var first = mvc.get().uri("/api/v1/series?sort=TITLE&limit=2").exchange();
        assertThat(first).bodyJson().extractingPath("$.items[*].slug").asArray().containsExactly("s-a", "s-b");
        String cursor = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");

        var second = mvc.get()
                .uri("/api/v1/series?sort=TITLE&limit=2&cursor=" + cursor)
                .exchange();
        assertThat(second)
                .bodyJson()
                .extractingPath("$.items[*].slug")
                .asArray()
                .containsExactly("s-c", "s-d");
        String cursor2 = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.nextCursor");

        assertThat(mvc.get().uri("/api/v1/series?sort=TITLE&limit=2&cursor=" + cursor2))
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.items[*].slug")
                        .asArray()
                        .containsExactly("s-e"))
                .satisfies(
                        json -> assertThat(json).extractingPath("$.nextCursor").isNull());

        // A cursor only continues the query it came from.
        assertThat(mvc.get().uri("/api/v1/series?sort=NEWEST&cursor=" + cursor))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_cursor");
    }

    @Test
    void rejectsInvalidParametersAsProblems() {
        assertThat(mvc.get().uri("/api/v1/series?limit=500"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_failed");
        assertThat(mvc.get().uri("/api/v1/series?type=COMIC"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:canreadit:problem:bad_request");
        assertThat(mvc.get().uri("/api/v1/series?cursor=garbage"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_cursor");
    }

    @Test
    void cardsCarryCoverGenresAndOnlyVisibleLatestChapters() {
        var cover = fx.asset("COVER", 600, 900);
        var drama = fx.genre("drama", "Drama");
        var s = fx.series("cards")
                .cover(cover)
                .genres(drama)
                .lastPublishedAt(NOW)
                .create();
        for (int i = 1; i <= 4; i++) {
            fx.published(s.editionId(), String.valueOf(i), NOW.minus(10 - i, ChronoUnit.DAYS));
        }
        fx.published(s.editionId(), "4.5", NOW.minus(1, ChronoUnit.DAYS));
        fx.chapter(s.editionId(), "5", "SCHEDULED", NOW.plus(1, ChronoUnit.DAYS), "FREE");
        fx.chapter(s.editionId(), "6", "DRAFT", null, "FREE");

        assertThat(mvc.get().uri("/api/v1/series"))
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.items[0].latestChapters[*].number")
                        .asArray()
                        .containsExactly("4.5", "4", "3"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.items[0].cover.width")
                        .isEqualTo(600))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.items[0].genres[0].name")
                        .isEqualTo("Drama"));
    }

    @Test
    void detailAppliesSeriesVisibility() {
        var s = fx.series("tower").title("Tower").create();
        fx.published(s.editionId(), "1", NOW.minus(3, ChronoUnit.DAYS));
        fx.published(s.editionId(), "2", NOW.minus(2, ChronoUnit.DAYS));
        fx.chapter(s.editionId(), "3", "SCHEDULED", NOW.plus(1, ChronoUnit.HOURS), "FREE");
        fx.series("hidden").visibility("UNLISTED").create();
        fx.series("wip").visibility("DRAFT").create();
        fx.series("gone").visibility("REMOVED").create();

        assertThat(mvc.get().uri("/api/v1/series/tower"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json ->
                        assertThat(json).extractingPath("$.chapters.count").isEqualTo(2))
                .satisfies(json ->
                        assertThat(json).extractingPath("$.chapters.latest").isEqualTo("2"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.editions[0].language")
                        .isEqualTo("en"));
        assertThat(mvc.get().uri("/api/v1/series/hidden")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/series/wip")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/series/nope")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/series/gone"))
                .hasStatus(HttpStatus.GONE)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("series_removed");
        assertThat(mvc.get().uri("/api/v1/series/Not_A_Slug")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void oldSlugsRedirectPermanently() {
        var s = fx.series("new-name").create();
        fx.oldSlug("old-name", s.id());

        assertThat(mvc.get().uri("/api/v1/series/old-name"))
                .hasStatus(HttpStatus.MOVED_PERMANENTLY)
                .hasHeader("Location", "/api/v1/series/new-name");
    }
}
