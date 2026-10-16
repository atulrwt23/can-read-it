package com.canreadit.notifications.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.identity.UserDirectory;
import com.canreadit.notifications.EmailMessage;
import com.canreadit.notifications.EmailSender;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emails each reader the chapters queued for them, at most one email per {@code app.notifications.min-gap}, grouped
 * by series. Only confirmed, active accounts get email; anything queued for others is dropped. Each reader is sent in
 * a transaction of its own that locks their row, so several API instances never send the same digest.
 */
@Component
class ChapterDigests {

    private static final Logger log = LoggerFactory.getLogger(ChapterDigests.class);

    /** Readers handled per run; the rest wait for the next run. */
    static final int BATCH = 200;

    /** Chapters listed per series; the rest are a link to the series. */
    static final int CHAPTERS_PER_SERIES = 5;

    private final NotificationRepository repository;
    private final UserDirectory users;
    private final CatalogQueries catalog;
    private final EmailSender sender;
    private final NotificationsProperties properties;
    private final Brand brand;
    private final TransactionTemplate tx;
    private final Clock clock;

    ChapterDigests(
            NotificationRepository repository,
            UserDirectory users,
            CatalogQueries catalog,
            EmailSender sender,
            NotificationsProperties properties,
            Brand brand,
            TransactionTemplate tx,
            Clock clock) {
        this.repository = repository;
        this.users = users;
        this.catalog = catalog;
        this.sender = sender;
        this.properties = properties;
        this.brand = brand;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.digest-interval:PT10M}", initialDelayString = "PT1M")
    void run() {
        int sent = sendDue();
        if (sent > 0) {
            log.info("Sent {} new-chapter email(s)", sent);
        }
    }

    /** Sends the digests that are due and returns how many went out. */
    int sendDue() {
        Instant now = clock.instant();
        int expired = repository.deletePendingBefore(now.minus(properties.pendingTtl()));
        if (expired > 0) {
            log.info("Dropped {} queued chapter(s) that waited too long", expired);
        }
        String site = properties.site();
        if (site == null) {
            // Without the site's address the emails can't link anywhere; chapters wait (up to the TTL).
            log.debug("app.notifications.site-url is not set; new-chapter emails wait");
            return 0;
        }
        Instant cutoff = now.minus(properties.minGap());
        List<UUID> due = repository.dueReaders(cutoff, BATCH);
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, UserDirectory.Contact> contacts = users.contacts(due);
        int sent = 0;
        for (UUID userId : due) {
            UserDirectory.Contact contact = contacts.get(userId);
            if (contact == null) {
                // Unconfirmed, suspended or deleted: never emailed.
                repository.deletePending(userId);
                continue;
            }
            if (Boolean.TRUE.equals(tx.execute(status -> sendOne(contact, cutoff, site)))) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendOne(UserDirectory.Contact contact, Instant cutoff, String site) {
        UUID userId = contact.userId();
        Instant now = clock.instant();
        NotificationRepository.Preferences preferences = repository.preferences(userId, now);
        if (!repository.claimDigest(userId, cutoff)) {
            return false;
        }
        List<NotificationRepository.Pending> pending = repository.pending(userId);
        if (pending.isEmpty()) {
            return false;
        }
        List<UUID> chapterIds =
                pending.stream().map(NotificationRepository.Pending::chapterId).toList();
        List<UUID> seriesIds = pending.stream()
                .map(NotificationRepository.Pending::seriesId)
                .distinct()
                .toList();
        Map<UUID, SeriesCard> series = catalog.readableCardsByIds(seriesIds).stream()
                .collect(Collectors.toMap(SeriesCard::id, Function.identity()));
        List<SeriesChapters> groups = group(pending, series);
        if (groups.isEmpty()) {
            // Every series was taken down meanwhile: nothing to tell.
            repository.digestSent(userId, chapterIds, now);
            return false;
        }
        try {
            sender.send(render(contact, groups, preferences.unsubscribeToken(), site, brand.name()));
        } catch (RuntimeException e) {
            // Left queued: the next run tries again, until the chapters expire.
            log.warn("Could not send a new-chapter email: {}", e.toString());
            return false;
        }
        repository.digestSent(userId, chapterIds, now);
        return true;
    }

    record SeriesChapters(SeriesCard series, List<String> numbers) {}

    /** Pending chapters by series, in the order the series first appear; series readers can't open are left out. */
    static List<SeriesChapters> group(List<NotificationRepository.Pending> pending, Map<UUID, SeriesCard> series) {
        Map<UUID, List<String>> numbers = new LinkedHashMap<>();
        for (NotificationRepository.Pending chapter : pending) {
            if (series.containsKey(chapter.seriesId())) {
                numbers.computeIfAbsent(chapter.seriesId(), id -> new ArrayList<>())
                        .add(chapter.number());
            }
        }
        return numbers.entrySet().stream()
                .map(e -> new SeriesChapters(series.get(e.getKey()), e.getValue()))
                .toList();
    }

    static EmailMessage render(
            UserDirectory.Contact contact, List<SeriesChapters> groups, String token, String site, String brand) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(contact.displayName()).append(",\n\n");
        body.append("New chapters are out in series you follow on ")
                .append(brand)
                .append(":\n");
        for (SeriesChapters group : groups) {
            String seriesUrl = site + "/series/" + group.series().slug();
            body.append("\n").append(group.series().title()).append("\n");
            List<String> numbers = group.numbers();
            for (String number : numbers.subList(0, Math.min(numbers.size(), CHAPTERS_PER_SERIES))) {
                body.append("  Chapter ")
                        .append(number)
                        .append(": ")
                        .append(seriesUrl)
                        .append("/chapter/")
                        .append(number)
                        .append("\n");
            }
            if (numbers.size() > CHAPTERS_PER_SERIES) {
                body.append("  and ")
                        .append(numbers.size() - CHAPTERS_PER_SERIES)
                        .append(" more: ")
                        .append(seriesUrl)
                        .append("\n");
            }
        }
        body.append("\nYou get this email because you follow these series. ")
                .append("To stop new-chapter emails, open ")
                .append(site)
                .append("/unsubscribe?token=")
                .append(token)
                .append("\nor change what we send you in your account: ")
                .append(site)
                .append("/account\n");
        return new EmailMessage(
                contact.email(),
                subject(groups, brand),
                body.toString(),
                Map.of(
                        // RFC 8058 one-click unsubscribe: mail apps POST to this address.
                        "List-Unsubscribe",
                        "<" + site + "/api/v1/notifications/unsubscribe?token=" + token + ">",
                        "List-Unsubscribe-Post",
                        "List-Unsubscribe=One-Click"));
    }

    static String subject(List<SeriesChapters> groups, String brand) {
        if (groups.size() > 1) {
            return "New chapters in " + groups.size() + " series you follow on " + brand;
        }
        SeriesChapters only = groups.getFirst();
        int count = only.numbers().size();
        return count == 1
                ? only.series().title() + ": chapter " + only.numbers().getFirst() + " is out"
                : only.series().title() + ": " + count + " new chapters";
    }
}
