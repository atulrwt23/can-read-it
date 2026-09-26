package com.canreadit.catalog.internal;

import com.canreadit.catalog.ChapterAccess;
import com.canreadit.catalog.ChapterSummary;
import com.canreadit.catalog.Genre;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-side queries over the catalog schema. Every chapter query applies the visibility rule. */
@Repository
class SeriesRepository {

    /** ADR 0006: a chapter is visible once it is not a draft and its publish time has passed. */
    static final String VISIBLE_CHAPTER = "c.status <> 'DRAFT' and c.publish_at <= now()";

    private final JdbcClient jdbc;

    SeriesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<SeriesRow> findBySlug(String slug) {
        return jdbc.sql("select " + SeriesRow.COLUMNS + " from catalog.series s where s.slug = :slug")
                .param("slug", slug)
                .query(SeriesRow::map)
                .optional();
    }

    /** The current slug for a slug the series used to have. */
    Optional<String> findCurrentSlug(String oldSlug) {
        return jdbc.sql("""
                        select s.slug from catalog.series_slug_history h
                        join catalog.series s on s.id = h.series_id
                        where h.old_slug = :slug
                        """).param("slug", oldSlug).query(String.class).optional();
    }

    List<SeriesRow> browse(BrowseQuery query) {
        var sql = new StringBuilder(
                "select " + SeriesRow.COLUMNS + " from catalog.series s where s.visibility = 'PUBLISHED'");
        var params = new HashMap<String, Object>();
        if (query.type() != null) {
            sql.append(" and s.type = :type");
            params.put("type", query.type().name());
        }
        if (query.status() != null) {
            sql.append(" and s.status = :status");
            params.put("status", query.status().name());
        }
        if (query.q() != null) {
            sql.append("""
                     and (catalog.search_text(s.title, s.alt_titles) ilike :pattern escape '\\'
                          or to_tsvector('simple', s.synopsis) @@ plainto_tsquery('simple', :q))
                    """);
            params.put("pattern", "%" + escapeLike(query.q()) + "%");
            params.put("q", query.q());
        }
        if (!query.genres().isEmpty()) {
            sql.append("""
                     and (select count(distinct g.slug) from catalog.series_genres sg
                          join catalog.genres g on g.id = sg.genre_id
                          where sg.series_id = s.id and g.slug in (:genres)) = :genreCount
                    """);
            params.put("genres", query.genres());
            params.put("genreCount", query.genres().size());
        }
        BrowseQuery.After after = query.after();
        switch (query.sort()) {
            case UPDATED -> {
                if (after != null) {
                    sql.append(" and (coalesce(s.last_published_at, s.created_at), s.id) < (:afterKey, :afterId)");
                    params.put("afterKey", OffsetDateTime.ofInstant(Instant.parse(after.key()), ZoneOffset.UTC));
                }
                sql.append(" order by coalesce(s.last_published_at, s.created_at) desc, s.id desc");
            }
            case NEWEST -> {
                if (after != null) {
                    sql.append(" and (s.created_at, s.id) < (:afterKey, :afterId)");
                    params.put("afterKey", OffsetDateTime.ofInstant(Instant.parse(after.key()), ZoneOffset.UTC));
                }
                sql.append(" order by s.created_at desc, s.id desc");
            }
            case TITLE -> {
                if (after != null) {
                    sql.append(" and (s.title, s.id) > (:afterKey, :afterId)");
                    params.put("afterKey", after.key());
                }
                sql.append(" order by s.title, s.id");
            }
        }
        if (after != null) {
            params.put("afterId", after.id());
        }
        sql.append(" limit :limit");
        params.put("limit", query.limit());
        return jdbc.sql(sql.toString()).params(params).query(SeriesRow::map).list();
    }

    List<SeriesRow> latestUpdates(int limit) {
        return jdbc.sql("select " + SeriesRow.COLUMNS + """
                                from catalog.series s
                                where s.visibility = 'PUBLISHED' and s.last_published_at is not null
                                order by s.last_published_at desc, s.id desc
                                limit :limit
                                """)
                .param("limit", limit)
                .query(SeriesRow::map)
                .list();
    }

