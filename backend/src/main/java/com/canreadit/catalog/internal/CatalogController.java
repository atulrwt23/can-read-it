package com.canreadit.catalog.internal;

import com.canreadit.catalog.Genre;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.PublicCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CatalogController {

    static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";
    private static final Duration LISTS = Duration.ofSeconds(60);
    private static final Duration GENRES = Duration.ofHours(1);

    private final CatalogService catalog;
    private final SeriesResolver resolver;

    CatalogController(CatalogService catalog, SeriesResolver resolver) {
        this.catalog = catalog;
        this.resolver = resolver;
    }

    @GetMapping("/genres")
    ResponseEntity<List<Genre>> genres() {
        return ResponseEntity.ok().cacheControl(PublicCache.sharedFor(GENRES)).body(catalog.genres());
    }

    /**
     * Browse and search published series. {@code genres} narrows to series having all of them.
     */
    @GetMapping("/series")
    ResponseEntity<CursorPage<SeriesCard>> browse(
            @RequestParam(required = false) @Size(max = 100) @Nullable String q,
            @RequestParam(required = false) @Nullable SeriesType type,
            @RequestParam(required = false) @Nullable SeriesStatus status,
            @RequestParam(required = false) @Size(max = 10) @Nullable List<@Pattern(regexp = SLUG) String> genres,
            @RequestParam(defaultValue = "UPDATED") BrowseSort sort,
            @RequestParam(required = false) @Size(max = 1024) @Nullable String cursor,
            @RequestParam(defaultValue = "24") @Min(1) @Max(50) int limit) {
        String query = q == null || q.isBlank() ? null : q.strip();
        var page = catalog.browse(new CatalogService.BrowseRequest(
                sort, type, status, query, genres == null ? List.of() : List.copyOf(genres), limit, cursor));
        return ResponseEntity.ok().cacheControl(PublicCache.sharedFor(LISTS)).body(page);
    }

    @GetMapping("/series/{slug}")
    ResponseEntity<SeriesDetail> series(@PathVariable @Pattern(regexp = SLUG) String slug, HttpServletRequest request) {
        var detail = catalog.detail(resolver.resolve(slug, request));
        return ResponseEntity.ok().cacheControl(PublicCache.sharedFor(LISTS)).body(detail);
    }
}
