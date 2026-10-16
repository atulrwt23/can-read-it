package com.canreadit.analytics.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class AnalyticsRequests {

    static final int MAX_BATCH = 20;

    private AnalyticsRequests() {}

    /** Events a browser collected over a few seconds. */
    record EventBatch(
            @NotNull @Size(min = 1, max = MAX_BATCH) List<@NotNull @Valid ClientEvent> events) {}

    /**
     * One event. When it happened is the server's receive time: browser clocks can't be trusted.
     *
     * @param path a route template ({@code /series/[slug]}), never a URL with personal details
     * @param series the series' slug, for events about a series
     * @param chapter the chapter number
     * @param value a count, such as the number of search results
     */
    record ClientEvent(
            @NotNull EventName name,

            @Size(max = 100) @Pattern(regexp = "^/[A-Za-z0-9/\\[\\]_-]*$") @Nullable
            String path,

            @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") @Nullable
            String series,

            @Pattern(regexp = "^\\d{1,6}(\\.\\d{1,2})?$") @Nullable
            String chapter,

            @Min(0) @Max(1_000_000) @Nullable Integer value) {}
}
