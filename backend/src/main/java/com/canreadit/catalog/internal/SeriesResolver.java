package com.canreadit.catalog.internal;

import com.canreadit.shared.ApiException;
import com.canreadit.shared.MovedPermanentlyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Finds the series a URL slug refers to and applies series visibility: drafts are 404, removed
 * series are 410, and old slugs answer 301 with the same path under the current slug.
 */
@Component
class SeriesResolver {

    private final SeriesRepository series;

    SeriesResolver(SeriesRepository series) {
        this.series = series;
    }

    SeriesRow resolve(String slug, HttpServletRequest request) {
        var row = series.findBySlug(slug);
        if (row.isPresent()) {
            return switch (row.get().visibility()) {
                case PUBLISHED, UNLISTED -> row.get();
                case REMOVED -> throw ApiException.gone("series_removed", "This series is no longer available.");
                case DRAFT -> throw notFound();
            };
        }
        String current = series.findCurrentSlug(slug).orElseThrow(SeriesResolver::notFound);
        throw new MovedPermanentlyException(relocate(request, slug, current));
    }

    private static String relocate(HttpServletRequest request, String oldSlug, String newSlug) {
        String path = request.getRequestURI().replaceFirst("/series/" + oldSlug + "(?=/|$)", "/series/" + newSlug);
        return request.getQueryString() == null ? path : path + "?" + request.getQueryString();
    }

    private static ApiException notFound() {
        return ApiException.notFound("series_not_found", "No series has this slug.");
    }
}
