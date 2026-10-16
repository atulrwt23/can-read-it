package com.canreadit.notifications.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.ChapterPublished;
import com.canreadit.catalog.ChapterRef;
import com.canreadit.identity.UserDeleted;
import com.canreadit.reading.ReadingQueries;
import com.canreadit.reading.SeriesUnfollowed;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Queues each released chapter for the readers who follow its series; {@link ChapterDigests} sends them. Readers who
 * turned the emails off are skipped, and unfollowing or deleting the account drops what was waiting.
 */
@Component
class NewChapterQueue {

    private final NotificationRepository repository;
    private final ReadingQueries reading;
    private final CatalogQueries catalog;
    private final Clock clock;

    NewChapterQueue(NotificationRepository repository, ReadingQueries reading, CatalogQueries catalog, Clock clock) {
        this.repository = repository;
        this.reading = reading;
        this.catalog = catalog;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(ChapterPublished event) {
        // Readers open a series' original edition, so translations don't send emails (yet).
        // TODO(product): notify per language once editions in several languages are readable.
        boolean original = catalog.visibleChapter(event.seriesId(), event.number())
                .map(ChapterRef::id)
                .filter(event.chapterId()::equals)
                .isPresent();
        if (!original) {
            return;
        }
        Set<UUID> readers = new HashSet<>(reading.followers(event.seriesId()));
        readers.removeAll(repository.optedOut(readers));
        repository.queue(
                readers, event.chapterId(), event.seriesId(), event.number(), event.publishedAt(), clock.instant());
    }

    @ApplicationModuleListener
    void on(SeriesUnfollowed event) {
        repository.deletePending(event.userId(), event.seriesId());
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        repository.forget(event.userId());
    }
}
