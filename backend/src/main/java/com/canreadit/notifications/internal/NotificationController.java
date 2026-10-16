package com.canreadit.notifications.internal;

import com.canreadit.shared.ApiException;
import com.canreadit.shared.ClientIp;
import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the reader is emailed about, and the unsubscribe link in those emails. */
@RestController
class NotificationController {

    static final RateLimit UNSUBSCRIBE = new RateLimit("notifications-unsubscribe", 30, Duration.ofHours(1));

    static final RateLimit PREFERENCES = new RateLimit("notifications-preferences", 30, Duration.ofMinutes(1));

    private final NotificationRepository repository;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    NotificationController(NotificationRepository repository, RateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** @param newChapterEmails an email when series the reader follows get new chapters (at most one every 3 h) */
    record NotificationSettings(boolean newChapterEmails) {}

    record NotificationSettingsUpdate(@NotNull Boolean newChapterEmails) {}

    @GetMapping("/api/v1/me/notifications")
    ResponseEntity<NotificationSettings> settings(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NotificationSettings(repository.newChapterEmails(userId(jwt))));
    }

    @PatchMapping("/api/v1/me/notifications")
    ResponseEntity<NotificationSettings> update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NotificationSettingsUpdate update) {
        UUID userId = userId(jwt);
        rateLimiter.check(PREFERENCES, userId.toString());
        repository.setNewChapterEmails(userId, update.newChapterEmails(), clock.instant());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NotificationSettings(update.newChapterEmails()));
    }

    /**
     * Turns new-chapter emails off for the owner of the token in an email. Needs no account or cookie: the unsubscribe
     * page posts here, and so do mail apps (RFC 8058 one-click, which adds a form body this ignores).
     */
    @PostMapping("/api/v1/notifications/unsubscribe")
    ResponseEntity<Void> unsubscribe(
            @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{32}") String token, HttpServletRequest request) {
        rateLimiter.check(UNSUBSCRIBE, ClientIp.of(request));
        if (repository.unsubscribe(token, clock.instant()).isEmpty()) {
            throw ApiException.notFound(
                    "unsubscribe_link_invalid", "This unsubscribe link is not valid. Change emails in your account.");
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
