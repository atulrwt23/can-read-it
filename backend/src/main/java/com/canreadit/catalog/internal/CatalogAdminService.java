package com.canreadit.catalog.internal;

import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import com.canreadit.media.AssetKind;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaAssets;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Ids;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff editing of the catalog: series, chapters, their pages or text, and release schedules. Releases follow the
 * visibility rule (ADR 0006): scheduling sets {@code publish_at}, and {@link ChapterPublisher} records the release.
 *
 * <p>TODO(product): creators will manage their own series through {@code series_members} (phase 3). TODO(product):
 * edits to series or chapters readers can already see reach cached pages only at the next time-based revalidation.
 */
@Service
class CatalogAdminService {

    /** Every series gets an original edition; the content language isn't chosen yet. */
    // TODO(product): pick the original language per series once launch languages are decided.
    static final String ORIGINAL_LANGUAGE = "en";

    private final SeriesEntities seriesEntities;
    private final ChapterEntities chapterEntities;
    private final NovelBodies novelBodies;
    private final JdbcClient jdbc;
    private final MediaAssets assets;
    private final MediaUrls mediaUrls;
    private final Clock clock;

    CatalogAdminService(
            SeriesEntities seriesEntities,
            ChapterEntities chapterEntities,
            NovelBodies novelBodies,
            JdbcClient jdbc,
            MediaAssets assets,
            MediaUrls mediaUrls,
            Clock clock) {
        this.seriesEntities = seriesEntities;
        this.chapterEntities = chapterEntities;
        this.novelBodies = novelBodies;
        this.jdbc = jdbc;
        this.assets = assets;
        this.mediaUrls = mediaUrls;
        this.clock = clock;
    }

    // --- Series

    /** Every series, drafts included, most recently edited first. {@code q} matches titles and slugs. */
    @Transactional(readOnly = true)
    AdminViews.AdminSeriesList list(@Nullable String q, int offset, int limit) {
        String pattern = q == null || q.isBlank() ? null : "%" + escapeLike(q.strip()) + "%";
        String where = """
                where cast(:pattern as text) is null
                   or catalog.search_text(s.title, s.alt_titles) ilike :pattern
                   or s.slug ilike :pattern
                """;
        long total = jdbc.sql("select count(*) from catalog.series s " + where)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        record Row(
                AdminViews.AdminSeriesItem item, @Nullable UUID coverId) {}
        List<Row> rows = jdbc.sql("""
                        select s.id, s.slug, s.title, s.type, s.status, s.visibility, s.cover_asset_id,
                               s.last_published_at, s.updated_at,
                               (select count(*) from catalog.chapters c
                                join catalog.editions e on e.id = c.edition_id
                                where e.series_id = s.id and e.is_original) as chapter_count
                        from catalog.series s
                        """ + where + """
                        order by s.updated_at desc, s.id
                        offset :offset limit :limit
                        """)
                .param("pattern", pattern)
                .param("offset", offset)
                .param("limit", limit)
                .query((rs, n) -> new Row(
                        new AdminViews.AdminSeriesItem(
                                rs.getObject("id", UUID.class),
                                rs.getString("slug"),
                                rs.getString("title"),
                                SeriesType.valueOf(rs.getString("type")),
                                SeriesStatus.valueOf(rs.getString("status")),
                                SeriesRow.Visibility.valueOf(rs.getString("visibility")),
                                null,
                                rs.getInt("chapter_count"),
                                SeriesRow.instant(rs, "last_published_at"),
                                Objects.requireNonNull(SeriesRow.instant(rs, "updated_at"))),
                        rs.getObject("cover_asset_id", UUID.class)))
                .list();
        Map<UUID, ImageRef> covers = mediaUrls.resolve(
                rows.stream().map(Row::coverId).filter(Objects::nonNull).toList(), ImageVariant.SMALL);
        List<AdminViews.AdminSeriesItem> items = rows.stream()
                .map(row -> {
                    var item = row.item();
                    return new AdminViews.AdminSeriesItem(
                            item.id(),
                            item.slug(),
                            item.title(),
                            item.type(),
                            item.status(),
                            item.visibility(),
                            row.coverId() == null ? null : covers.get(row.coverId()),
                            item.chapterCount(),
                            item.lastPublishedAt(),
                            item.updatedAt());
                })
                .toList();
        return new AdminViews.AdminSeriesList(items, total);
    }

