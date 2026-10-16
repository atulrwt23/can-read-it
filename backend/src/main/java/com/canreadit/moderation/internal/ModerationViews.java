package com.canreadit.moderation.internal;

import com.canreadit.engagement.ReportReason;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the moderation endpoints return. */
final class ModerationViews {

    private ModerationViews() {}

    record Person(String handle, String displayName) {}

    /** Where the comment was posted; null fields when the series or chapter is gone. */
    record Place(
            @Nullable String seriesSlug,
            @Nullable String seriesTitle,
            @Nullable String chapterNumber) {}

    /** @param author null when the account is being deleted */
    record QueueItem(
            UUID commentId,
            String body,
            Instant createdAt,
            @Nullable Person author,
            Place place,
            int reports,
            Map<ReportReason, Integer> reasons,
            List<String> notes,
            Instant firstReportedAt) {}

    record Queue(long total, List<QueueItem> items) {}

    record ActionView(
            UUID id,
            @Nullable Person moderator,
            String action,
            UUID commentId,
            @Nullable String note,
            Instant at) {}
}
