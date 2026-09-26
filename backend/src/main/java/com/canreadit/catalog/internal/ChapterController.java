package com.canreadit.catalog.internal;

import static com.canreadit.catalog.internal.CatalogController.SLUG;

import com.canreadit.catalog.ChapterSummary;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.PublicCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/series/{slug}/chapters")
class ChapterController {

    static final String LANGUAGE = "^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$";
    private static final Duration LIST = Duration.ofSeconds(60);
    private static final Duration CONTENT = Duration.ofMinutes(5);

    private final SeriesResolver resolver;
    private final ChapterService chapters;

    ChapterController(SeriesResolver resolver, ChapterService chapters) {
        this.resolver = resolver;
        this.chapters = chapters;
    }

    /** Visible chapters of one edition ({@code lang}, default: the original). */
    @GetMapping
    ResponseEntity<CursorPage<ChapterSummary>> list(
            @PathVariable @Pattern(regexp = SLUG) String slug,
            @RequestParam(required = false) @Pattern(regexp = LANGUAGE) @Nullable String lang,
            @RequestParam(defaultValue = "DESC") ChapterOrder order,
            @RequestParam(required = false) @Size(max = 1024) @Nullable String cursor,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
            HttpServletRequest request) {
        var page = chapters.list(resolver.resolve(slug, request), lang, order, cursor, limit);
        return ResponseEntity.ok().cacheControl(PublicCache.sharedFor(LIST)).body(page);
    }

    @GetMapping("/{number}")
    ResponseEntity<ChapterContent> chapter(
            @PathVariable @Pattern(regexp = SLUG) String slug,
            @PathVariable @Size(max = 12) String number,
            @RequestParam(required = false) @Pattern(regexp = LANGUAGE) @Nullable String lang,
            HttpServletRequest request) {
        var content = chapters.content(resolver.resolve(slug, request), lang, number);
        return ResponseEntity.ok().cacheControl(PublicCache.sharedFor(CONTENT)).body(content);
    }
}