    @Transactional(readOnly = true)
    AdminViews.AdminSeries series(UUID id) {
        return view(findSeries(id));
    }

    @Transactional
    AdminViews.AdminSeries create(AdminRequests.SeriesForm form) {
        Instant now = clock.instant();
        UUID id = Ids.newId();
        String slug = form.slug() != null ? claimSlug(form.slug(), id) : freeSlug(form.title(), id);
        SeriesEntity series = new SeriesEntity(id, slug, form.type(), now);
        series.edit(form, slug, genreIds(form.genres()), checkedCover(form.coverAssetId()), now);
        seriesEntities.saveAndFlush(series);
        jdbc.sql("""
                        insert into catalog.editions (id, series_id, language, is_original)
                        values (:id, :series, :language, true)
                        """)
                .param("id", Ids.newId())
                .param("series", id)
                .param("language", ORIGINAL_LANGUAGE)
                .update();
        return view(series);
    }

    /** Replaces the series' details. Leaving out the slug keeps it; a new slug keeps the old one as a redirect. */
    @Transactional
    AdminViews.AdminSeries update(UUID id, AdminRequests.SeriesForm form) {
        SeriesEntity series = findSeries(id);
        if (form.type() != series.type()) {
            throw ApiException.badRequest("series_type_fixed", "A series can't change between manhwa and novel.");
        }
        String oldSlug = series.slug();
        String slug = form.slug() == null || form.slug().equals(oldSlug) ? oldSlug : claimSlug(form.slug(), id);
        series.edit(form, slug, genreIds(form.genres()), checkedCover(form.coverAssetId()), clock.instant());
        seriesEntities.flush();
        if (!slug.equals(oldSlug)) {
            // Taking back one of its own old slugs: that one stops redirecting.
            jdbc.sql("delete from catalog.series_slug_history where old_slug = :slug")
                    .param("slug", slug)
                    .update();
            jdbc.sql("""
                            insert into catalog.series_slug_history (old_slug, series_id, changed_at)
                            values (:old, :series, now())
                            on conflict (old_slug) do update set series_id = excluded.series_id, changed_at = now()
                            """).param("old", oldSlug).param("series", id).update();
        }
        return view(series);
    }

    // --- Chapters

    @Transactional(readOnly = true)
    AdminViews.AdminChapter chapter(UUID id) {
        return view(chapterEntities.findById(id).orElseThrow(CatalogAdminService::chapterNotFound));
    }

    @Transactional
    AdminViews.AdminChapter createChapter(UUID seriesId, AdminRequests.ChapterForm form) {
        findSeries(seriesId);
        UUID editionId = jdbc.sql("select id from catalog.editions where series_id = :id and is_original")
                .param("id", seriesId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("Series without an original edition: " + seriesId));
        BigDecimal number = ChapterNumbers.parse(form.number());
        ensureNumberFree(editionId, number, null);
        ChapterEntity chapter = new ChapterEntity(Ids.newId(), editionId, number, title(form.title()), clock.instant());
        try {
            chapterEntities.saveAndFlush(chapter);
        } catch (DataIntegrityViolationException e) {
            throw numberTaken(number);
        }
        return view(chapter);
    }

    /** Renames a chapter. Its number can change only until readers can see it, because URLs carry the number. */
    @Transactional
    AdminViews.AdminChapter updateChapter(UUID id, AdminRequests.ChapterForm form) {
        ChapterEntity chapter = lockChapter(id);
        Instant now = clock.instant();
        BigDecimal number = ChapterNumbers.parse(form.number());
        if (number.compareTo(chapter.number()) != 0) {
            if (chapter.isLive(now)) {
                throw conflict("chapter_live", "Readers can already see this chapter, so its number stays.");
            }
            ensureNumberFree(chapter.editionId(), number, id);
        }
        chapter.rename(number, title(form.title()), now);
        try {
            chapterEntities.flush();
        } catch (DataIntegrityViolationException e) {
            throw numberTaken(number);
        }
        return view(chapter);
    }

