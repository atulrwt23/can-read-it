package com.canreadit.catalog.internal;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.Genre;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import com.canreadit.media.ImageRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record SeriesDetail(
        UUID id,
        String slug,
        String title,
        List<String> altTitles,
        String synopsis,
        SeriesType type,
        SeriesStatus status,
        AgeRating ageRating,
        List<Genre> genres,
        @Nullable ImageRef cover,
        @Nullable Integer firstReleasedYear,
        @Nullable String releaseCadence,
        @Nullable Instant lastPublishedAt,
        List<Edition> editions,
        ChapterStats chapters) {

    /** @param language BCP-47 tag */
    record Edition(
            String language, boolean original, @Nullable String translatorCredit) {}

    /** Visible chapters of the original edition. */
    record ChapterStats(
            int count, @Nullable String first, @Nullable String latest) {}
}
