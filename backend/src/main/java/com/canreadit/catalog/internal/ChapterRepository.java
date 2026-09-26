package com.canreadit.catalog.internal;

import static com.canreadit.catalog.internal.SeriesRepository.VISIBLE_CHAPTER;

import com.canreadit.catalog.ChapterSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Chapter queries. Readers only ever see visible chapters (ADR 0006). */
@Repository
class ChapterRepository {

    private final JdbcClient jdbc;

    ChapterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The edition in {@code language}, or the original edition when no language is given. */
    Optional<UUID> findEdition(UUID seriesId, @Nullable String language) {
        if (language != null) {
            return jdbc.sql("select id from catalog.editions where series_id = :s and language = :lang")
                    .param("s", seriesId)
                    .param("lang", language)
                    .query(UUID.class)
                    .optional();
        }
        return jdbc.sql("""
                        select id from catalog.editions where series_id = :s
                        order by is_original desc, language limit 1
                        """).param("s", seriesId).query(UUID.class).optional();
    }

    List<ChapterSummary> list(UUID editionId, ChapterOrder order, @Nullable BigDecimal after, int limit) {
        String direction = order == ChapterOrder.ASC ? "asc" : "desc";
        String keyset =
                after == null ? "" : order == ChapterOrder.ASC ? " and c.number > :after" : " and c.number < :after";
        var spec = jdbc.sql("""
                        select c.number, c.title, coalesce(c.published_at, c.publish_at) as published_at, c.access
                        from catalog.chapters c
                        where c.edition_id = :edition and %s%s
                        order by c.number %s
                        limit :limit
                        """.formatted(VISIBLE_CHAPTER, keyset, direction))
                .param("edition", editionId)
                .param("limit", limit);
        if (after != null) {
            spec = spec.param("after", after);
        }
        return spec.query((rs, n) -> SeriesRepository.chapterSummary(rs)).list();
    }

    Optional<ChapterRow> findVisible(UUID editionId, BigDecimal number) {
        return jdbc.sql("""
                        select c.id, c.number, c.title, coalesce(c.published_at, c.publish_at) as published_at, c.access
                        from catalog.chapters c
                        where c.edition_id = :edition and c.number = :number and %s
                        """.formatted(VISIBLE_CHAPTER))
                .param("edition", editionId)
                .param("number", number)
                .query((rs, n) -> new ChapterRow(rs.getObject("id", UUID.class), SeriesRepository.chapterSummary(rs)))
                .optional();
    }

    Neighbours neighbours(UUID editionId, BigDecimal number) {
        return jdbc.sql("""
                        select
                            (select max(c.number) from catalog.chapters c
                             where c.edition_id = :edition and c.number < :number and %1$s) as previous,
                            (select min(c.number) from catalog.chapters c
                             where c.edition_id = :edition and c.number > :number and %1$s) as next
                        """.formatted(VISIBLE_CHAPTER))
                .param("edition", editionId)
                .param("number", number)
                .query((rs, n) -> new Neighbours(
                        rs.getBigDecimal("previous") == null
                                ? null
                                : ChapterNumbers.format(rs.getBigDecimal("previous")),
                        rs.getBigDecimal("next") == null ? null : ChapterNumbers.format(rs.getBigDecimal("next"))))
                .single();
    }

    List<PageRow> pages(UUID chapterId) {
        return jdbc.sql("""
                        select asset_id, width, height from catalog.chapter_pages
                        where chapter_id = :c order by page_index
                        """)
                .param("c", chapterId)
                .query((rs, n) ->
                        new PageRow(rs.getObject("asset_id", UUID.class), rs.getInt("width"), rs.getInt("height")))
                .list();
    }

    Optional<ChapterContent.NovelBody> novelBody(UUID chapterId) {
        return jdbc.sql("select body_markdown, word_count from catalog.novel_chapter_bodies where chapter_id = :c")
                .param("c", chapterId)
                .query((rs, n) -> new ChapterContent.NovelBody(rs.getString("body_markdown"), rs.getInt("word_count")))
                .optional();
    }

    record ChapterRow(UUID id, ChapterSummary summary) {}

    record Neighbours(@Nullable String previous, @Nullable String next) {}

    record PageRow(UUID assetId, int width, int height) {}
}
