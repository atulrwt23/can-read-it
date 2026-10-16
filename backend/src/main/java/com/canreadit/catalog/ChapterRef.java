package com.canreadit.catalog;

import java.util.UUID;

/**
 * A chapter as other modules refer to it: its ID, its series and its number as readers see it ("12.5").
 */
public record ChapterRef(UUID id, UUID seriesId, String number) {}
