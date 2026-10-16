package com.canreadit.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.canreadit.catalog.AgeRating;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.catalog.SeriesStatus;
import com.canreadit.catalog.SeriesType;
import com.canreadit.identity.UserDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChapterDigestsTest {

    static final UserDirectory.Contact READER = new UserDirectory.Contact(UUID.randomUUID(), "r@example.com", "Mina");

    @Test
    void namesTheChapterWhenThereIsOnlyOne() {
        var groups = List.of(new ChapterDigests.SeriesChapters(card("tower", "Tower"), List.of("12.5")));
        assertThat(ChapterDigests.subject(groups, "CanReadIt")).isEqualTo("Tower: chapter 12.5 is out");

        var two = List.of(new ChapterDigests.SeriesChapters(card("tower", "Tower"), List.of("12", "13")));
        assertThat(ChapterDigests.subject(two, "CanReadIt")).isEqualTo("Tower: 2 new chapters");
    }

    @Test
    void listsFiveChaptersPerSeriesThenLinksTheSeries() {
        List<String> numbers =
                IntStream.rangeClosed(1, 7).mapToObj(String::valueOf).toList();
        var groups = List.of(new ChapterDigests.SeriesChapters(card("tower", "Tower"), numbers));

        var email = ChapterDigests.render(READER, groups, "tok", "https://example.test", "CanReadIt");

        assertThat(email.to()).isEqualTo("r@example.com");
        assertThat(email.text())
                .startsWith("Hi Mina,")
                .contains("  Chapter 5: https://example.test/series/tower/chapter/5\n")
                .doesNotContain("Chapter 6:")
                .contains("  and 2 more: https://example.test/series/tower\n")
                .contains("https://example.test/unsubscribe?token=tok");
        assertThat(email.headers())
                .containsEntry("List-Unsubscribe", "<https://example.test/api/v1/notifications/unsubscribe?token=tok>");
    }

    @Test
    void skipsSeriesReadersCanNoLongerOpen() {
        var open = card("tower", "Tower");
        UUID removed = UUID.randomUUID();
        var pending = List.of(
                new NotificationRepository.Pending(READER.userId(), UUID.randomUUID(), removed, "3", Instant.now()),
                new NotificationRepository.Pending(READER.userId(), UUID.randomUUID(), open.id(), "1", Instant.now()));

        var groups = ChapterDigests.group(pending, Map.of(open.id(), open));

        assertThat(groups)
                .singleElement()
                .satisfies(g -> assertThat(g.numbers()).containsExactly("1"));
    }

    @Test
    void siteUrlLosesItsTrailingSlash() {
        assertThat(properties("https://example.test/").site()).isEqualTo("https://example.test");
        assertThat(properties(" ").site()).isNull();
    }

    private static NotificationsProperties properties(String site) {
        return new NotificationsProperties(site, null, null, null);
    }

    private static SeriesCard card(String slug, String title) {
        return new SeriesCard(
                UUID.randomUUID(),
                slug,
                title,
                SeriesType.MANHWA,
                SeriesStatus.ONGOING,
                AgeRating.ALL,
                List.of(),
                null,
                "",
                null,
                List.of());
    }
}
