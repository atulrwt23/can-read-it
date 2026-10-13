package com.canreadit.catalog.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff catalog editing. Nothing here is cached; writes need the CSRF header like every cookie write. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('CATALOG_MANAGE')")
class CatalogAdminController {

    private final CatalogAdminService admin;
    private final ChapterPublisher publisher;

    CatalogAdminController(CatalogAdminService admin, ChapterPublisher publisher) {
        this.admin = admin;
        this.publisher = publisher;
    }

    @GetMapping("/series")
    ResponseEntity<AdminViews.AdminSeriesList> list(
            @RequestParam(required = false) @Size(max = 100) @Nullable String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100_000) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return noStore(admin.list(q, offset, limit));
    }

    /** Creates a series as a draft unless the form says otherwise, with its original edition. */
    @PostMapping("/series")
    ResponseEntity<AdminViews.AdminSeries> create(@Valid @RequestBody AdminRequests.SeriesForm form) {
        AdminViews.AdminSeries series = admin.create(form);
        return ResponseEntity.created(URI.create("/api/v1/admin/series/" + series.id()))
                .cacheControl(CacheControl.noStore())
                .body(series);
    }

    @GetMapping("/series/{id}")
    ResponseEntity<AdminViews.AdminSeries> series(@PathVariable UUID id) {
        return noStore(admin.series(id));
    }

    @PutMapping("/series/{id}")
    ResponseEntity<AdminViews.AdminSeries> update(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.SeriesForm form) {
        return noStore(admin.update(id, form));
    }

    /** Adds a draft chapter to the series' original edition. */
    @PostMapping("/series/{id}/chapters")
    ResponseEntity<AdminViews.AdminChapter> createChapter(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.ChapterForm form) {
        AdminViews.AdminChapter chapter = admin.createChapter(id, form);
        return ResponseEntity.created(URI.create("/api/v1/admin/chapters/" + chapter.id()))
                .cacheControl(CacheControl.noStore())
                .body(chapter);
    }

    @GetMapping("/chapters/{id}")
    ResponseEntity<AdminViews.AdminChapter> chapter(@PathVariable UUID id) {
        return noStore(admin.chapter(id));
    }

    @PutMapping("/chapters/{id}")
    ResponseEntity<AdminViews.AdminChapter> updateChapter(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.ChapterForm form) {
        return noStore(admin.updateChapter(id, form));
    }

    @PutMapping("/chapters/{id}/pages")
    ResponseEntity<AdminViews.AdminChapter> pages(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.ChapterPages body) {
        return noStore(admin.replacePages(id, body.assetIds()));
    }

    @PutMapping("/chapters/{id}/body")
    ResponseEntity<AdminViews.AdminChapter> body(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.ChapterBody body) {
        return noStore(admin.replaceBody(id, body.markdown()));
    }

    /**
     * Schedules the release. Without a time (or with one that has passed) the chapter is released now, and the
     * release is recorded straight away instead of at the publish job's next run.
     */
    @PostMapping("/chapters/{id}/schedule")
    ResponseEntity<AdminViews.AdminChapter> schedule(
            @PathVariable UUID id, @Valid @RequestBody AdminRequests.ChapterSchedule body) {
        AdminViews.AdminChapter chapter = admin.schedule(id, body.publishAt());
        if (chapter.live()) {
            publisher.publishDue();
            chapter = admin.chapter(id);
        }
        return noStore(chapter);
    }

    @PostMapping("/chapters/{id}/unschedule")
    ResponseEntity<AdminViews.AdminChapter> unschedule(@PathVariable UUID id) {
        return noStore(admin.unschedule(id));
    }

    @DeleteMapping("/chapters/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        admin.deleteChapter(id);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
