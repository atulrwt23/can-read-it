package com.canreadit.reading.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Request bodies of the reading endpoints. */
final class ReadingRequests {

    /** A chapter number as readers see it: "12", "12.5". */
    static final String CHAPTER_NUMBER = "^\\d{1,6}(\\.\\d{1,2})?$";

    static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** The guest history keeps at most 200 series (web/src/lib/history.ts). */
    static final int MAX_IMPORTED_SERIES = 200;

    /** Read marks taken from one import; the guest history keeps up to 2,000 per series, newest last. */
    static final int MAX_IMPORTED_MARKS = 5_000;

    private ReadingRequests() {}

    /**
     * Where the reader is now.
     *
     * @param position how far through the chapter, 0–1
     * @param finished the reader reached the end: the chapter is marked read
     */
    record ProgressUpdate(
            @NotNull @Pattern(regexp = CHAPTER_NUMBER) String number,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double position,
            boolean finished) {}

    /** A guest's history, sent once after sign-in. */
    record HistoryImport(
            @NotNull @Size(max = MAX_IMPORTED_SERIES) List<@NotNull @Valid ImportedSeries> series) {}

    /** @param read chapters read to the end, oldest first */
    record ImportedSeries(
            @NotNull @Size(max = 80) @Pattern(regexp = SLUG) String slug,
            @NotNull @Pattern(regexp = CHAPTER_NUMBER) String number,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double position,
            @NotNull Instant updatedAt,
            @Size(max = 2000) @Nullable List<@NotNull @Pattern(regexp = CHAPTER_NUMBER) String> read) {}
}
