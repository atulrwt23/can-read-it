package com.canreadit.reading;

import java.time.Instant;
import java.util.UUID;

/** A reader followed a series (it was not in their library before). */
public record SeriesFollowed(UUID userId, UUID seriesId, Instant at) {}
