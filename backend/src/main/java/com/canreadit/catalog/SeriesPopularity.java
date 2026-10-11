package com.canreadit.catalog;

import java.util.Map;
import java.util.UUID;

/** Lets discovery hand the catalog each series' popularity, which browse sorts by. */
public interface SeriesPopularity {

    /** Sets each given series' popularity (views in the last 7 days). Unknown IDs are ignored. */
    void update(Map<UUID, Long> sevenDayViews);
}
