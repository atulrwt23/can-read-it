package com.canreadit.catalog.internal;

import com.canreadit.catalog.ChapterAccess;
import com.canreadit.catalog.ChapterSummary;
import com.canreadit.catalog.SeriesType;
import com.canreadit.media.ImageRef;
import com.canreadit.media.ImageVariant;
import com.canreadit.media.MediaUrls;
import com.canreadit.shared.ApiException;
import com.canreadit.shared.CursorPage;
import com.canreadit.shared.Cursors;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private final ChapterRepository chapters;
    private final MediaUrls mediaUrls;

    ChapterService(ChapterRepository chapters, MediaUrls mediaUrls) {
        this.chapters = chapters;
        this.mediaUrls = mediaUrls;
    }

    CursorPage<ChapterSummary> list(
            SeriesRow series, @Nullable String language, ChapterOrder order, @Nullable String cursor, int limit) {
        var edition = edition(series, language);
        BigDecimal after = cursor == null ? null : decode(cursor, order);
        List<ChapterSummary> rows = chapters.list(edition, order, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChapterSummary> page = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? Cursors.encode(order.name(), page.getLast().number()) : null;
        return new CursorPage<>(page, next);
    }

    ChapterContent content(SeriesRow series, @Nullable String language, String rawNumber) {
        var edition = edition(series, language);
        BigDecimal number = ChapterNumbers.parse(rawNumber);
        var chapter = chapters.findVisible(edition, number)
                .orElseThrow(() ->
                        ApiException.notFound("chapter_not_found", "This chapter does not exist or is not out yet."));
        var neighbours = chapters.neighbours(edition, number);
        ChapterSummary summary = chapter.summary();
        // TODO(phase 3): unlock early access for readers holding an entitlement (monetization).
        boolean locked = summary.access() == ChapterAccess.EARLY_ACCESS;

        List<ImageRef> pages = List.of();
        ChapterContent.NovelBody novel = null;
        if (!locked && series.type() == SeriesType.MANHWA) {
            pages = pages(chapter.id());
        } else if (!locked) {
            novel = chapters.novelBody(chapter.id()).orElse(null);
        }
        return new ChapterContent(
                new ChapterContent.SeriesRef(series.slug(), series.title(), series.type()),
                summary.number(),
                summary.title(),
                summary.publishedAt(),
                summary.access(),
                locked,
                pages,
                novel,
                neighbours.previous(),
                neighbours.next());
    }

    /** Page URLs come from media; sizes from the page rows, so they are known before images load. */
    private List<ImageRef> pages(java.util.UUID chapterId) {
        var rows = chapters.pages(chapterId);
        Map<java.util.UUID, ImageRef> urls = mediaUrls.resolve(
                rows.stream().map(ChapterRepository.PageRow::assetId).toList(), ImageVariant.ORIGINAL);
        if (urls.size()
                < rows.stream()
                        .map(ChapterRepository.PageRow::assetId)
                        .distinct()
                        .count()) {
            log.warn("Chapter {} has pages whose assets are not ready; they are left out", chapterId);
        }
        return rows.stream()
                .filter(row -> urls.containsKey(row.assetId()))
                .map(row -> new ImageRef(urls.get(row.assetId()).url(), row.width(), row.height()))
                .toList();
    }

    private java.util.UUID edition(SeriesRow series, @Nullable String language) {
        return chapters.findEdition(series.id(), language)
                .orElseThrow(() ->
                        ApiException.notFound("edition_not_found", "This series has no edition in that language."));
    }

    private static BigDecimal decode(String cursor, ChapterOrder order) {
        List<String> parts = Cursors.decode(cursor, 2);
        if (!parts.get(0).equals(order.name())) {
            throw ApiException.badRequest("invalid_cursor", "The cursor does not match this query.");
        }
        try {
            return ChapterNumbers.parse(parts.get(1));
        } catch (ApiException e) {
            throw ApiException.badRequest("invalid_cursor", "The cursor is malformed.");
        }
    }
}
