package com.canreadit.reading;

import java.time.Instant;
import java.util.UUID;

/** A reader unfollowed a series. */
public record SeriesUnfollowed(UUID userId, UUID seriesId, Instant at) {}
