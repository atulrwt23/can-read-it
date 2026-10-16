# engagement

**Purpose.** What readers say about series: ratings now, comments and reports later in phase 2. The module does not own series (see [catalog](catalog.md)); it stores their IDs.

## Public API (`com.canreadit.engagement`)
Nothing yet: other modules don't read it. `discovery.series_stats` has rating columns for when rankings use ratings; an event (for example `SeriesRated`) will feed them then.

**Uses:** `catalog.CatalogQueries.readableSeriesId(slug)`. Only series readers can open (published or unlisted) can be rated.

**Listens to:** `identity.UserDeleted`. `EngagementDataEraser` deletes the reader's ratings and takes them out of each series' totals.

## Ratings
- **Scores.** One score from 1 to 5 per reader and series. Rating again replaces the score, and readers can remove it.
- **Who can rate.** Rating needs a confirmed email: the access token's `ev` claim, otherwise `403 email_not_confirmed`. Ratings are public, so this makes every throwaway account cost a real inbox. `TODO(product)`: CLAUDE.md section 7 names only commenting and purchasing.
- **Average.** The average is the plain mean, to two decimals, with no minimum number of ratings. `TODO(product)`: a minimum count, or a weighted (Bayesian) average, before ratings drive rankings.
- **Totals.** `series_ratings` keeps a count and a sum per series. They change in the same transaction as the score: a new rating adds one and its score, a change adds the difference, and a removal subtracts it. A series page reads one row and never scans the ratings.
- **Placeholder data.** `LocalEngagementSeeder` (`local` and `demo` profiles) gives each seeded series placeholder totals. It runs only while no series has ratings. Real ratings add to those totals.

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/series/{slug}/rating` | Public, the same for everyone: `{average, count}`; `average` is null before the first rating. CDN-cacheable for a minute (`PublicCache`). `404` for a series readers can't open |
| `GET /api/v1/me/ratings/{slug}` | Signed in: `{score, series: {average, count}}`, with `score` null when the reader hasn't rated it |
| `PUT /api/v1/me/ratings/{slug}` | Signed in, CSRF, confirmed email. Body `{score}` (1–5). Answers like `GET`, with the new average |
| `DELETE /api/v1/me/ratings/{slug}` | Signed in, CSRF. Removes the reader's score (no score: no change) |

The `me` routes are `no-store`. Writes are limited to 30 a minute per reader.

## Data (schema `engagement`)
Migration `V202610161800__engagement_ratings.sql`. There are no foreign keys to other schemas.

| Table | Key | Notes |
|---|---|---|
| `ratings` | (user_id, series_id) | `score` 1–5 (`smallint`, checked), `created_at`, `updated_at` |
| `series_ratings` | series_id | `ratings` (count) and `score_sum`, neither below zero |

## Web
- **Series banner.** The average and the number of ratings (`3.4 (748 ratings)`) are rendered on the server, the same for every visitor. If the rating can't be loaded, the page renders without it.
- **`SeriesRating` client component.** It loads after hydration and shows:
  - signed-out visitors: "Sign in to rate this series"
  - readers with an unconfirmed email: a link to confirm it
  - everyone else: five stars as a native radio group ("Your rating"), so arrow keys work, plus "Remove rating"
- **After a change,** the average updates from the API's answer. Other visitors see it within the cache minute.
