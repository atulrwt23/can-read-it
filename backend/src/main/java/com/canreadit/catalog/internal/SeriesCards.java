package com.canreadit.catalog.internal;

import com.canreadit.catalog.SeriesCard;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUrls;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Turns series rows into cards with a fixed number of queries, whatever the list size. */
@Component
class SeriesCards {

    static final int LATEST_CHAPTERS_PER_CARD = 3;

    private final SeriesRepository series;
    private final MediaUrls mediaUrls;

    SeriesCards(SeriesRepository series, MediaUrls mediaUrls) {
        this.series = series;
        this.mediaUrls = mediaUrls;
    }

    List<SeriesCard> toCards(List<SeriesRow> rows) {
        List<UUID> ids = rows.stream().map(SeriesRow::id).toList();
        var genres = series.genresBySeries(ids);
        var chapters = series.latestChapters(ids, LATEST_CHAPTERS_PER_CARD);
        Map<UUID, ImageRef> covers = mediaUrls.resolve(
                rows.stream()
                        .map(SeriesRow::coverAssetId)
                        .filter(Objects::nonNull)
                        .toList(),
                ImageVariant.ORIGINAL);
        return rows.stream()
                .map(row -> new SeriesCard(
                        row.id(),
                        row.slug(),
                        row.title(),
                        row.type(),
                        row.status(),
                        row.ageRating(),
                        genres.getOrDefault(row.id(), List.of()),
                        row.coverAssetId() == null ? null : covers.get(row.coverAssetId()),
                        row.synopsis(),
                        row.lastPublishedAt(),
                        chapters.getOrDefault(row.id(), List.of())))
                .toList();
    }
}
