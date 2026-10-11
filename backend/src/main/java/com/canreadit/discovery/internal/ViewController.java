package com.canreadit.discovery.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
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

    private final CatalogQueries catalog;
    private final ViewCounter views;
    private final Clock clock;

    ViewController(CatalogQueries catalog, ViewCounter views, Clock clock) {
        this.catalog = catalog;
        this.views = views;
        this.clock = clock;
    }

    /** Sent by the reader when a chapter opens. Counts once per visitor, series and day; always answers 204. */
    @PostMapping("/api/v1/series/{slug}/views")
    ResponseEntity<Void> view(@PathVariable String slug, HttpServletRequest request) {
        UUID seriesId = catalog.readableSeriesId(slug)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this slug."));
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        views.record(seriesId, ClientIp.of(request), userAgent == null ? "" : userAgent, LocalDate.now(clock));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
