package com.canreadit.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
class ChapterApiIT {

    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    CatalogFixtures fx;
    CatalogFixtures.Created manhwa;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
        manhwa = fx.series("tower").create();
        fx.published(manhwa.editionId(), "1", NOW.minus(5, ChronoUnit.DAYS));
        fx.published(manhwa.editionId(), "2", NOW.minus(4, ChronoUnit.DAYS));
        fx.published(manhwa.editionId(), "2.5", NOW.minus(3, ChronoUnit.DAYS));
        fx.published(manhwa.editionId(), "3", NOW.minus(2, ChronoUnit.DAYS));
        fx.chapter(manhwa.editionId(), "4", "SCHEDULED", NOW.plus(2, ChronoUnit.DAYS), "FREE");
        fx.chapter(manhwa.editionId(), "5", "DRAFT", null, "FREE");
    }

    @Test
    void listsOnlyVisibleChaptersNewestFirst() {
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items[*].number")
                .asArray()
                .containsExactly("3", "2.5", "2", "1");
    }

    @Test
    void paginatesInEitherOrder() throws Exception {
        var first =
                mvc.get().uri("/api/v1/series/tower/chapters?order=ASC&limit=3").exchange();
        assertThat(first)
                .bodyJson()
                .extractingPath("$.items[*].number")
                .asArray()
                .containsExactly("1", "2", "2.5");
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");

        assertThat(mvc.get().uri("/api/v1/series/tower/chapters?order=ASC&limit=3&cursor=" + cursor))
                .bodyJson()
                .extractingPath("$.items[*].number")
                .asArray()
                .containsExactly("3");
    }

    @Test
    void readsMangaPagesWithSizesAndNeighbours() {
        var chapter = fx.published(manhwa.editionId(), "10", NOW.minus(1, ChronoUnit.HOURS));
        var p1 = fx.asset("PAGE", 800, 1200);
        var p2 = fx.asset("PAGE", 800, 2400);
        fx.page(chapter, 1, p2, 800, 2400);
        fx.page(chapter, 0, p1, 800, 1200);

        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/2.5"))
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.previous").isEqualTo("2"))
                .satisfies(json -> assertThat(json).extractingPath("$.next").isEqualTo("3"));
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/10.00"))
                .hasStatusOk()
                .hasHeader("Cache-Control", "max-age=0, public, s-maxage=300, stale-while-revalidate=1500")
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.number").isEqualTo("10"))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.pages[*].height")
                        .asArray()
                        .containsExactly(1200, 2400))
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.pages[0].url")
                        .asString()
                        .endsWith(".png"))
                .satisfies(json -> assertThat(json).extractingPath("$.previous").isEqualTo("3"))
                .satisfies(json -> assertThat(json).extractingPath("$.next").isNull())
                .satisfies(json -> assertThat(json).extractingPath("$.novel").isNull());
    }

    @Test
    void readsNovelBodies() {
        var novel = fx.series("story").type("NOVEL").create();
        var chapter = fx.published(novel.editionId(), "1", NOW.minus(1, ChronoUnit.DAYS));
        fx.novelBody(chapter, "It was a *quiet* night.\n\nThe end.");

        assertThat(mvc.get().uri("/api/v1/series/story/chapters/1"))
                .bodyJson()
                .satisfies(json -> assertThat(json)
                        .extractingPath("$.novel.markdown")
                        .asString()
                        .startsWith("It was"))
                .satisfies(json ->
                        assertThat(json).extractingPath("$.pages").asArray().isEmpty())
                .satisfies(
                        json -> assertThat(json).extractingPath("$.series.type").isEqualTo("NOVEL"));
    }

    @Test
    void hidesUnreleasedChapters() {
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/4"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("chapter_not_found");
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/5")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void scheduledChaptersGoLiveWithoutWaitingForTheJob() {
        // Status is still SCHEDULED, but its time has passed: visible (ADR 0006).
        fx.chapter(manhwa.editionId(), "3.5", "SCHEDULED", NOW.minus(1, ChronoUnit.MINUTES), "FREE");

        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/3.5")).hasStatusOk();
    }

    @Test
    void locksEarlyAccessChapters() {
        var chapter =
                fx.chapter(manhwa.editionId(), "3.9", "PUBLISHED", NOW.minus(1, ChronoUnit.MINUTES), "EARLY_ACCESS");
        fx.page(chapter, 0, fx.asset("PAGE", 800, 1200), 800, 1200);

        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/3.9"))
                .bodyJson()
                .satisfies(json -> assertThat(json).extractingPath("$.locked").isEqualTo(true))
                .satisfies(json ->
                        assertThat(json).extractingPath("$.pages").asArray().isEmpty());
    }

    @Test
    void validatesNumbersLanguagesAndSlugs() {
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters/abc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_chapter_number");
        assertThat(mvc.get().uri("/api/v1/series/tower/chapters?lang=fr"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("edition_not_found");
        fx.oldSlug("the-tower", manhwa.id());
        assertThat(mvc.get().uri("/api/v1/series/the-tower/chapters/2?lang=en"))
                .hasStatus(HttpStatus.MOVED_PERMANENTLY)
                .hasHeader("Location", "/api/v1/series/tower/chapters/2?lang=en");
    }
}
