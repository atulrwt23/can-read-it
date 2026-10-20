package com.canreadit.engagement.internal;

import com.canreadit.engagement.CommentModeration;
import com.canreadit.engagement.ReportReason;
import com.canreadit.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Readers report comments; moderators (through the moderation module) remove them or keep them. */
@Service
class ReportService implements CommentModeration {

    private final ReportRepository reports;
    private final CommentRepository comments;
    private final Clock clock;

    ReportService(ReportRepository reports, CommentRepository comments, Clock clock) {
        this.reports = reports;
        this.comments = comments;
        this.clock = clock;
    }

    @Transactional
    void report(UUID reporterId, UUID commentId, ReportReason reason, @Nullable String note) {
        UUID author = comments.visibleAuthor(commentId)
                .orElseThrow(() -> ApiException.notFound("comment_not_found", "No comment has this ID."));
        if (author.equals(reporterId)) {
            throw ApiException.badRequest("cannot_report_own_comment", "You can delete your own comment instead.");
        }
        String text = note == null ? null : CommentService.normalize(note);
        reports.report(commentId, reporterId, reason, text == null || text.isEmpty() ? null : text, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportedComment> reported(int offset, int limit) {
        return reports.reported(offset, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long reportedCount() {
        return reports.reportedCount();
    }

    @Override
    @Transactional
    public boolean remove(UUID commentId, UUID moderatorId, Instant at) {
        if (!reports.removeComment(commentId, moderatorId, at)) {
            return false;
        }
        reports.resolve(commentId, "REMOVED", moderatorId, at);
        return true;
    }

    @Override
    @Transactional
    public boolean keep(UUID commentId, UUID moderatorId, Instant at) {
        return reports.resolve(commentId, "DISMISSED", moderatorId, at) > 0;
    }
}
