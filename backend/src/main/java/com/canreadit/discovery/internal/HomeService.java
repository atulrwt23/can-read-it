package com.canreadit.discovery.internal;

import com.canreadit.catalog.CatalogQueries;
import com.canreadit.catalog.SeriesCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
class HomeService {

    static final int FEATURED = 5;
    static final int LATEST = 12;
    static final int RANKED = 10;

    private final CatalogQueries catalog;
    private final SeriesStatsRepository stats;

    HomeService(CatalogQueries catalog, SeriesStatsRepository stats) {
        this.catalog = catalog;
        this.stats = stats;
    }

    HomeResponse home() {
        // Over-fetch rankings: series that stopped being published are dropped below.
        var today = stats.top(RankingPeriod.TODAY, RANKED * 2);
        var week = stats.top(RankingPeriod.WEEK, RANKED * 2);
        var allTime = stats.top(RankingPeriod.ALL_TIME, RANKED * 2);
        List<UUID> ids = Stream.of(today, week, allTime)
                .flatMap(List::stream)
                .map(SeriesStatsRepository.Ranked::seriesId)
                .distinct()
                .toList();
        Map<UUID, SeriesCard> cards =
                catalog.cardsByIds(ids).stream().collect(Collectors.toMap(SeriesCard::id, Function.identity()));

        List<SeriesCard> latest = catalog.latestUpdates(LATEST);
        // TODO(product): editorial curation for featured; for now the week's most viewed.
        List<SeriesCard> featured = week.stream()
                .map(r -> cards.get(r.seriesId()))
                .filter(java.util.Objects::nonNull)
                .limit(FEATURED)
                .toList();
        if (featured.isEmpty()) {
            featured = latest.stream().limit(FEATURED).toList();
        }
        return new HomeResponse(
                featured,
                latest,
                new HomeResponse.Popular(rank(today, cards), rank(week, cards), rank(allTime, cards)));
    }

    private static List<HomeResponse.RankedSeries> rank(
            List<SeriesStatsRepository.Ranked> ranking, Map<UUID, SeriesCard> cards) {
        List<HomeResponse.RankedSeries> ranked = new ArrayList<>();
        for (var entry : ranking) {
            SeriesCard card = cards.get(entry.seriesId());
            if (card != null && ranked.size() < RANKED) {
                ranked.add(new HomeResponse.RankedSeries(ranked.size() + 1, entry.views(), card));
            }
        }
        return ranked;
    }
}
