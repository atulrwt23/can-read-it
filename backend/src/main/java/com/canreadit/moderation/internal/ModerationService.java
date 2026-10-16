package com.canreadit.moderation.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.ChapterRef;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.engagement.CommentModeration;
import com.canreadit.identity.UserDirectory;
import com.canreadit.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The moderation queue: reported comments with where they were posted and who wrote them, and the decisions
 * moderators make, each written to the audit log in the same transaction.
 */
@Service
class ModerationService {

    private final CommentModeration comments;
    private final CatalogQueries catalog;
    private final UserDirectory users;
    private final ActionRepository actions;
    private final Clock clock;

    ModerationService(
            CommentModeration comments,
            CatalogQueries catalog,
            UserDirectory users,
            ActionRepository actions,
            Clock clock) {
        this.comments = comments;
        this.catalog = catalog;
        this.users = users;
        this.actions = actions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    ModerationViews.Queue queue(int page, int size) {
        List<CommentModeration.ReportedComment> reported = comments.reported(page * size, size);
        Map<UUID, ChapterRef> chapters = catalog.chaptersByIds(reported.stream()
                .map(CommentModeration.ReportedComment::chapterId)
                .toList());
        Map<UUID, SeriesCard> series = catalog
                .readableCardsByIds(reported.stream()
                        .map(CommentModeration.ReportedComment::seriesId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SeriesCard::id, Function.identity()));
        Map<UUID, UserDirectory.Profile> authors = users.profiles(reported.stream()
                .map(CommentModeration.ReportedComment::authorId)
                .toList());
        List<ModerationViews.QueueItem> items = reported.stream()
                .map(comment -> {
                    SeriesCard card = series.get(comment.seriesId());
                    ChapterRef chapter = chapters.get(comment.chapterId());
                    return new ModerationViews.QueueItem(
                            comment.commentId(),
                            comment.body(),
                            comment.createdAt(),
                            person(authors.get(comment.authorId())),
                            new ModerationViews.Place(
                                    card == null ? null : card.slug(),
                                    card == null ? null : card.title(),
                                    chapter == null ? null : chapter.number()),
                            comment.reports(),
                            comment.reasons(),
                            comment.notes(),
                            comment.firstReportedAt());
                })
                .toList();
        return new ModerationViews.Queue(comments.reportedCount(), items);
    }

    @Transactional
    void remove(UUID moderatorId, UUID commentId, @Nullable String note) {
        Instant now = clock.instant();
        if (!comments.remove(commentId, moderatorId, now)) {
            throw ApiException.notFound("comment_not_found", "No visible comment has this ID.");
        }
        actions.log(moderatorId, ActionRepository.Action.REMOVE_COMMENT, commentId, blankToNull(note), now);
    }

    @Transactional
    void keep(UUID moderatorId, UUID commentId, @Nullable String note) {
        Instant now = clock.instant();
        if (!comments.keep(commentId, moderatorId, now)) {
            throw ApiException.notFound("no_open_reports", "This comment has no open reports.");
        }
        actions.log(moderatorId, ActionRepository.Action.KEEP_COMMENT, commentId, blankToNull(note), now);
    }

    @Transactional(readOnly = true)
    List<ModerationViews.ActionView> recentActions(int limit) {
        List<ActionRepository.LoggedAction> logged = actions.recent(limit);
        Map<UUID, UserDirectory.Profile> moderators = users.profiles(logged.stream()
                .map(ActionRepository.LoggedAction::moderatorId)
                .distinct()
                .toList());
        return logged.stream()
                .map(action -> new ModerationViews.ActionView(
                        action.id(),
                        person(moderators.get(action.moderatorId())),
                        action.action().name(),
                        action.targetId(),
                        action.note(),
                        action.at()))
                .toList();
    }

    private static ModerationViews.@Nullable Person person(UserDirectory.@Nullable Profile profile) {
        return profile == null ? null : new ModerationViews.Person(profile.handle(), profile.displayName());
    }

    private static @Nullable String blankToNull(@Nullable String note) {
        return Stream.ofNullable(note)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
