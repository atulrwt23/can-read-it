package com.canreadit.catalog;

import java.util.List;
import java.util.UUID;

/** Read access to published catalog content for other modules. */
public interface CatalogQueries {

    /** Published series with the most recent releases first. */
    List<SeriesCard> latestUpdates(int limit);

    /** Cards for the given series, in the given order, skipping any that are not published. */
    List<SeriesCard> cardsByIds(List<UUID> seriesIds);

    /** IDs of every published series. */
    List<UUID> publishedSeriesIds();
}
