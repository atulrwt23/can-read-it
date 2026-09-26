package com.canreadit.catalog.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.Genre;
import com.canreadit.catalog.SeriesCard;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.Cursors;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogService implements CatalogQueries {

    private final SeriesRepository series;
    private final SeriesCards cards;
    private final MediaUrls mediaUrls;

    CatalogService(SeriesRepository series, SeriesCards cards, MediaUrls mediaUrls) {
        this.series = series;
        this.cards = cards;
        this.mediaUrls = mediaUrls;
    }

    @Override
    public List<SeriesCard> latestUpdates(int limit) {
        return cards.toCards(series.latestUpdates(limit));
    }

    @Override
    public List<SeriesCard> cardsByIds(List<UUID> seriesIds) {
        Map<UUID, SeriesRow> byId =
                series.publishedByIds(seriesIds).stream().collect(Collectors.toMap(SeriesRow::id, Function.identity()));
        return cards.toCards(seriesIds.stream()
                .distinct()
                .filter(byId::containsKey)
                .map(byId::get)
                .toList());
    }

    @Override
    public List<UUID> publishedSeriesIds() {
        return series.publishedIds();
    }

    List<Genre> genres() {
        return series.allGenres();
    }

    CursorPage<SeriesCard> browse(BrowseRequest request) {
        BrowseQuery.After after = request.cursor() == null ? null : decode(request.cursor(), request.sort());
        List<SeriesRow> rows = series.browse(new BrowseQuery(
                request.sort(),
                request.type(),
                request.status(),
                request.q(),
                request.genres(),
                request.limit() + 1,
                after));
        boolean hasMore = rows.size() > request.limit();
        List<SeriesRow> page = hasMore ? rows.subList(0, request.limit()) : rows;
        String next = hasMore ? encode(page.getLast(), request.sort()) : null;
        return new CursorPage<>(cards.toCards(page), next);
    }

    SeriesDetail detail(SeriesRow row) {
        var cover = row.coverAssetId() == null
                ? null
                : mediaUrls
                        .resolve(List.of(row.coverAssetId()), ImageVariant.ORIGINAL)
                        .get(row.coverAssetId());
        return new SeriesDetail(
                row.id(),
                row.slug(),
                row.title(),
                row.altTitles(),
                row.synopsis(),
                row.type(),
                row.status(),
                row.ageRating(),
                series.genresBySeries(List.of(row.id())).getOrDefault(row.id(), List.of()),
                cover,
                row.firstReleasedYear(),
                row.releaseCadence(),
                row.lastPublishedAt(),
                series.editions(row.id()),
                series.chapterStats(row.id()));
    }

    private static String encode(SeriesRow last, BrowseSort sort) {
        String key = switch (sort) {
            case UPDATED -> (last.lastPublishedAt() == null ? last.createdAt() : last.lastPublishedAt()).toString();
            case NEWEST -> last.createdAt().toString();
            case TITLE -> last.title();
        };
        return Cursors.encode(sort.name(), key, last.id().toString());
    }

    private static BrowseQuery.After decode(String cursor, BrowseSort sort) {
        List<String> parts = Cursors.decode(cursor, 3);
        try {
            if (!parts.get(0).equals(sort.name())) {
                throw invalidCursor();
            }
            if (sort != BrowseSort.TITLE) {
                Instant.parse(parts.get(1));
            }
            return new BrowseQuery.After(parts.get(1), UUID.fromString(parts.get(2)));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    private static ApiException invalidCursor() {
        return ApiException.badRequest("invalid_cursor", "The cursor does not match this query.");
    }

    record BrowseRequest(
            BrowseSort sort,
            com.canreadit.catalog.@Nullable SeriesType type,
            com.canreadit.catalog.@Nullable SeriesStatus status,
            @Nullable String q,
            List<String> genres,
            int limit,
            @Nullable String cursor) {}
}
