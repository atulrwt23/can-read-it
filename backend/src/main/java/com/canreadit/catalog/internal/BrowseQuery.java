package com.canreadit.catalog.internal;

import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * @param limit rows to fetch (page size + 1, to detect a next page)
 * @param after keyset position to continue from
 */
record BrowseQuery(
        BrowseSort sort,
        @Nullable SeriesType type,
        @Nullable SeriesStatus status,
        @Nullable String q,
        List<String> genres,
        int limit,
        @Nullable After after) {

    /** @param key the sort key of the last row (ISO instant or title) */
    record After(String key, UUID id) {}
}
