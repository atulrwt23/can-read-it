package com.canreadit.catalog.internal;

import com.canreadit.catalog.SeriesCard;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of browse results. {@code total} counts every match and is only computed for the first page (no cursor);
 * later pages leave it out.
 */
record SeriesPage(
        List<SeriesCard> items,
        @Nullable String nextCursor,
        @Nullable Long total) {}
