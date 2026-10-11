package com.canreadit.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * A scheduled chapter has become visible to readers (ADR 0006). Published once per chapter, through the event
 * publication registry, by the job that flips due chapters to {@code PUBLISHED}. Listeners refresh caches and
 * rankings, and later notify followers.
 *
 * @param language the chapter's edition (BCP-47), so listeners can tell the original from translations
 * @param publishedAt when the chapter became visible: its scheduled {@code publish_at}
 */
public record ChapterPublished(
        UUID seriesId, String seriesSlug, UUID chapterId, String number, String language, Instant publishedAt) {}
