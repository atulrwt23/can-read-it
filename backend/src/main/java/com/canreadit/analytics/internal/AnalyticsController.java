package com.canreadit.analytics.internal;

import com.canreadit.shared.ClientIp;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AnalyticsController {

    /** A browser sends a batch every few seconds at most; this is far above that, even behind a shared address. */
    static final RateLimit PER_IP = new RateLimit("analytics", 60, Duration.ofMinutes(1));

    private final AnalyticsService analytics;
    private final RateLimiter rateLimiter;

    AnalyticsController(AnalyticsService analytics, RateLimiter rateLimiter) {
        this.analytics = analytics;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Anonymous events from the web app, sent with {@code navigator.sendBeacon}. Needs no account and reads no
     * cookie; answers 204 whether or not the events were kept.
     */
    @PostMapping("/api/v1/analytics/events")
    ResponseEntity<Void> events(@Valid @RequestBody AnalyticsRequests.EventBatch batch, HttpServletRequest request) {
        String clientIp = ClientIp.of(request);
        rateLimiter.check(PER_IP, clientIp);
        analytics.record(batch.events(), clientIp, request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** Daily event counts and the most read series, for staff. */
    @GetMapping("/api/v1/admin/analytics")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
    ResponseEntity<AnalyticsViews.AnalyticsSummary> summary(
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analytics.summary(days));
    }
}
