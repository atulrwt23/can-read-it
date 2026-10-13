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
- **Search:** a series matches when its title or alt titles contain the text (case-insensitive), are close to it word by word (pg_trgm `<%`, word similarity ≥ 0.6, so one typo per word still matches: "towr climber" finds "The Tower Climber"), or its synopsis has all the words (`simple` full-text config, no stemming). All three use indexes (the trigram index over `catalog.search_text(title, alt_titles)` and a GIN index on the synopsis). Results come best match first by default: a substring match scores 1, a near miss its word similarity, a synopsis-only match 0.5.
- Read queries use `JdbcClient` with keyset pagination; cursors are opaque (`shared.Cursors`).

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/genres` | cached 1 h at the CDN |
| `GET /api/v1/series` | `q`, `type`, `status`, `genres` (all must match), `sort` (`RELEVANCE`: the default with `q`; `UPDATED`: the default without; `NEWEST`, `TITLE`, `POPULAR`: 7-day views), `cursor`, `limit` ≤ 50. The first page (no cursor) also carries `total`, the number of matches |
| `GET /api/v1/series/{slug}` | detail, editions, visible-chapter stats |
| `GET /api/v1/series/{slug}/chapters` | `lang`, `order` (`DESC`, `ASC`), `cursor`, `limit` ≤ 200 |
| `GET /api/v1/series/{slug}/chapters/{number}` | pages (URL + size) or novel Markdown, `previous`/`next`, and the series' slug, title, type and cover; early access is returned `locked` without content |

## Staff editing (step 3)
`CatalogAdminController` and `CatalogAdminService`, permission `CATALOG_MANAGE` on every route. Responses are `no-store`; writes need the CSRF header like every cookie write. The write side uses JPA entities (`SeriesEntity`, `ChapterEntity` with its pages as an ordered element collection, `NovelBodyEntity`) with `@DynamicUpdate`, so an edit never overwrites columns the publish job or the popularity roll-up maintain.

| Route | Notes |
|---|---|
| `GET /api/v1/admin/series` | every series, drafts included, newest edit first; `q` (title, alt titles, slug), `offset`, `limit` ≤ 100; answers `items` and `total` |
| `POST /api/v1/admin/series` | creates a series and its original edition (`en`). The slug comes from the title when left out (`title`, `title-2`, …; `series` for titles without Latin letters); a taken one is `409 slug_taken` |
| `GET`, `PUT /api/v1/admin/series/{id}` | the whole form: title, slug, type (fixed: `series_type_fixed`), status, visibility, age rating, synopsis, alt titles, genre slugs (`unknown_genre`), cover asset (must be a ready cover: `invalid_cover`), first year, cadence. A new slug keeps the old one as a 301 |
| `POST /api/v1/admin/series/{id}/chapters` | a draft chapter `{number, title}`; a duplicate number is `409 chapter_number_taken` |
| `GET`, `PUT /api/v1/admin/chapters/{id}` | renames; the number can change only until the chapter is live (`409 chapter_live`), because URLs carry it |
| `PUT /api/v1/admin/chapters/{id}/pages` | manhwa only (`not_manhwa`): uploaded page assets in reading order, up to 300 (`invalid_page` names the first bad one); widths and heights come from `media` |
| `PUT /api/v1/admin/chapters/{id}/body` | novels only (`not_novel`): Markdown up to 200,000 characters; the word count is stored |
| `POST /api/v1/admin/chapters/{id}/schedule` | `{publishAt?}`; none, or a time that has passed, releases now and runs the publish job straight away. Needs pages or text (`chapter_empty`) and refuses chapters already live (`chapter_live`) |
| `POST /api/v1/admin/chapters/{id}/unschedule` | back to draft while not yet live |
| `DELETE /api/v1/admin/chapters/{id}` | drafts and not-yet-live scheduled chapters only |

Chapter writes lock the row (`select … for update`). The publish job skips locked rows, so it can't release a chapter while staff change its schedule, and staff can't pull back a chapter the job has just released.

## Local seed
`LocalCatalogSeeder` (profile `local`, empty catalog only) creates 16 invented series with generated covers and pages, stored through `media.MediaUploads` (so they go through the WebP pipeline; about 90 s on a laptop).

## Open questions
- `TODO(product)`: creators will manage their own series through `series_members` (phase 3); today only `ADMIN` can edit.
- `TODO(product)`: edits to series or chapters readers can already see reach cached pages at the next time-based revalidation (about a minute); only releases revalidate on demand.
- `TODO(product)`: every new series gets an `en` original edition until launch languages are decided.
- `TODO(product)`: taking down a released chapter (moderation, rights) is a separate flow; staff can only delete chapters readers never saw.
- `TODO(product)`: which language edition to show by default when there are several (today: the original).
- `TODO(phase 3)`: unlocking early-access chapters (entitlements from monetization).
