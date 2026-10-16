package com.canreadit.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read access to published catalog content for other modules. */
public interface CatalogQueries {

    /** Published series with the most recent releases first. */
    List<SeriesCard> latestUpdates(int limit);

    /** Cards for the given series, in the given order, skipping any that are not published. */
    List<SeriesCard> cardsByIds(List<UUID> seriesIds);

    /** IDs of every published series. */
    List<UUID> publishedSeriesIds();

    /** The ID of the series readers can open at this slug (published or unlisted), if any. */
    Optional<UUID> readableSeriesId(String slug);

    /** Like {@link #cardsByIds}, but unlisted series too: the ones a reader can open by link. */
    List<SeriesCard> readableCardsByIds(List<UUID> seriesIds);

    /**
     * The chapter readers see at this number in the series' original edition, if it is visible (ADR 0006).
     *
     * @param number a chapter number as readers see it ("12", "12.5"); a malformed one is a {@code 400}
     */
    Optional<ChapterRef> visibleChapter(UUID seriesId, String number);

    /**
     * The visible chapters among these numbers, keyed by the number as readers see it. Numbers that are malformed
     * or not visible are absent.
     */
    Map<String, ChapterRef> visibleChapters(UUID seriesId, Collection<String> numbers);

    /** Chapters by ID, whatever their state; unknown IDs are absent. */
    Map<UUID, ChapterRef> chaptersByIds(Collection<UUID> chapterIds);
}
