# 0005. Discovery owns home and rankings

**Status:** Accepted (2026-09-26)

## Context
The home page needs "popular today / this week / all time", but no module owned view counts. Putting ranking logic in `catalog` would make the core content module depend on traffic data and, later, on follows and ratings.

## Decision
- `discovery` owns `GET /api/v1/home`, rankings, trending and search. It reads series data from `catalog`'s public API.
- View beacons increment Redis sorted sets per day (`views:{yyyy-mm-dd}`). A roll-up job every few minutes writes `discovery.series_views_daily` and refreshes `discovery.series_stats` (views_today, views_7d, views_all, follows, ratings).
- Follows and ratings reach `series_stats` through events from `reading` and `engagement`.
- The `local` seeder writes fake stats so the rankings render in step 1.

## Consequences
- `catalog` stays free of traffic concerns, and the dependency direction is `discovery → catalog`, never the reverse.
- If Redis is lost, rankings lose at most one roll-up interval of views. Committed data is unaffected.
- Moving rankings to a dedicated analytics store later changes only `discovery`.
