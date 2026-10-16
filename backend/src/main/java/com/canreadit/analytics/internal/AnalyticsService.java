package com.canreadit.analytics.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.shared.Visitors;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records anonymous events and counts them for staff. Events about a series store its ID, so counts survive slug
 * changes. Each recorded event also increments the Prometheus counter {@code canreadit_analytics_events_total}.
 *
 * <p>TODO(product): a privacy notice that describes these events, before the beta opens to the public.
 */
@Service
class AnalyticsService {

    static final int TOP_SERIES = 10;
    static final Duration TOP_SERIES_WINDOW = Duration.ofDays(7);

    private final AnalyticsRepository repository;
    private final CatalogQueries catalog;
    private final Visitors visitors;
    private final MeterRegistry meters;
    private final Clock clock;

    AnalyticsService(
            AnalyticsRepository repository,
            CatalogQueries catalog,
            Visitors visitors,
            MeterRegistry meters,
            Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.visitors = visitors;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Records what a browser sent and returns how many events were kept. Crawlers are ignored, as are events about
     * series readers can't open and events only the server may record.
     */
    @Transactional
    int record(List<AnalyticsRequests.ClientEvent> events, String clientIp, @Nullable String userAgent) {
        if (Visitors.isBot(userAgent)) {
            return 0;
        }
        Instant now = clock.instant();
        String visitor = visitors.fingerprint(LocalDate.ofInstant(now, ZoneOffset.UTC), clientIp + "\n" + userAgent)
                .orElse(null);
        Map<String, Optional<UUID>> series = new HashMap<>();
        List<AnalyticsRepository.Event> kept = new ArrayList<>();
        for (AnalyticsRequests.ClientEvent event : events) {
            if (!event.name().fromBrowser()) {
                continue;
            }
            UUID seriesId = null;
            if (event.name().aboutSeries()) {
                if (event.series() == null) {
                    continue;
                }
                Optional<UUID> found = series.computeIfAbsent(event.series(), catalog::readableSeriesId);
                if (found.isEmpty()) {
                    continue;
                }
                seriesId = found.get();
            }
            boolean chapterEvent = event.name() == EventName.CHAPTER_OPEN || event.name() == EventName.CHAPTER_FINISH;
            kept.add(new AnalyticsRepository.Event(
                    event.name(),
                    now,
                    visitor,
                    event.path(),
                    seriesId,
                    chapterEvent ? event.chapter() : null,
                    event.name() == EventName.SEARCH ? event.value() : null));
        }
        save(kept);
        return kept.size();
    }

    /** An event the API itself observed, such as a sign-up. No visitor: it is not tied to a browser. */
    @Transactional
    void recordServerEvent(EventName name, Instant at) {
        save(List.of(new AnalyticsRepository.Event(name, at, null, null, null, null, null)));
    }

    /** Daily counts for the last {@code days} days (today included, counted live) and the most opened series. */
    @Transactional(readOnly = true)
    AnalyticsViews.AnalyticsSummary summary(int days) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Map<String, AnalyticsViews.EventCount>> byDay = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            byDay.put(day, new LinkedHashMap<>());
        }
        List<AnalyticsRepository.DayCount> counts = new ArrayList<>(repository.dailyCounts(from, today));
        counts.addAll(repository.liveCounts(today));
        for (AnalyticsRepository.DayCount count : counts) {
            byDay.get(count.day()).put(count.name(), new AnalyticsViews.EventCount(count.events(), count.visitors()));
        }

        List<AnalyticsRepository.SeriesCount> top =
                repository.topSeries(EventName.CHAPTER_OPEN, now.minus(TOP_SERIES_WINDOW), TOP_SERIES);
        Map<UUID, SeriesCard> cards =
                catalog
                        .readableCardsByIds(top.stream()
                                .map(AnalyticsRepository.SeriesCount::seriesId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(SeriesCard::id, Function.identity()));
        List<AnalyticsViews.TopSeries> topSeries = top.stream()
                .filter(count -> cards.containsKey(count.seriesId()))
                .map(count -> {
                    SeriesCard card = cards.get(count.seriesId());
                    return new AnalyticsViews.TopSeries(card.slug(), card.title(), count.events(), count.visitors());
                })
                .toList();

        return new AnalyticsViews.AnalyticsSummary(
                Arrays.stream(EventName.values()).map(Enum::name).toList(),
                byDay.entrySet().stream()
                        .map(entry -> new AnalyticsViews.DayCounts(entry.getKey(), entry.getValue()))
                        .toList(),
                topSeries);
    }

    private void save(List<AnalyticsRepository.Event> events) {
        if (events.isEmpty()) {
            return;
        }
        repository.insert(events);
        for (AnalyticsRepository.Event event : events) {
            meters.counter("canreadit.analytics.events", "name", event.name().name())
                    .increment();
        }
    }
}