    /** Sets a manhwa chapter's pages, in reading order, from uploaded page images. */
    @Transactional
    AdminViews.AdminChapter replacePages(UUID id, List<UUID> assetIds) {
        ChapterEntity chapter = lockChapter(id);
        if (seriesOf(chapter).type() != SeriesType.MANHWA) {
            throw ApiException.badRequest("not_manhwa", "Only manhwa chapters have pages; novels have text.");
        }
        Map<UUID, MediaAssets.AssetInfo> found = assets.find(new HashSet<>(assetIds));
        List<ChapterEntity.Page> pages = new ArrayList<>(assetIds.size());
        for (int i = 0; i < assetIds.size(); i++) {
            MediaAssets.AssetInfo asset = found.get(assetIds.get(i));
            if (asset == null || asset.kind() != AssetKind.PAGE || !asset.ready()) {
                throw ApiException.badRequest(
                        "invalid_page", "Page " + (i + 1) + " is not an uploaded chapter page. Upload it again.");
            }
            pages.add(new ChapterEntity.Page(asset.id(), asset.width(), asset.height()));
        }
        chapter.replacePages(pages, clock.instant());
        chapterEntities.flush();
        return view(chapter);
    }

    /** Sets a novel chapter's text. */
    @Transactional
    AdminViews.AdminChapter replaceBody(UUID id, String markdown) {
        ChapterEntity chapter = lockChapter(id);
        if (seriesOf(chapter).type() != SeriesType.NOVEL) {
            throw ApiException.badRequest("not_novel", "Only novel chapters have text; manhwa has pages.");
        }
        String text = markdown.strip();
        novelBodies
                .findById(id)
                .ifPresentOrElse(body -> body.replace(text), () -> novelBodies.save(new NovelBodyEntity(id, text)));
        chapter.touch(clock.instant());
        novelBodies.flush();
        return view(chapter);
    }

    /**
     * Schedules the chapter's release. A time that has passed (or none) releases it now. Readers see it from that
     * moment; the publish job then records the release and announces it.
     */
    @Transactional
    AdminViews.AdminChapter schedule(UUID id, @Nullable Instant publishAt) {
        ChapterEntity chapter = lockChapter(id);
        Instant now = clock.instant();
        if (chapter.status() == ChapterEntity.Status.PUBLISHED || chapter.isLive(now)) {
            throw conflict("chapter_live", "Readers can already see this chapter.");
        }
        if (!hasContent(chapter)) {
            throw conflict("chapter_empty", "Add the chapter's pages or text before scheduling it.");
        }
        chapter.schedule(publishAt == null || publishAt.isBefore(now) ? now : publishAt, now);
        chapterEntities.flush();
        return view(chapter);
    }

    /** Takes a scheduled chapter back to draft, as long as readers can't see it yet. */
    @Transactional
    AdminViews.AdminChapter unschedule(UUID id) {
        ChapterEntity chapter = lockChapter(id);
        if (chapter.status() == ChapterEntity.Status.PUBLISHED || chapter.isLive(clock.instant())) {
            throw conflict("chapter_live", "Readers can already see this chapter.");
        }
        chapter.unschedule(clock.instant());
        chapterEntities.flush();
        return view(chapter);
    }

    /** Deletes a chapter readers haven't seen. Released chapters stay. */
    // TODO(product): taking down a released chapter (moderation, rights) is a separate flow.
    @Transactional
    void deleteChapter(UUID id) {
        ChapterEntity chapter = lockChapter(id);
        if (chapter.status() == ChapterEntity.Status.PUBLISHED || chapter.isLive(clock.instant())) {
            throw conflict("chapter_live", "Readers can already see this chapter, so it can't be deleted.");
        }
        chapterEntities.delete(chapter);
    }

