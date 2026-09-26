package com.canreadit.catalog;

import com.canreadit.media.ImageRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A series as shown in grids, carousels and rankings, with its newest visible chapters. */
public record SeriesCard(
        UUID id,
        String slug,
        String title,
        SeriesType type,
        SeriesStatus status,
        AgeRating ageRating,
        List<Genre> genres,
        @Nullable ImageRef cover,
        String synopsis,
        @Nullable Instant lastPublishedAt,
        List<ChapterSummary> latestChapters) {}
