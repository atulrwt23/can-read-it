package com.canreadit.discovery.internal;

import com.canreadit.catalog.SeriesCard;
import java.util.List;

/** Everything the home page renders, in one cacheable response. */
record HomeResponse(List<SeriesCard> featured, List<SeriesCard> latestUpdates, Popular popular) {

    record Popular(List<RankedSeries> today, List<RankedSeries> week, List<RankedSeries> allTime) {}

    /** @param rank 1-based position */
    record RankedSeries(int rank, long views, SeriesCard series) {}
}
