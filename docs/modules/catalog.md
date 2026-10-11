# catalog

**Purpose.** Owns the content model: series, editions (one per language), chapters, chapter pages, novel bodies, genres and per-series members. Serves the public read API for series and chapters. It does not own traffic data (see [discovery](discovery.md)) or image storage (see [media](media.md)).

## Public API (`com.canreadit.catalog`)
- `CatalogQueries`: `latestUpdates(limit)`, `cardsByIds(ids)` (keeps order, drops unpublished), `publishedSeriesIds()`, `readableSeriesId(slug)` (published or unlisted)
- `SeriesPopularity`: `update(sevenDayViews)`, called by discovery's roll-up; stored in `series.popularity` for the "Popular" sort
- Records and enums: `SeriesCard`, `ChapterSummary`, `Genre`, `SeriesType`, `SeriesStatus`, `AgeRating`, `ChapterAccess`
- Events: `ChapterPublished(seriesId, seriesSlug, chapterId, number, language, publishedAt)`, for chapters of published or unlisted series

## Data (schema `catalog`)
`series`, `series_slug_history`, `genres`, `series_genres`, `editions`, `chapters`, `chapter_pages`, `novel_chapter_bodies`, `series_members`. See the migration `V202609261203__catalog_core.sql`.

- **Series visibility:** `PUBLISHED` is listed; `UNLISTED` works by link; `DRAFT` answers 404; `REMOVED` answers 410. Old slugs answer 301 to the same path under the current slug.
- **Chapter visibility (ADR 0006):** `status <> 'DRAFT' and publish_at <= now()`, applied in every chapter query (`SeriesRepository.VISIBLE_CHAPTER`).
- **Scheduled publishing:** `ChapterPublisher` runs every minute (`app.catalog.publish-interval`). It locks due `SCHEDULED` chapters (`for update skip locked`, so several instances never publish one twice), sets `status = PUBLISHED` and `published_at = publish_at`, raises `series.last_published_at` for original-edition chapters, and publishes `ChapterPublished` through the Modulith outbox. Visibility never waits for it.
- **Search:** case-insensitive substring match on title and alt titles (served by a trigram index over `catalog.search_text(title, alt_titles)`), plus whole-word full-text on `synopsis` (`simple` config: no stemming). It does not tolerate typos yet.
- Read queries use `JdbcClient` with keyset pagination; cursors are opaque (`shared.Cursors`).

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/genres` | cached 1 h at the CDN |
| `GET /api/v1/series` | `q`, `type`, `status`, `genres` (all must match), `sort` (`UPDATED`, `NEWEST`, `TITLE`, `POPULAR`: 7-day views), `cursor`, `limit` ≤ 50 |
| `GET /api/v1/series/{slug}` | detail, editions, visible-chapter stats |
| `GET /api/v1/series/{slug}/chapters` | `lang`, `order` (`DESC`, `ASC`), `cursor`, `limit` ≤ 200 |
| `GET /api/v1/series/{slug}/chapters/{number}` | pages (URL + size) or novel Markdown, `previous`/`next`; early access is returned `locked` without content |

## Local seed
`LocalCatalogSeeder` (profile `local`, empty catalog only) creates 16 invented series with generated PNG covers and pages, stored through `media.MediaUploads`.

## Open questions
- `TODO(product)`: which language edition to show by default when there are several (today: the original).
- `TODO(phase 3)`: unlocking early-access chapters (entitlements from monetization).