    // --- Views

    private AdminViews.AdminSeries view(SeriesEntity series) {
        List<String> genres = series.genreIds().isEmpty()
                ? List.of()
                : jdbc.sql("select slug from catalog.genres where id in (:ids) order by name")
                        .param("ids", List.copyOf(series.genreIds()))
                        .query(String.class)
                        .list();
        ImageRef cover = series.coverAssetId() == null
                ? null
                : mediaUrls
                        .resolve(List.of(series.coverAssetId()), ImageVariant.MEDIUM)
                        .get(series.coverAssetId());
        List<AdminViews.AdminChapterItem> chapters = jdbc.sql("""
                        select c.id, c.number, c.title, c.status, c.publish_at, c.published_at,
                               (c.status <> 'DRAFT' and c.publish_at <= now()) as live,
                               (select count(*) from catalog.chapter_pages p where p.chapter_id = c.id) as pages,
                               coalesce(b.word_count, 0) as words
                        from catalog.chapters c
                        join catalog.editions e on e.id = c.edition_id
                        left join catalog.novel_chapter_bodies b on b.chapter_id = c.id
                        where e.series_id = :id and e.is_original
                        order by c.number desc
                        """)
                .param("id", series.getId())
                .query((rs, n) -> new AdminViews.AdminChapterItem(
                        rs.getObject("id", UUID.class),
                        ChapterNumbers.format(rs.getBigDecimal("number")),
                        rs.getString("title"),
                        ChapterEntity.Status.valueOf(rs.getString("status")),
                        SeriesRow.instant(rs, "publish_at"),
                        SeriesRow.instant(rs, "published_at"),
                        rs.getBoolean("live"),
                        rs.getInt("pages"),
                        rs.getInt("words")))
                .list();
        Short year = series.firstReleasedYear();
        return new AdminViews.AdminSeries(
                series.getId(),
                series.slug(),
                series.title(),
                List.copyOf(series.altTitles()),
                series.synopsis(),
                series.type(),
                series.status(),
                series.visibility(),
                series.ageRating(),
                genres,
                series.coverAssetId(),
                cover,
                year == null ? null : year.intValue(),
                series.releaseCadence(),
                series.updatedAt(),
                chapters);
    }

