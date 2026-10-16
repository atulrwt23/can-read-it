package com.canreadit.analytics.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Responses of the staff analytics endpoint. */
final class AnalyticsViews {

    private AnalyticsViews() {}

    /**
     * @param days one entry per day, oldest first; today counts live
     * @param topSeries the most opened series over the last 7 days
     */
    record AnalyticsSummary(List<String> names, List<DayCounts> days, List<TopSeries> topSeries) {}

    /** @param counts by event name; names without events are absent */
    record DayCounts(LocalDate day, Map<String, EventCount> counts) {}

    /** @param visitors distinct visitors that day (server events, such as sign-ups, have none) */
    record EventCount(long events, long visitors) {}

    /**
     * @param opens chapter opens
     * @param visitors distinct daily visitors summed over the days, an upper bound on distinct readers
     */
    record TopSeries(String slug, String title, long opens, long visitors) {}
}
