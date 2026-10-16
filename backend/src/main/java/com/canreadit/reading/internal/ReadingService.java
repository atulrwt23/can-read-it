package com.canreadit.reading.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.ChapterRef;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.reading.SeriesFollowed;
import com.canreadit.reading.SeriesUnfollowed;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.Cursors;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in reader's library, progress and read chapters (CLAUDE.md section 6). Series and chapters are stored by
 * ID, so a renamed slug keeps the history; the API speaks slugs and chapter numbers, like the reader's URLs.
 *
 * <p>TODO(product): following and the library are one thing for now; notifications for followed series arrive in
 * phase 2, and the follower count in discovery's {@code series_stats} is not fed yet.
 */
@Service
class ReadingService {

    private final ReadingRepository repository;
    private final CatalogQueries catalog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReadingService(
            ReadingRepository repository, CatalogQueries catalog, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    // --- Library

    @Transactional
    void follow(UUID userId, String slug) {
        UUID seriesId = seriesId(slug);
        Instant now = clock.instant();
        if (repository.follow(userId, seriesId, now)) {
            events.publishEvent(new SeriesFollowed(userId, seriesId, now));
        }
    }

    @Transactional
    void unfollow(UUID userId, String slug) {
        UUID seriesId = seriesId(slug);
        if (repository.unfollow(userId, seriesId)) {
            events.publishEvent(new SeriesUnfollowed(userId, seriesId, clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    ReadingViews.LibraryPage library(UUID userId, @Nullable String cursor, int limit) {
        List<ReadingRepository.Entry> entries = repository.library(userId, after(cursor), limit + 1);
        Page page = Page.of(entries, limit);
        Map<UUID, ReadingViews.ReadingProgress> progress =
                progressBySeries(userId, repository.progress(userId, ids(page.items())));
        List<ReadingViews.LibraryItem> items = new ArrayList<>();
        Map<UUID, ReadingRepository.Entry> byId = byId(page.items());
        for (SeriesCard card : catalog.readableCardsByIds(ids(page.items()))) {
            items.add(new ReadingViews.LibraryItem(card, byId.get(card.id()).at(), progress.get(card.id())));
        }
        return new ReadingViews.LibraryPage(items, page.nextCursor());
    }

    // --- Progress

    /** Records where the reader is; reaching the end marks the chapter read. */
    @Transactional
    void saveProgress(UUID userId, String slug, ReadingRequests.ProgressUpdate update) {
        UUID seriesId = seriesId(slug);
        ChapterRef chapter = catalog.visibleChapter(seriesId, update.number())
                .orElseThrow(() -> ApiException.notFound("chapter_not_found", "This chapter can't be read."));
        Instant now = clock.instant();
        repository.saveProgress(userId, seriesId, chapter.id(), update.position(), now);
        if (update.finished()) {
            repository.markRead(userId, seriesId, List.of(chapter.id()), now);
        }
    }

    @Transactional(readOnly = true)
    ReadingViews.SeriesReading series(UUID userId, String slug) {
        UUID seriesId = seriesId(slug);
        Optional<ReadingRepository.Progress> progress = repository.progress(userId, seriesId);
        List<UUID> read = repository.readChapters(userId, seriesId);
        Set<UUID> chapterIds = new HashSet<>(read);
        progress.ifPresent(p -> chapterIds.add(p.chapterId()));
        Map<UUID, ChapterRef> chapters = catalog.chaptersByIds(chapterIds);
        List<String> readNumbers = read.stream()
                .map(chapters::get)
                .filter(ref -> ref != null)
                .map(ChapterRef::number)
                .sorted(Comparator.comparing(BigDecimal::new))
                .toList();
        return new ReadingViews.SeriesReading(
                repository.follows(userId, seriesId),
                progress.filter(p -> chapters.containsKey(p.chapterId()))
                        .map(p -> view(p, chapters))
                        .orElse(null),
                readNumbers);
    }

    /** Recently read series, newest first: "Continue reading" for signed-in readers. */
    @Transactional(readOnly = true)
    ReadingViews.HistoryPage history(UUID userId, @Nullable String cursor, int limit) {
        List<ReadingRepository.Entry> entries = repository.history(userId, after(cursor), limit + 1);
        Page page = Page.of(entries, limit);
        Map<UUID, ReadingViews.ReadingProgress> progress =
                progressBySeries(userId, repository.progress(userId, ids(page.items())));
        List<ReadingViews.HistoryItem> items = new ArrayList<>();
        for (SeriesCard card : catalog.readableCardsByIds(ids(page.items()))) {
            ReadingViews.ReadingProgress p = progress.get(card.id());
            if (p != null) {
                items.add(new ReadingViews.HistoryItem(card, p));
            }
        }
        return new ReadingViews.HistoryPage(items, page.nextCursor());
    }

    /**
     * Merges a guest's history after sign-in: newer progress replaces older, read marks add up. Series or chapters
     * that can't be read any more are skipped.
     */
    @Transactional
    ReadingViews.ImportResult importHistory(UUID userId, List<ReadingRequests.ImportedSeries> series) {
        Instant now = clock.instant();
        int imported = 0;
        int skipped = 0;
        int marksLeft = ReadingRequests.MAX_IMPORTED_MARKS;
        for (ReadingRequests.ImportedSeries entry : series) {
            Optional<UUID> seriesId = catalog.readableSeriesId(entry.slug());
            if (seriesId.isEmpty()) {
                skipped++;
                continue;
            }
            // The newest marks matter most when there are too many.
            List<String> read = entry.read() == null ? List.of() : entry.read();
            List<String> marks = read.subList(Math.max(0, read.size() - marksLeft), read.size());
            Set<String> numbers = new HashSet<>(marks);
            numbers.add(entry.number());
            Map<String, ChapterRef> chapters = catalog.visibleChapters(seriesId.get(), numbers);
            ChapterRef current = chapters.get(canonical(entry.number()));
            if (current == null) {
                skipped++;
                continue;
            }
            // A guest's clock may run ahead; never let it claim a time after now.
            Instant at = entry.updatedAt().isAfter(now) ? now : entry.updatedAt();
            repository.saveProgress(userId, seriesId.get(), current.id(), entry.position(), at);
            List<UUID> readIds = marks.stream()
                    .map(number -> chapters.get(canonical(number)))
                    .filter(ref -> ref != null)
                    .map(ChapterRef::id)
                    .distinct()
                    .toList();
            repository.markRead(userId, seriesId.get(), readIds, at);
            marksLeft -= marks.size();
            imported++;
        }
        return new ReadingViews.ImportResult(imported, skipped);
    }

    // --- Helpers

    private UUID seriesId(String slug) {
        return catalog.readableSeriesId(slug)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this slug."));
    }

    private Map<UUID, ReadingViews.ReadingProgress> progressBySeries(
            UUID userId, List<ReadingRepository.Progress> progress) {
        Map<UUID, ChapterRef> chapters = catalog.chaptersByIds(
                progress.stream().map(ReadingRepository.Progress::chapterId).toList());
        return progress.stream()
                .filter(p -> chapters.containsKey(p.chapterId()))
                .collect(Collectors.toMap(ReadingRepository.Progress::seriesId, p -> view(p, chapters)));
    }

    /** The progress with its chapter's number; callers check the chapter still exists. */
    private static ReadingViews.ReadingProgress view(
            ReadingRepository.Progress progress, Map<UUID, ChapterRef> chapters) {
        return new ReadingViews.ReadingProgress(
                chapters.get(progress.chapterId()).number(), progress.position(), progress.updatedAt());
    }

    /** "1.50" and "1.5" are the same chapter; catalog keys numbers without trailing zeros. */
    private static String canonical(String number) {
        return new BigDecimal(number).stripTrailingZeros().toPlainString();
    }

    private static List<UUID> ids(Collection<ReadingRepository.Entry> entries) {
        return entries.stream().map(ReadingRepository.Entry::seriesId).toList();
    }

    private static Map<UUID, ReadingRepository.Entry> byId(Collection<ReadingRepository.Entry> entries) {
        return entries.stream().collect(Collectors.toMap(ReadingRepository.Entry::seriesId, Function.identity()));
    }

    private static ReadingRepository.@Nullable Entry after(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        List<String> parts = Cursors.decode(cursor, 2);
        try {
            return new ReadingRepository.Entry(UUID.fromString(parts.get(1)), Instant.parse(parts.get(0)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw ApiException.badRequest("invalid_cursor", "The cursor is malformed or expired.");
        }
    }

    /** One page of entries fetched with one extra row, which tells whether another page follows. */
    private record Page(
            List<ReadingRepository.Entry> items, @Nullable String nextCursor) {

        static Page of(List<ReadingRepository.Entry> fetched, int limit) {
            if (fetched.size() <= limit) {
                return new Page(fetched, null);
            }
            List<ReadingRepository.Entry> items = fetched.subList(0, limit);
            ReadingRepository.Entry last = items.getLast();
            return new Page(
                    items, Cursors.encode(last.at().toString(), last.seriesId().toString()));
        }
    }
}
