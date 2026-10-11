package com.canreadit.catalog.internal;

import com.canreadit.catalog.ChapterPublished;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes scheduled chapters whose time has come (ADR 0006). Readers can already see such a chapter, because
 * visibility only depends on {@code publish_at}; this job records the release: it sets the status and {@code
 * published_at}, moves the series up in "latest updates", and announces {@link ChapterPublished}. It is safe to run
 * on several API instances at once ({@code skip locked}) and to run late.
 */
@Component
class ChapterPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChapterPublisher.class);

    /** Chapters handled per run; a backlog drains over the following runs. */
    static final int BATCH = 200;

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    ChapterPublisher(JdbcClient jdbc, ApplicationEventPublisher events, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.events = events;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.catalog.publish-interval:PT1M}", initialDelayString = "PT30S")
    void run() {
        int published = publishDue();
        if (published > 0) {
            log.info("Published {} scheduled chapter(s)", published);
        }
    }

    /**
     * Publishes due chapters and returns how many. Status changes and events commit together, so the event
     * publication registry delivers each event exactly when its chapter is published.
     */
    int publishDue() {
        Integer count = tx.execute(status -> publishDueInTransaction());
        return count == null ? 0 : count;
    }

    private int publishDueInTransaction() {
        List<Released> released = jdbc.sql("""
                        with due as (
                            select id from catalog.chapters
                            where status = 'SCHEDULED' and publish_at <= now()
                            order by publish_at, id
                            limit :batch
                            for update skip locked
                        )
                        update catalog.chapters c
                        set status = 'PUBLISHED', published_at = c.publish_at, updated_at = now()
                        from due, catalog.editions e, catalog.series s
                        where c.id = due.id and e.id = c.edition_id and s.id = e.series_id
                        returning c.id, c.number, c.publish_at, e.series_id, e.language, e.is_original, s.slug,
                                  s.visibility
                        """)
                .param("batch", BATCH)
                .query((rs, n) -> new Released(
                        new ChapterPublished(
                                rs.getObject("series_id", java.util.UUID.class),
                                rs.getString("slug"),
                                rs.getObject("id", java.util.UUID.class),
                                ChapterNumbers.format(rs.getBigDecimal("number")),
                                rs.getString("language"),
                                rs.getObject("publish_at", OffsetDateTime.class).toInstant()),
                        rs.getBoolean("is_original"),
                        rs.getString("visibility")))
                .list();

        for (Released chapter : released) {
            ChapterPublished event = chapter.event();
            // "Latest updates" follows the original edition, like the chapter lists on series cards.
            if (chapter.original()) {
                jdbc.sql("""
                                update catalog.series
                                set last_published_at = greatest(coalesce(last_published_at, :at), :at),
                                    updated_at = now()
                                where id = :id
                                """)
                        .param("id", event.seriesId())
                        .param("at", OffsetDateTime.ofInstant(event.publishedAt(), ZoneOffset.UTC))
                        .update();
            }
            // Drafts and removed series stay quiet: nobody can read them.
            if (chapter.visibility().equals("PUBLISHED") || chapter.visibility().equals("UNLISTED")) {
                events.publishEvent(event);
            }
        }
        return released.size();
    }

    private record Released(ChapterPublished event, boolean original, String visibility) {}
}
