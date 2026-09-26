package com.canreadit.catalog.internal;

import com.canreadit.catalog.ChapterAccess;
import com.canreadit.catalog.SeriesType;
import com.canreadit.media.ImageRef;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the reader renders. Manhwa chapters carry {@code pages} (with intrinsic sizes, so the
 * layout never shifts); novel chapters carry {@code novel}. Locked chapters carry neither.
 */
record ChapterContent(
        SeriesRef series,
        String number,
        @Nullable String title,
        Instant publishedAt,
        ChapterAccess access,
        boolean locked,
        List<ImageRef> pages,
        @Nullable NovelBody novel,
        @Nullable String previous,
        @Nullable String next) {

    record SeriesRef(String slug, String title, SeriesType type) {}

    record NovelBody(String markdown, int wordCount) {}
}
