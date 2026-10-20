package com.canreadit.moderation.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The staff moderation queue (moderators and admins). */
@RestController
@RequestMapping("/api/v1/admin/moderation")
@PreAuthorize("hasAuthority('CONTENT_MODERATE')")
class ModerationController {

    private final ModerationService moderation;

    ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    /** @param note why, for the audit log (optional) */
    record Decision(@Size(max = 500) @Nullable String note) {}

    /** Reported comments, the one waiting longest first. */
    @GetMapping("/comments")
    ResponseEntity<ModerationViews.Queue> queue(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return noStore(moderation.queue(page, size));
    }

    /** Hides the comment from readers and closes its reports. */
    @PostMapping("/comments/{id}/remove")
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Decision decision) {
        moderation.remove(UUID.fromString(jwt.getSubject()), id, decision.note());
        return ResponseEntity.noContent().build();
    }

    /** Keeps the comment and closes its reports. */
    @PostMapping("/comments/{id}/keep")
    ResponseEntity<Void> keep(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Decision decision) {
        moderation.keep(UUID.fromString(jwt.getSubject()), id, decision.note());
        return ResponseEntity.noContent().build();
    }

    /** The latest decisions, newest first. */
    @GetMapping("/actions")
    ResponseEntity<List<ModerationViews.ActionView>> actions(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return noStore(moderation.recentActions(limit));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
