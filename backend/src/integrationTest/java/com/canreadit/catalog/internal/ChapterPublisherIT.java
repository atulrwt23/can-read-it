package com.canreadit.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.TestcontainersConfiguration;
import com.canreadit.catalog.CatalogFixtures;
import com.canreadit.catalog.ChapterPublished;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ChapterPublisherIT {

    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    ChapterPublisher publisher;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    CatalogFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new CatalogFixtures(jdbc);
        fx.reset();
    }

    @Test
    void publishesDueChaptersOnceAndMovesTheSeriesUp() {
        var series = fx.series("tower")
                .lastPublishedAt(NOW.minus(7, ChronoUnit.DAYS))
                .create();
        Instant dueAt = NOW.minus(5, ChronoUnit.MINUTES);
        UUID due = fx.chapter(series.editionId(), "2", "SCHEDULED", dueAt, "FREE");
        UUID future = fx.chapter(series.editionId(), "3", "SCHEDULED", NOW.plus(1, ChronoUnit.DAYS), "FREE");
        UUID draft = fx.chapter(series.editionId(), "4", "DRAFT", null, "FREE");

        assertThat(publisher.publishDue()).isEqualTo(1);

        assertThat(status(due)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(due)).isEqualTo(dueAt);
        assertThat(status(future)).isEqualTo("SCHEDULED");
        assertThat(status(draft)).isEqualTo("DRAFT");
        assertThat(lastPublishedAt(series.id())).isEqualTo(dueAt);
        assertThat(events.stream(ChapterPublished.class))
                .containsExactly(new ChapterPublished(series.id(), "tower", due, "2", "en", dueAt));

        // Nothing left to do: running again publishes and announces nothing.
        assertThat(publisher.publishDue()).isZero();
        assertThat(events.stream(ChapterPublished.class)).hasSize(1);
    }

    @Test
    void neverMovesLatestUpdatesBackwards() {
        Instant newer = NOW.minus(1, ChronoUnit.MINUTES);
        var series = fx.series("late").lastPublishedAt(newer).create();
        fx.chapter(series.editionId(), "1", "SCHEDULED", NOW.minus(2, ChronoUnit.DAYS), "FREE");

        assertThat(publisher.publishDue()).isEqualTo(1);
        assertThat(lastPublishedAt(series.id())).isEqualTo(newer);
    }

    @Test
    void staysQuietForSeriesNobodyCanRead() {
        var draft = fx.series("draft-series").visibility("DRAFT").create();
        fx.chapter(draft.editionId(), "1", "SCHEDULED", NOW.minus(1, ChronoUnit.HOURS), "FREE");

        assertThat(publisher.publishDue()).isEqualTo(1);
        assertThat(events.stream(ChapterPublished.class)).isEmpty();
    }

    private String status(UUID chapter) {
        return jdbc.sql("select status from catalog.chapters where id = :id")
                .param("id", chapter)
                .query(String.class)
                .single();
    }

    private Instant publishedAt(UUID chapter) {
        return jdbc.sql("select published_at from catalog.chapters where id = :id")
                .param("id", chapter)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }

    private Instant lastPublishedAt(UUID series) {
        return jdbc.sql("select last_published_at from catalog.series where id = :id")
                .param("id", series)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }
}
