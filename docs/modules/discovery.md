# discovery

**Purpose.** Owns the home feed, rankings and view counting ([ADR 0005](../decisions/0005-discovery-owns-home-and-rankings.md)). Reads series through `catalog.CatalogQueries`, never catalog tables.

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/home` | `featured`, `latestUpdates` and `popular.{today,week,allTime}` (rank, views, series card). Cached 60 s at the CDN |
| `POST /api/v1/series/{slug}/views` | the reader's view beacon. Anonymous, no CSRF token, `204` (`404` for a series readers can't open, `429` when rate limited) |

## Counting views
1. The chapter page sends a beacon (`navigator.sendBeacon`) when it opens.
2. `ViewCounter` drops crawlers (by user agent), then counts the view once per **visitor, series and UTC day** in the Redis sorted set `views:<day>`. The visitor is an HMAC of IP and user agent with a random salt that changes daily (`shared.Visitors`, also used by [analytics](analytics.md)), so it can't be reversed or linked across days.
3. Changing the user agent makes a new visitor, so one IP counts at most 30 visitors per series and day (`TODO(product)`: tune for shared mobile addresses).
4. The beacon is rate limited to 120 requests a minute per IP (`shared.RateLimiter`).
5. `ViewRollup` (every 5 minutes, `app.discovery.rollup-interval`) copies yesterday's and today's counts into `series_views_daily`, recomputes `series_stats`, and hands each series' 7-day views to `catalog.SeriesPopularity`, which browse's "Popular" sort uses. Each step writes absolute values, so a late, repeated or concurrent run changes nothing.

If Redis is down, views are not counted and requests are not limited; nothing fails.

## Keeping cached pages fresh
`WebCacheRevalidator` listens for `catalog.ChapterPublished` and asks the web app (`app.web.revalidate-url`, bearer `app.web.revalidate-secret`) to refresh the `home`, `series-lists` and `series:<slug>` cache tags. Without those settings, pages refresh on their timers.

## Data (schema `discovery`)
- `series_stats`: views today / 7 days / all time, follows, ratings (a read model)
- `series_views_daily`: views per series and day

## Local seed
`LocalDiscoverySeeder` writes 30 days of invented daily views plus follows and ratings for every published series, then recomputes the stats, so rankings and "Popular" render.

## Open questions
- `TODO(product)`: editorial curation for featured (today: the week's most viewed, falling back to latest updates).
- `TODO(product)`: "today" is the UTC day; launch markets may want a local day.
