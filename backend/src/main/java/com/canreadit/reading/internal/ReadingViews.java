package com.canreadit.reading.internal;

import com.canreadit.catalog.SeriesCard;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Responses of the reading endpoints. Personal, so never cached. */
final class ReadingViews {

    private ReadingViews() {}

    /** @param position how far through the chapter, 0–1 */
    record ReadingProgress(String number, double position, Instant updatedAt) {}

    record LibraryItem(
            SeriesCard series, Instant addedAt, @Nullable ReadingProgress progress) {}

    record LibraryPage(List<LibraryItem> items, @Nullable String nextCursor) {}

    record HistoryItem(SeriesCard series, ReadingProgress progress) {}

    record HistoryPage(List<HistoryItem> items, @Nullable String nextCursor) {}

    /**
     * The reader's state for one series, for its page and reader.
     *
     * @param following whether the series is in the library
     * @param readChapters chapters read to the end
     */
    record SeriesReading(boolean following, @Nullable ReadingProgress progress, List<String> readChapters) {}

    /**
     * @param imported series whose history was merged
     * @param skipped series that no longer exist or can't be read
     */
    record ImportResult(int imported, int skipped) {}
}
