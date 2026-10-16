package com.canreadit.engagement.internal;

import com.canreadit.shared.ApiException;
import com.canreadit.shared.PublicCache;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A series' average rating for everyone, and the signed-in reader's own score. */
@RestController
class RatingController {

    static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";

    /** Changing a score a few times is normal; this stops scripted flipping. */
    static final RateLimit RATE = new RateLimit("engagement-rate", 30, Duration.ofMinutes(1));

    /** The average shown on series pages may lag real ratings by this much. */
    static final Duration PUBLIC_CACHE = Duration.ofMinutes(1);

    private final RatingService ratings;
    private final RateLimiter rateLimiter;

    RatingController(RatingService ratings, RateLimiter rateLimiter) {
        this.ratings = ratings;
        this.rateLimiter = rateLimiter;
    }

    record RatingUpdate(@NotNull @Min(1) @Max(5) Integer score) {}

    @GetMapping("/api/v1/series/{slug}/rating")
    ResponseEntity<RatingService.SeriesRating> seriesRating(@PathVariable @Pattern(regexp = SLUG) String slug) {
        return ResponseEntity.ok()
                .cacheControl(PublicCache.sharedFor(PUBLIC_CACHE))
                .body(ratings.seriesRating(slug));
    }

    @GetMapping("/api/v1/me/ratings/{slug}")
    ResponseEntity<RatingService.MyRating> myRating(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = SLUG) String slug) {
        return noStore(ratings.myRating(userId(jwt), slug));
    }

    /** Rates a series from 1 to 5, replacing an earlier score. Needs a confirmed email. */
    @PutMapping("/api/v1/me/ratings/{slug}")
    ResponseEntity<RatingService.MyRating> rate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = SLUG) String slug,
            @Valid @RequestBody RatingUpdate update) {
        UUID userId = confirmedReader(jwt);
        rateLimiter.check(RATE, userId.toString());
        return noStore(ratings.rate(userId, slug, update.score()));
    }

    @DeleteMapping("/api/v1/me/ratings/{slug}")
    ResponseEntity<RatingService.MyRating> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = SLUG) String slug) {
        UUID userId = userId(jwt);
        rateLimiter.check(RATE, userId.toString());
        return noStore(ratings.remove(userId, slug));
    }

    /**
     * Ratings are public, so like comments they need a confirmed email (the access token's {@code ev} claim), which
     * makes throwaway accounts cost a real inbox each. TODO(product): CLAUDE.md names only comments and purchases.
     */
    private static UUID confirmedReader(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("ev"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "email_not_confirmed", "Confirm your email address to rate series.");
        }
        return userId(jwt);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
