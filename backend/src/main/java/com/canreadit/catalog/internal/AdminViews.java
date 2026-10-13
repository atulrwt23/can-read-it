package com.canreadit.catalog.internal;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import com.canreadit.media.ImageRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Responses of the staff catalog endpoints. Unlike the public views, they include drafts and schedules. */
final class AdminViews {

    private AdminViews() {}

    record AdminSeriesList(List<AdminSeriesItem> items, long total) {}

    record AdminSeriesItem(
            UUID id,
            String slug,
            String title,
            SeriesType type,
            SeriesStatus status,
            SeriesRow.Visibility visibility,
            @Nullable ImageRef cover,
            int chapterCount,
            @Nullable Instant lastPublishedAt,
            Instant updatedAt) {}

    /** @param genres genre slugs */
    record AdminSeries(
            UUID id,
            String slug,
            String title,
            List<String> altTitles,
            String synopsis,
            SeriesType type,
            SeriesStatus status,
            SeriesRow.Visibility visibility,
            AgeRating ageRating,
            List<String> genres,
            @Nullable UUID coverAssetId,
            @Nullable ImageRef cover,
            @Nullable Integer firstReleasedYear,
            @Nullable String releaseCadence,
            Instant updatedAt,
            List<AdminChapterItem> chapters) {}

    /**
     * @param live whether readers can see it now (ADR 0006)
     * @param pageCount manhwa pages
     * @param wordCount novel words
     */
    record AdminChapterItem(
            UUID id,
            String number,
            @Nullable String title,
            ChapterEntity.Status status,
            @Nullable Instant publishAt,
            @Nullable Instant publishedAt,
            boolean live,
            int pageCount,
            int wordCount) {}

    /** @param body the novel text, for novels */
    record AdminChapter(
            UUID id,
            UUID seriesId,
            String seriesSlug,
            String seriesTitle,
            SeriesType seriesType,
            String number,
            @Nullable String title,
            ChapterEntity.Status status,
            @Nullable Instant publishAt,
            @Nullable Instant publishedAt,
            boolean live,
            List<AdminPage> pages,
            @Nullable String body) {}

    record AdminPage(UUID assetId, @Nullable String url, int width, int height) {}
}
