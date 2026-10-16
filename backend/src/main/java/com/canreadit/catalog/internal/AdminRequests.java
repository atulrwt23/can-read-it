package com.canreadit.catalog.internal;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request bodies of the staff catalog endpoints ({@code /api/v1/admin/...}). */
final class AdminRequests {

    /** Plain decimals with at most two fraction digits, as {@link ChapterNumbers} accepts them. */
    static final String CHAPTER_NUMBER = "^\\d{1,6}(\\.\\d{1,2})?$";

    /** Novel chapters: about 30,000 words of Markdown at most. */
    static final int MAX_BODY = 200_000;

    static final int MAX_PAGES = 300;

    private AdminRequests() {}

    /**
     * A whole series. The type is fixed once the series exists.
     *
     * @param slug the URL name; derived from the title when left out
     * @param genres genre slugs
     */
    record SeriesForm(
            @NotBlank @Size(max = 300) String title,

            @Size(max = 80) @Pattern(regexp = CatalogController.SLUG) @Nullable
            String slug,

            @NotNull SeriesType type,
            @NotNull SeriesStatus status,
            @NotNull SeriesRow.Visibility visibility,
            @NotNull AgeRating ageRating,
            @Size(max = 5000) @Nullable String synopsis,
            @Size(max = 20) @Nullable List<@NotBlank @Size(max = 300) String> altTitles,
            @Size(max = 10) @Nullable List<@Pattern(regexp = CatalogController.SLUG) String> genres,
            @Nullable UUID coverAssetId,
            @Min(1900) @Max(2200) @Nullable Integer firstReleasedYear,
            @Size(max = 100) @Nullable String releaseCadence) {}

    record ChapterForm(
            @NotNull @Pattern(regexp = CHAPTER_NUMBER) String number,
            @Size(max = 300) @Nullable String title) {}

    /** Manhwa pages in reading order: uploaded page assets. */
    record ChapterPages(@NotNull @Size(max = MAX_PAGES) List<@NotNull UUID> assetIds) {}

    /** A novel chapter's text, in Markdown. */
    record ChapterBody(@NotNull @Size(max = MAX_BODY) String markdown) {}

    /** @param publishAt when readers can see the chapter; left out (or in the past) means now */
    record ChapterSchedule(@Nullable Instant publishAt) {}
}
