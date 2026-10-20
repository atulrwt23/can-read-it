package com.canreadit.engagement.internal;

import com.canreadit.engagement.ReportReason;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.PublicCache;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chapter comments: public to read, and written by signed-in readers with a confirmed email. */
@RestController
class CommentController {

    static final String NUMBER = "[0-9]{1,6}(\\.[0-9]{1,2})?";

    static final RateLimit PER_MINUTE = new RateLimit("engagement-comment", 5, Duration.ofMinutes(1));

    /** TODO(product): a daily cap per reader; this one only stops floods. */
    static final RateLimit PER_DAY = new RateLimit("engagement-comment-day", 100, Duration.ofDays(1));

    static final RateLimit DELETE = new RateLimit("engagement-comment-delete", 30, Duration.ofMinutes(1));

    /** New comments may take this long to show for other readers. */
    static final Duration PUBLIC_CACHE = Duration.ofSeconds(15);

    /** Reports are rare; this stops one reader from flooding the queue. */
    static final RateLimit REPORT = new RateLimit("engagement-report", 10, Duration.ofHours(1));

    private final CommentService comments;
    private final ReportService reports;
    private final RateLimiter rateLimiter;

    CommentController(CommentService comments, ReportService reports, RateLimiter rateLimiter) {
        this.comments = comments;
        this.reports = reports;
        this.rateLimiter = rateLimiter;
    }

    /** @param note optional details for the moderators */
    record NewReport(
            @NotNull ReportReason reason,
            @Size(max = 500) @Nullable String note) {}

    /** Validated again after trimming; the raw limit leaves room for whitespace that gets removed. */
    record NewComment(@NotNull @Size(max = 4000) String body) {}

    /** A chapter's comments, newest first; the same for every visitor. */
    @GetMapping("/api/v1/series/{slug}/chapters/{number}/comments")
    ResponseEntity<CursorPage<CommentService.CommentView>> page(
            @PathVariable @Pattern(regexp = RatingController.SLUG) String slug,
            @PathVariable @Pattern(regexp = NUMBER) String number,
            @RequestParam(required = false) @Size(max = 1024) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok()
                .cacheControl(PublicCache.sharedFor(PUBLIC_CACHE))
                .body(comments.page(slug, number, cursor, limit));
    }

    @PostMapping("/api/v1/series/{slug}/chapters/{number}/comments")
    ResponseEntity<CommentService.CommentView> post(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = RatingController.SLUG) String slug,
            @PathVariable @Pattern(regexp = NUMBER) String number,
            @Valid @RequestBody NewComment comment) {
        UUID userId = confirmedReader(jwt);
        rateLimiter.check(PER_MINUTE, userId.toString());
        rateLimiter.check(PER_DAY, userId.toString());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(comments.post(userId, slug, number, comment.body()));
    }

    /** Readers delete their own comments. */
    @DeleteMapping("/api/v1/me/comments/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        rateLimiter.check(DELETE, userId.toString());
        comments.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reports a comment to the moderators. Any signed-in reader may report (TODO(product): require a confirmed
     * email if reports get abused); reporting the same comment again changes nothing.
     */
    @PostMapping("/api/v1/comments/{id}/reports")
    ResponseEntity<Void> report(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody NewReport report) {
        UUID userId = UUID.fromString(jwt.getSubject());
        rateLimiter.check(REPORT, userId.toString());
        reports.report(userId, id, report.reason(), report.note());
        return ResponseEntity.noContent().build();
    }

    /** Commenting needs a confirmed email (CLAUDE.md section 7). */
    private static UUID confirmedReader(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("ev"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "email_not_confirmed", "Confirm your email address to comment.");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