    List<SeriesRow> publishedByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + SeriesRow.COLUMNS
                        + " from catalog.series s where s.visibility = 'PUBLISHED' and s.id in (:ids)")
                .param("ids", List.copyOf(ids))
                .query(SeriesRow::map)
                .list();
    }

    List<UUID> publishedIds() {
        return jdbc.sql("select id from catalog.series where visibility = 'PUBLISHED' order by id")
                .query(UUID.class)
                .list();
    }

    List<SeriesDetail.Edition> editions(UUID seriesId) {
        return jdbc.sql("""
                        select language, is_original, translator_credit from catalog.editions
                        where series_id = :id order by is_original desc, language
                        """)
                .param("id", seriesId)
                .query((rs, n) -> new SeriesDetail.Edition(
                        rs.getString("language"), rs.getBoolean("is_original"), rs.getString("translator_credit")))
                .list();
    }

    /** Count, first and latest visible chapter numbers of the original edition. */
    SeriesDetail.ChapterStats chapterStats(UUID seriesId) {
        return jdbc.sql("""
                        select count(*) as chapter_count, min(c.number) as first_number, max(c.number) as latest_number
                        from catalog.chapters c
                        join catalog.editions e on e.id = c.edition_id and e.is_original
                        where e.series_id = :id and %s
                        """.formatted(VISIBLE_CHAPTER))
                .param("id", seriesId)
                .query((rs, n) -> new SeriesDetail.ChapterStats(
                        rs.getInt("chapter_count"),
                        rs.getBigDecimal("first_number") == null
                                ? null
                                : ChapterNumbers.format(rs.getBigDecimal("first_number")),
                        rs.getBigDecimal("latest_number") == null
                                ? null
                                : ChapterNumbers.format(rs.getBigDecimal("latest_number"))))
                .single();
    }

    List<Genre> allGenres() {
        return jdbc.sql("select slug, name from catalog.genres order by name")
                .query((rs, n) -> new Genre(rs.getString("slug"), rs.getString("name")))
                .list();
    }

    Map<UUID, List<Genre>> genresBySeries(Collection<UUID> seriesIds) {
        Map<UUID, List<Genre>> result = new HashMap<>();
        if (seriesIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        select sg.series_id, g.slug, g.name from catalog.series_genres sg
                        join catalog.genres g on g.id = sg.genre_id
                        where sg.series_id in (:ids)
                        order by g.name
                        """)
                .param("ids", List.copyOf(seriesIds))
                .query((rs, n) -> result.computeIfAbsent(rs.getObject("series_id", UUID.class), k -> new ArrayList<>())
                        .add(new Genre(rs.getString("slug"), rs.getString("name"))))
                .list();
        return result;
    }

    /** The newest {@code perSeries} visible chapters of each series' original edition. */
    Map<UUID, List<ChapterSummary>> latestChapters(Collection<UUID> seriesIds, int perSeries) {
        Map<UUID, List<ChapterSummary>> result = new LinkedHashMap<>();
        if (seriesIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        select series_id, number, title, published_at, access from (
                            select e.series_id, c.number, c.title, coalesce(c.published_at, c.publish_at) as published_at,
                                   c.access, row_number() over (partition by e.series_id order by c.number desc) as rank
                            from catalog.chapters c
                            join catalog.editions e on e.id = c.edition_id and e.is_original
                            where e.series_id in (:ids) and %s
                        ) ranked
                        where rank <= :perSeries
                        order by series_id, number desc
                        """.formatted(VISIBLE_CHAPTER))
                .param("ids", List.copyOf(seriesIds))
                .param("perSeries", perSeries)
                .query((rs, n) -> result.computeIfAbsent(rs.getObject("series_id", UUID.class), k -> new ArrayList<>())
                        .add(chapterSummary(rs)))
                .list();
        return result;
    }

    static ChapterSummary chapterSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        Instant publishedAt = SeriesRow.instant(rs, "published_at");
        return new ChapterSummary(
                ChapterNumbers.format(rs.getBigDecimal("number")),
                rs.getString("title"),
                java.util.Objects.requireNonNull(publishedAt),
                ChapterAccess.valueOf(rs.getString("access")));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
