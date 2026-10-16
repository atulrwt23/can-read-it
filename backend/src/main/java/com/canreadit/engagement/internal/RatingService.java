package com.canreadit.engagement.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.shared.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Series ratings: one score from 1 to 5 per reader and series, and each series' average. */
@Service
class RatingService {

    private final RatingRepository repository;
    private final CatalogQueries catalog;
    private final Clock clock;

    RatingService(RatingRepository repository, CatalogQueries catalog, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * @param average the mean score to two decimals, or null before the first rating
     * @param count how many readers rated the series
     */
    record SeriesRating(@Nullable BigDecimal average, int count) {}

    /** @param score the reader's score, or null if they haven't rated the series */
    record MyRating(@Nullable Integer score, SeriesRating series) {}

    @Transactional(readOnly = true)
    SeriesRating seriesRating(String slug) {
        return summary(seriesId(slug));
    }

    @Transactional(readOnly = true)
    MyRating myRating(UUID userId, String slug) {
        UUID seriesId = seriesId(slug);
        return new MyRating(repository.score(userId, seriesId).orElse(null), summary(seriesId));
    }

    @Transactional
    MyRating rate(UUID userId, String slug, int score) {
        UUID seriesId = seriesId(slug);
        repository.rate(userId, seriesId, score, clock.instant());
        return new MyRating(score, summary(seriesId));
    }

    @Transactional
    MyRating remove(UUID userId, String slug) {
        UUID seriesId = seriesId(slug);
        repository.remove(userId, seriesId, clock.instant());
        return new MyRating(null, summary(seriesId));
    }

    @Transactional
    void forget(UUID userId) {
        repository.forget(userId, clock.instant());
    }

    private SeriesRating summary(UUID seriesId) {
        return summary(repository.totals(seriesId));
    }

    static SeriesRating summary(RatingRepository.Totals totals) {
        if (totals.ratings() == 0) {
            return new SeriesRating(null, 0);
        }
        BigDecimal average = BigDecimal.valueOf(totals.scoreSum())
                .divide(BigDecimal.valueOf(totals.ratings()), 2, RoundingMode.HALF_UP);
        return new SeriesRating(average, totals.ratings());
    }

    /** Only series readers can open (published or unlisted) can be rated. */
    private UUID seriesId(String slug) {
        return catalog.readableSeriesId(slug)
                .orElseThrow(() -> ApiException.notFound("series_not_found", "No series has this slug."));
    }
}