    private AdminViews.AdminChapter view(ChapterEntity chapter) {
        SeriesRef series = seriesOf(chapter);
        Map<UUID, ImageRef> urls = mediaUrls.resolve(
                chapter.pages().stream()
                        .map(ChapterEntity.Page::assetId)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll),
                ImageVariant.ORIGINAL);
        List<AdminViews.AdminPage> pages = chapter.pages().stream()
                .map(page -> {
                    ImageRef ref = urls.get(page.assetId());
                    return new AdminViews.AdminPage(
                            page.assetId(), ref == null ? null : ref.url(), page.width(), page.height());
                })
                .toList();
        String body = series.type() == SeriesType.NOVEL
                ? novelBodies
                        .findById(chapter.getId())
                        .map(NovelBodyEntity::markdown)
                        .orElse("")
                : null;
        return new AdminViews.AdminChapter(
                chapter.getId(),
                series.id(),
                series.slug(),
                series.title(),
                series.type(),
                ChapterNumbers.format(chapter.number()),
                chapter.title(),
                chapter.status(),
                chapter.publishAt(),
                chapter.publishedAt(),
                chapter.isLive(clock.instant()),
                pages,
                body);
    }

    // --- Helpers

    private record SeriesRef(UUID id, String slug, String title, SeriesType type) {}

    private SeriesRef seriesOf(ChapterEntity chapter) {
        return jdbc.sql("""
                        select s.id, s.slug, s.title, s.type from catalog.editions e
                        join catalog.series s on s.id = e.series_id
                        where e.id = :edition
                        """)
                .param("edition", chapter.editionId())
                .query((rs, n) -> new SeriesRef(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("title"),
                        SeriesType.valueOf(rs.getString("type"))))
                .single();
    }

    private SeriesEntity findSeries(UUID id) {
        return seriesEntities
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this ID."));
    }

    /**
     * Locks the chapter row for the rest of the transaction. The publish job skips locked rows, so it can't release
     * a chapter while staff change its schedule.
     */
    private ChapterEntity lockChapter(UUID id) {
        return chapterEntities.lockById(id).orElseThrow(CatalogAdminService::chapterNotFound);
    }

    private boolean hasContent(ChapterEntity chapter) {
        return switch (seriesOf(chapter).type()) {
            case MANHWA -> !chapter.pages().isEmpty();
            case NOVEL ->
                novelBodies
                        .findById(chapter.getId())
                        .map(body -> body.wordCount() > 0)
                        .orElse(false);
        };
    }

    private void ensureNumberFree(UUID editionId, BigDecimal number, @Nullable UUID except) {
        boolean taken = jdbc.sql("""
                        select exists (
                            select 1 from catalog.chapters
                            where edition_id = :edition and number = :number
                              and (cast(:except as uuid) is null or id <> :except))
                        """)
                .param("edition", editionId)
                .param("number", number)
                .param("except", except)
                .query(Boolean.class)
                .single();
        if (taken) {
            throw numberTaken(number);
        }
    }

    /** The slug staff asked for, unless another series uses it now or redirects from it. */
    private String claimSlug(String slug, UUID seriesId) {
        if (slugTaken(slug, seriesId)) {
            throw conflict("slug_taken", "Another series already uses the URL name '" + slug + "'.");
        }
        return slug;
    }

    /** A free slug derived from the title: {@code title}, {@code title-2}, ... */
    private String freeSlug(String title, UUID seriesId) {
        String base = Slugs.fromTitle(title);
        if (base.isEmpty()) {
            base = "series";
        }
        base = base.substring(0, Math.min(base.length(), Slugs.MAX_LENGTH - 4)).replaceAll("-+$", "");
        for (int n = 1; n <= 50; n++) {
            String candidate = n == 1 ? base : base + "-" + n;
            if (!slugTaken(candidate, seriesId)) {
                return candidate;
            }
        }
        return base + "-" + seriesId.toString().substring(28);
    }

    private boolean slugTaken(String slug, UUID seriesId) {
        return jdbc.sql("""
                        select exists (select 1 from catalog.series where slug = :slug and id <> :id)
                            or exists (select 1 from catalog.series_slug_history
                                       where old_slug = :slug and series_id <> :id)
                        """)
                .param("slug", slug)
                .param("id", seriesId)
                .query(Boolean.class)
                .single();
    }

    private Set<UUID> genreIds(@Nullable List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = new LinkedHashSet<>(slugs);
        Map<String, UUID> found = new HashMap<>();
        jdbc.sql("select id, slug from catalog.genres where slug in (:slugs)")
                .param("slugs", List.copyOf(wanted))
                .query((rs, n) -> found.put(rs.getString("slug"), rs.getObject("id", UUID.class)))
                .list();
        for (String slug : wanted) {
            if (!found.containsKey(slug)) {
                throw ApiException.badRequest("unknown_genre", "There is no genre '" + slug + "'.");
            }
        }
        return new HashSet<>(found.values());
    }

    private @Nullable UUID checkedCover(@Nullable UUID assetId) {
        if (assetId == null) {
            return null;
        }
        MediaAssets.AssetInfo asset = assets.find(List.of(assetId)).get(assetId);
        if (asset == null || asset.kind() != AssetKind.COVER || !asset.ready()) {
            throw ApiException.badRequest("invalid_cover", "The cover is not an uploaded cover image.");
        }
        return assetId;
    }

    private static @Nullable String title(@Nullable String title) {
        return title == null || title.isBlank() ? null : title.strip();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ApiException chapterNotFound() {
        return ApiException.notFound("chapter_not_found", "No chapter has this ID.");
    }

    private static ApiException numberTaken(BigDecimal number) {
        return conflict(
                "chapter_number_taken", "The series already has a chapter " + ChapterNumbers.format(number) + ".");
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }
}
