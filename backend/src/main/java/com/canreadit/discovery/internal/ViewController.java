package com.canreadit.discovery.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.ClientIp;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ViewController {

    /**
     * Far above what readers behind one IP send (one beacon per chapter opened), even on a shared mobile network:
     * this keeps scripts from flooding Redis. Inflating counts is limited separately (ViewCounter).
     */
    static final RateLimit PER_IP = new RateLimit("views", 120, Duration.ofMinutes(1));

    private final CatalogQueries catalog;
    private final ViewCounter views;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ViewController(CatalogQueries catalog, ViewCounter views, RateLimiter rateLimiter, Clock clock) {
        this.catalog = catalog;
        this.views = views;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Sent by the reader when a chapter opens. Counts once per visitor, series and day; answers 204. */
    @PostMapping("/api/v1/series/{slug}/views")
    ResponseEntity<Void> view(@PathVariable String slug, HttpServletRequest request) {
        String clientIp = ClientIp.of(request);
        rateLimiter.check(PER_IP, clientIp);
        UUID seriesId = catalog.readableSeriesId(slug)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this slug."));
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        views.record(seriesId, clientIp, userAgent == null ? "" : userAgent, LocalDate.now(clock));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
