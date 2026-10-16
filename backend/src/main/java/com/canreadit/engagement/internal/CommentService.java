package com.canreadit.engagement.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.ChapterRef;
import com.canreadit.identity.UserDirectory;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.Cursors;
import com.canreadit.shared.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments under chapters: plain text, newest first, written by readers with a confirmed email. */
@Service
class CommentService {

    static final int MAX_LENGTH = 2000;

    /** Control characters other than tab and newline (they can hide text or break layouts). */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private final CommentRepository repository;
    private final CatalogQueries catalog;
    private final UserDirectory users;
    private final Clock clock;

    CommentService(CommentRepository repository, CatalogQueries catalog, UserDirectory users, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.users = users;
        this.clock = clock;
    }

    /** Who wrote a comment, by public name only. */
    record Author(String handle, String displayName) {}

    record CommentView(UUID id, Author author, String body, Instant createdAt) {}

    @Transactional(readOnly = true)
    CursorPage<CommentView> page(String slug, String number, @Nullable String cursor, int limit) {
        ChapterRef chapter = chapter(slug, number);
        UUID before = cursor == null ? null : parseCursor(cursor);
        List<CommentRepository.Comment> rows = repository.page(chapter.id(), before, limit + 1);
        boolean more = rows.size() > limit;
        List<CommentRepository.Comment> shown = more ? rows.subList(0, limit) : rows;
        Map<UUID, UserDirectory.Profile> authors = users.profiles(
                shown.stream().map(CommentRepository.Comment::userId).distinct().toList());
        // Comments by accounts being deleted disappear at once; erasure removes them for good.
        List<CommentView> items = shown.stream()
                .filter(c -> authors.containsKey(c.userId()))
                .map(c -> view(c, authors.get(c.userId())))
                .toList();
        String next = more ? Cursors.encode(shown.getLast().id().toString()) : null;
        return new CursorPage<>(items, next);
    }

    @Transactional
    CommentView post(UUID userId, String slug, String number, String body) {
        String text = normalize(body);
        if (text.isEmpty()) {
            throw ApiException.badRequest("comment_empty", "Write something first.");
        }
        if (text.length() > MAX_LENGTH) {
            throw ApiException.badRequest("comment_too_long", "Comments can be at most 2000 characters.");
        }
        ChapterRef chapter = chapter(slug, number);
        UserDirectory.Profile author = users.profiles(List.of(userId)).get(userId);
        if (author == null) {
            throw ApiException.notFound("account_not_found", "This account can't comment.");
        }
        var comment = new CommentRepository.Comment(
                Ids.newId(), chapter.seriesId(), chapter.id(), userId, text, clock.instant());
        repository.insert(comment);
        return view(comment, author);
    }

    @Transactional
    void delete(UUID userId, UUID commentId) {
        if (!repository.deleteOwn(commentId, userId)) {
            throw ApiException.notFound("comment_not_found", "No comment of yours has this ID.");
        }
    }

    @Transactional
    void forget(UUID userId) {
        repository.deleteAllBy(userId);
    }

    /** Trims, keeps line breaks (at most one blank line in a row), and drops other control characters. */
    static String normalize(String body) {
        String text = body.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL.matcher(text).replaceAll("");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    private static CommentView view(CommentRepository.Comment comment, UserDirectory.Profile author) {
        return new CommentView(
                comment.id(), new Author(author.handle(), author.displayName()), comment.body(), comment.createdAt());
    }

    /** Comments belong to chapters readers can see, in series they can open. */
    private ChapterRef chapter(String slug, String number) {
        UUID seriesId = catalog.readableSeriesId(slug)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this slug."));
        return catalog.visibleChapter(seriesId, number)
                .orElseThrow(() ->
                        ApiException.notFound("chapter_not_found", "This chapter does not exist or is not out yet."));
    }

    private static UUID parseCursor(String cursor) {
        String id = Cursors.decode(cursor, 1).getFirst();
        try {
            return UUID.fromString(Objects.requireNonNull(id));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_cursor", "The cursor is malformed or expired.");
        }
    }
}
