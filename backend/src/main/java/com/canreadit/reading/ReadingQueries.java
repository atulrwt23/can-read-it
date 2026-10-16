package com.canreadit.reading;

import java.util.List;
import java.util.UUID;

/** Read access to readers' libraries for other modules. */
public interface ReadingQueries {

    /** Users who follow the series, in no particular order. */
    // TODO(product): page through followers once a series has more than a few thousand (fan-out at scale).
    List<UUID> followers(UUID seriesId);
}
