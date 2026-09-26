package com.canreadit.catalog;

import com.canreadit.shared.Ids;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Inserts catalog rows directly, so API tests control every column. */
public final class CatalogFixtures {

    private final JdbcClient jdbc;

    public CatalogFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void reset() {
        jdbc.sql("truncate catalog.series, catalog.genres, media.assets cascade")
                .update();
    }

    public UUID genre(String slug, String name) {
        UUID id = Ids.newId();
        jdbc.sql("insert into catalog.genres (id, slug, name) values (:id, :slug, :name)")
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .update();
        return id;
    }

    public SeriesBuilder series(String slug) {
        return new SeriesBuilder(slug);
    }

    public UUID asset(String kind, int width, int height) {
        UUID id = Ids.newId();
        String sha = "%064x".formatted(id.getLeastSignificantBits() & Long.MAX_VALUE);
        jdbc.sql("""
                        insert into media.assets (id, kind, storage_key, sha256, content_type, width, height, bytes, status)
                        values (:id, :kind, :key, :sha, 'image/png', :w, :h, 100, 'READY')
                        """)
                .param("id", id)
                .param("kind", kind)
                .param("key", kind.toLowerCase() + "s/" + sha + ".png")
                .param("sha", sha)
                .param("w", width)
                .param("h", height)
                .update();
        return id;
    }

    public UUID chapter(UUID editionId, String number, String status, @Nullable Instant publishAt, String access) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into catalog.chapters (id, edition_id, number, title, status, publish_at, access)
                        values (:id, :edition, :number, :title, :status, :publishAt, :access)
                        """)
                .param("id", id)
                .param("edition", editionId)
                .param("number", new BigDecimal(number))
                .param("title", "Chapter " + number)
                .param("status", status)
                .param("publishAt", publishAt == null ? null : OffsetDateTime.ofInstant(publishAt, ZoneOffset.UTC))
                .param("access", access)
                .update();
        return id;
    }

    public UUID published(UUID editionId, String number, Instant at) {
        return chapter(editionId, number, "PUBLISHED", at, "FREE");
    }

    public void page(UUID chapterId, int index, UUID assetId, int width, int height) {
        jdbc.sql("""
                        insert into catalog.chapter_pages (chapter_id, page_index, asset_id, width, height)
                        values (:c, :i, :a, :w, :h)
                        """)
                .param("c", chapterId)
                .param("i", index)
                .param("a", assetId)
                .param("w", width)
                .param("h", height)
                .update();
    }

    public void novelBody(UUID chapterId, String markdown) {
        jdbc.sql("insert into catalog.novel_chapter_bodies (chapter_id, body_markdown, word_count) values (:c, :b, :w)")
                .param("c", chapterId)
                .param("b", markdown)
                .param("w", markdown.split("\\s+").length)
                .update();
    }

    public void oldSlug(String oldSlug, UUID seriesId) {
        jdbc.sql("insert into catalog.series_slug_history (old_slug, series_id) values (:o, :s)")
                .param("o", oldSlug)
                .param("s", seriesId)
                .update();
    }

    public final class SeriesBuilder {
        private final String slug;
        private String title;
        private List<String> altTitles = List.of();
        private String synopsis = "";
        private String type = "MANHWA";
        private String status = "ONGOING";
        private String visibility = "PUBLISHED";
        private @Nullable UUID cover;
        private @Nullable Instant lastPublishedAt;
        private Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        private List<UUID> genres = List.of();

        SeriesBuilder(String slug) {
            this.slug = slug;
            this.title = slug;
        }

        public SeriesBuilder title(String value) {
            title = value;
            return this;
        }

        public SeriesBuilder altTitles(String... values) {
            altTitles = List.of(values);
            return this;
        }

        public SeriesBuilder synopsis(String value) {
            synopsis = value;
            return this;
        }

        public SeriesBuilder type(String value) {
            type = value;
            return this;
        }

        public SeriesBuilder status(String value) {
            status = value;
            return this;
        }

        public SeriesBuilder visibility(String value) {
            visibility = value;
            return this;
        }

        public SeriesBuilder cover(UUID value) {
            cover = value;
            return this;
        }

        public SeriesBuilder lastPublishedAt(Instant value) {
            lastPublishedAt = value;
            return this;
        }

        public SeriesBuilder createdAt(Instant value) {
            createdAt = value;
            return this;
        }

        public SeriesBuilder genres(UUID... values) {
            genres = List.of(values);
            return this;
        }

        /** Inserts the series with an original English edition and returns (seriesId, editionId). */
        public Created create() {
            UUID id = Ids.newId();
            jdbc.sql("""
                            insert into catalog.series (id, slug, title, alt_titles, synopsis, type, status, visibility,
                                cover_asset_id, last_published_at, created_at)
                            values (:id, :slug, :title, :alt, :synopsis, :type, :status, :visibility, :cover, :last, :created)
                            """)
                    .param("id", id)
                    .param("slug", slug)
                    .param("title", title)
                    .param("alt", altTitles.toArray(String[]::new))
                    .param("synopsis", synopsis)
                    .param("type", type)
                    .param("status", status)
                    .param("visibility", visibility)
                    .param("cover", cover)
                    .param(
                            "last",
                            lastPublishedAt == null ? null : OffsetDateTime.ofInstant(lastPublishedAt, ZoneOffset.UTC))
                    .param("created", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                    .update();
            for (UUID genre : genres) {
                jdbc.sql("insert into catalog.series_genres (series_id, genre_id) values (:s, :g)")
                        .param("s", id)
                        .param("g", genre)
                        .update();
            }
            UUID edition = Ids.newId();
            jdbc.sql("insert into catalog.editions (id, series_id, language, is_original) values (:id, :s, 'en', true)")
                    .param("id", edition)
                    .param("s", id)
                    .update();
            return new Created(id, edition);
        }
    }

    public record Created(UUID id, UUID editionId) {}
}
