package com.canreadit.engagement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reported comments and what moderators decide about them, for the moderation module. Who reported a comment is never
 * exposed: moderators see how many readers did, why, and their notes.
 */
public interface CommentModeration {

    /** Comments with open reports, the one waiting longest first. */
    List<ReportedComment> reported(int offset, int limit);

    /** How many comments have open reports. */
    long reportedCount();

    /** Hides the comment for good and closes its reports. False if no visible comment has this ID. */
    boolean remove(UUID commentId, UUID moderatorId, Instant at);

    /** Keeps the comment and closes its open reports. False if it has none. */
    boolean keep(UUID commentId, UUID moderatorId, Instant at);

    /**
     * @param reasons how many open reports gave each reason
     * @param notes what reporters added, oldest first (may be empty)
     */
    record ReportedComment(
            UUID commentId,
            UUID authorId,
            UUID seriesId,
            UUID chapterId,
            String body,
            Instant createdAt,
            int reports,
            Map<ReportReason, Integer> reasons,
            List<String> notes,
            Instant firstReportedAt) {}
}
