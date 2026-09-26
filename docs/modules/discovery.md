# discovery

**Purpose.** Owns the home feed and rankings ([ADR 0005](../decisions/0005-discovery-owns-home-and-rankings.md)). Reads series through `catalog.CatalogQueries`, never catalog tables.

## Endpoints
`GET /api/v1/home`: `featured`, `latestUpdates` and `popular.{today,week,allTime}` (rank, views, series card). Cached 60 s at the CDN.

## Data (schema `discovery`)
- `series_stats`: views today / 7 days / all time, follows, ratings (a read model)
- `series_views_daily`: per-day views, filled by the roll-up job (not built yet)

## Local seed
`LocalDiscoverySeeder` writes fake stats for every published series so rankings render.

## Open questions
- `TODO(product)`: editorial curation for featured (today: the week's most viewed, falling back to latest updates).
- Step 5: view beacons into Redis per-day sorted sets and the roll-up job into `series_views_daily` and `series_stats`.
