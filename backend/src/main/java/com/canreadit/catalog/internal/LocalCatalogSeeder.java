package com.canreadit.catalog.internal;

import com.canreadit.media.AssetKind;
import com.canreadit.media.MediaUploads;
import com.canreadit.shared.Ids;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds invented placeholder series (CLAUDE.md section 9). Runs only in the {@code local} profile
 * (development) or the {@code demo} profile (the placeholder beta), and only into an empty catalog. All titles, text and images are made up.
 * To reseed: {@code docker compose -f infra/local/docker-compose.yml down -v}, then start again.
 */
@Component
@Profile({"local", "demo"})
@Order(10)
class LocalCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCatalogSeeder.class);

    private static final Map<String, String> GENRES = Map.of(
            "action", "Action",
            "fantasy", "Fantasy",
            "romance", "Romance",
            "comedy", "Comedy",
            "drama", "Drama",
            "mystery", "Mystery",
            "sci-fi", "Sci-Fi",
            "slice-of-life", "Slice of Life",
            "thriller", "Thriller",
            "martial-arts", "Martial Arts");

    private final JdbcClient jdbc;
    private final MediaUploads uploads;
    private final TransactionTemplate tx;

    LocalCatalogSeeder(JdbcClient jdbc, MediaUploads uploads, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.uploads = uploads;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.sql("select count(*) from catalog.series")
                .query(Integer.class)
                .single();
        if (existing > 0) {
            log.info("Catalog already has {} series; skipping local seed", existing);
            return;
        }
        long started = System.nanoTime();
        var random = new Random(42);
        Map<String, UUID> genreIds = new HashMap<>();
        GENRES.forEach((slug, name) -> genreIds.put(slug, insertGenre(slug, name)));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        List<SeedSeries> all = SeedSeries.all();
        for (SeedSeries series : all) {
            seed(series, genreIds, now, random);
        }
        log.info("Seeded {} placeholder series in {} s", all.size(), (System.nanoTime() - started) / 1_000_000_000);
    }

    private void seed(SeedSeries s, Map<String, UUID> genreIds, Instant now, Random random) {
        String typeLabel = s.novel() ? "Novel" : "Manhwa";
        UUID cover =
                uploads.storePublicImage(AssetKind.COVER, PlaceholderImages.cover(s.title(), typeLabel, s.color()));

        Instant lastRelease = now.minus(s.lastUpdateAgo());
        Instant firstRelease = lastRelease.minus(s.cadence().multipliedBy(s.chapters() - 1L));
        List<SeedChapter> chapters = chapters(s, firstRelease, now);

        // Images are uploaded before the transaction so it only holds database work.
        Map<String, List<UUID>> pageAssets = new HashMap<>();
        Map<String, List<int[]>> pageSizes = new HashMap<>();
        if (!s.novel()) {
            for (SeedChapter chapter : chapters) {
                int pageCount = 5 + random.nextInt(3);
                List<UUID> assets = new ArrayList<>();
                List<int[]> sizes = new ArrayList<>();
                for (int p = 1; p <= pageCount; p++) {
                    int height = 1000 + random.nextInt(6) * 100;
                    assets.add(uploads.storePublicImage(
                            AssetKind.PAGE, PlaceholderImages.page(s.title(), chapter.number(), p, height, s.color())));
                    sizes.add(new int[] {720, height});
                }
                pageAssets.put(chapter.number(), assets);
                pageSizes.put(chapter.number(), sizes);
            }
        }

        tx.executeWithoutResult(status -> {
            UUID seriesId = Ids.newId();
            Instant lastPublished = chapters.stream()
                    .filter(c -> !c.status().equals("DRAFT") && !c.publishAt().isAfter(now))
                    .map(SeedChapter::publishAt)
                    .max(Instant::compareTo)
                    .orElse(null);
            jdbc.sql("""
                            insert into catalog.series (id, slug, title, alt_titles, synopsis, type, status, visibility,
                                age_rating, cover_asset_id, first_released_year, release_cadence, last_published_at,
                                created_at, updated_at)
                            values (:id, :slug, :title, :alt, :synopsis, :type, :status, :visibility, :age, :cover,
                                :year, :cadence, :last, :created, :created)
                            """)
                    .param("id", seriesId)
                    .param("slug", s.slug())
                    .param("title", s.title())
                    .param("alt", s.altTitles().toArray(String[]::new))
                    .param("synopsis", s.synopsis())
                    .param("type", s.novel() ? "NOVEL" : "MANHWA")
                    .param("status", s.status())
                    .param("visibility", s.visibility())
                    .param("age", s.ageRating())
                    .param("cover", cover)
                    .param("year", s.year())
                    .param("cadence", s.cadenceLabel())
                    .param("last", lastPublished == null ? null : utc(lastPublished))
                    .param("created", utc(firstRelease.minus(1, ChronoUnit.DAYS)))
                    .update();
            for (String genre : s.genres()) {
                jdbc.sql("insert into catalog.series_genres (series_id, genre_id) values (:s, :g)")
                        .param("s", seriesId)
                        .param("g", genreIds.get(genre))
                        .update();
            }
            if (s.oldSlug() != null) {
                jdbc.sql("insert into catalog.series_slug_history (old_slug, series_id) values (:o, :s)")
                        .param("o", s.oldSlug())
                        .param("s", seriesId)
                        .update();
            }
            UUID editionId = Ids.newId();
            jdbc.sql("insert into catalog.editions (id, series_id, language, is_original) values (:id, :s, 'en', true)")
                    .param("id", editionId)
                    .param("s", seriesId)
                    .update();

            for (SeedChapter chapter : chapters) {
                UUID chapterId = insertChapter(editionId, chapter, now);
                if (s.novel()) {
                    String body = NovelText.chapter(s.title(), chapter.number(), random);
                    jdbc.sql("""
                                    insert into catalog.novel_chapter_bodies (chapter_id, body_markdown, word_count)
                                    values (:c, :b, :w)
                                    """)
                            .param("c", chapterId)
                            .param("b", body)
                            .param("w", NovelText.wordCount(body))
                            .update();
                } else {
                    List<UUID> assets = pageAssets.get(chapter.number());
                    List<int[]> sizes = pageSizes.get(chapter.number());
                    for (int i = 0; i < assets.size(); i++) {
                        jdbc.sql("""
                                        insert into catalog.chapter_pages (chapter_id, page_index, asset_id, width, height)
                                        values (:c, :i, :a, :w, :h)
                                        """)
                                .param("c", chapterId)
                                .param("i", i)
                                .param("a", assets.get(i))
                                .param("w", sizes.get(i)[0])
                                .param("h", sizes.get(i)[1])
                                .update();
                    }
                }
            }
        });
    }

    /** Regular releases, plus the extras a real catalogue has: a .5 chapter, a scheduled one, early access. */
    private static List<SeedChapter> chapters(SeedSeries s, Instant firstRelease, Instant now) {
        List<SeedChapter> chapters = new ArrayList<>();
        for (int n = 1; n <= s.chapters(); n++) {
            Instant at = firstRelease.plus(s.cadence().multipliedBy(n - 1L));
            boolean earlyAccess = s.earlyAccessLatest() && n == s.chapters();
            chapters.add(new SeedChapter(String.valueOf(n), "PUBLISHED", at, earlyAccess ? "EARLY_ACCESS" : "FREE"));
            if (s.extraHalfChapterAfter() == n) {
                chapters.add(new SeedChapter(n + ".5", "PUBLISHED", at.plus(Duration.ofHours(12)), "FREE"));
            }
        }
        if (s.scheduledNext()) {
            Instant next = firstRelease.plus(s.cadence().multipliedBy(s.chapters()));
            if (!next.isAfter(now)) {
                next = now.plus(Duration.ofDays(2));
            }
            chapters.add(new SeedChapter(String.valueOf(s.chapters() + 1), "SCHEDULED", next, "FREE"));
        }
        return chapters;
    }

    private UUID insertChapter(UUID editionId, SeedChapter chapter, Instant now) {
        UUID id = Ids.newId();
        boolean released = chapter.status().equals("PUBLISHED");
        jdbc.sql("""
                        insert into catalog.chapters (id, edition_id, number, title, status, publish_at, published_at,
                            access, price_coins)
                        values (:id, :edition, :number, :title, :status, :publishAt, :publishedAt, :access, :price)
                        """)
                .param("id", id)
                .param("edition", editionId)
                .param("number", new BigDecimal(chapter.number()))
                .param("title", NovelText.chapterTitle(chapter.number()))
                .param("status", chapter.status())
                .param("publishAt", utc(chapter.publishAt()))
                .param("publishedAt", released && !chapter.publishAt().isAfter(now) ? utc(chapter.publishAt()) : null)
                .param("access", chapter.access())
                .param("price", chapter.access().equals("EARLY_ACCESS") ? 3 : 0)
                .update();
        return id;
    }

    private UUID insertGenre(String slug, String name) {
        UUID id = Ids.newId();
        jdbc.sql("insert into catalog.genres (id, slug, name) values (:id, :slug, :name)")
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .update();
        return id;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record SeedChapter(String number, String status, Instant publishAt, String access) {}
}
