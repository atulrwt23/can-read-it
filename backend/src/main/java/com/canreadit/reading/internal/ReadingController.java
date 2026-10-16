package com.canreadit.reading.internal;

import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in reader's library, progress and history. Personal, so nothing is cached; writes need the CSRF header
 * like every cookie write. Reading needs no confirmed email (CLAUDE.md section 7).
 */
@RestController
@RequestMapping("/api/v1/me")
class ReadingController {

    /** The reader saves progress every few seconds while reading. */
    static final RateLimit PROGRESS = new RateLimit("reading-progress", 120, Duration.ofMinutes(1));

    static final RateLimit LIBRARY = new RateLimit("reading-library", 60, Duration.ofMinutes(1));

    /** Sent once after each sign-in. */
    static final RateLimit IMPORT = new RateLimit("reading-import", 10, Duration.ofHours(1));

    private final ReadingService reading;
    private final RateLimiter rateLimiter;

    ReadingController(ReadingService reading, RateLimiter rateLimiter) {
        this.reading = reading;
        this.rateLimiter = rateLimiter;
    }

    /** Followed series, most recently added first, with where the reader is in each. */
    @GetMapping("/library")
    ResponseEntity<ReadingViews.LibraryPage> library(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 1024) @Nullable String cursor,
            @RequestParam(defaultValue = "24") @Min(1) @Max(50) int limit) {
        return noStore(reading.library(userId(jwt), cursor, limit));
    }

    /** Follows a series (adds it to the library). Following twice changes nothing. */
    @PutMapping("/library/{slug}")
    ResponseEntity<Void> follow(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = ReadingRequests.SLUG) String slug) {
        UUID userId = userId(jwt);
        rateLimiter.check(LIBRARY, userId.toString());
        reading.follow(userId, slug);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/library/{slug}")
    ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = ReadingRequests.SLUG) String slug) {
        UUID userId = userId(jwt);
        rateLimiter.check(LIBRARY, userId.toString());
        reading.unfollow(userId, slug);
        return ResponseEntity.noContent().build();
    }

    /** Whether the reader follows the series, where they are in it, and which chapters they finished. */
    @GetMapping("/series/{slug}")
    ResponseEntity<ReadingViews.SeriesReading> series(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = ReadingRequests.SLUG) String slug) {
        return noStore(reading.series(userId(jwt), slug));
    }

    /** Saves where the reader is in a series; {@code finished} also marks the chapter read. */
    @PutMapping("/progress/{slug}")
    ResponseEntity<Void> progress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = ReadingRequests.SLUG) String slug,
            @Valid @RequestBody ReadingRequests.ProgressUpdate body) {
        UUID userId = userId(jwt);
        rateLimiter.check(PROGRESS, userId.toString());
        reading.saveProgress(userId, slug, body);
        return ResponseEntity.noContent().build();
    }

    /** Recently read series, newest first ("Continue reading"). */
    @GetMapping("/history")
    ResponseEntity<ReadingViews.HistoryPage> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 1024) @Nullable String cursor,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return noStore(reading.history(userId(jwt), cursor, limit));
    }

    /** Merges the history a guest kept in the browser into the account (sent once after sign-in). */
    @PostMapping("/history/import")
    ResponseEntity<ReadingViews.ImportResult> importHistory(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReadingRequests.HistoryImport body) {
        UUID userId = userId(jwt);
        rateLimiter.check(IMPORT, userId.toString());
        return noStore(reading.importHistory(userId, body.series()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
