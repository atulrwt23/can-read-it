# engagement

**Purpose.** What readers say about series: ratings and chapter comments now, reports later in phase 2. The module does not own series (see [catalog](catalog.md)); it stores their IDs.

## Public API (`com.canreadit.engagement`)
Nothing yet: other modules don't read it. `discovery.series_stats` has rating columns for when rankings use ratings; an event (for example `SeriesRated`) will feed them then.

**Uses:**
- `catalog.CatalogQueries`:
  - `readableSeriesId(slug)`: only series readers can open (published or unlisted) can be rated or commented on
  - `visibleChapter(seriesId, number)`: comments belong to chapters that are out
- `identity.UserDirectory.profiles(userIds)`: the handle and display name shown with each comment

**Listens to:** `identity.UserDeleted`. `EngagementDataEraser` deletes the reader's ratings (taking them out of each series' totals) and their comments.

## Ratings
- **Scores.** One score from 1 to 5 per reader and series. Rating again replaces the score, and readers can remove it.
- **Who can rate.** Rating needs a confirmed email: the access token's `ev` claim, otherwise `403 email_not_confirmed`. Ratings are public, so this makes every throwaway account cost a real inbox. `TODO(product)`: CLAUDE.md section 7 names only commenting and purchasing.
- **Average.** The average is the plain mean, to two decimals, with no minimum number of ratings. `TODO(product)`: a minimum count, or a weighted (Bayesian) average, before ratings drive rankings.
- **Totals.** `series_ratings` keeps a count and a sum per series. They change in the same transaction as the score: a new rating adds one and its score, a change adds the difference, and a removal subtracts it. A series page reads one row and never scans the ratings.
- **Placeholder data.** `LocalEngagementSeeder` (`local` and `demo` profiles) gives each seeded series placeholder totals. It runs only while no series has ratings. Real ratings add to those totals.

## Comments
- **Where.** Comments belong to a chapter that is out, in a series readers can open. They are a flat list, newest first. `TODO(product)`: replies, and comments on the series page.
- **Text.**
  - Plain text, 1–2000 characters after cleaning: trimmed, line breaks kept (at most one blank line in a row), other control characters removed.
  - The web app shows the text as text, never as HTML or Markdown.
- **Who can comment.** Writing needs a confirmed email (CLAUDE.md section 7), otherwise `403 email_not_confirmed`.
- **Limits.** 5 comments a minute and 100 a day per reader. `TODO(product)`: the daily cap.
- **Authors.**
  - Comments show the author's display name and handle, and nothing else about the account.
  - Comments by accounts waiting to be deleted are hidden at once. Erasing the account deletes them.
  - Authors delete their own comments. Moderators removing other people's comments comes with reports and the moderation queue.
- **Paging.** The list is public and the same for everyone, cached for 15 seconds. It uses cursor paging by comment ID (UUIDv7, so newest first), served by the index `(chapter_id, id desc)`.

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/series/{slug}/rating` | Public, the same for everyone: `{average, count}`; `average` is null before the first rating. CDN-cacheable for a minute (`PublicCache`). `404` for a series readers can't open |
| `GET /api/v1/me/ratings/{slug}` | Signed in: `{score, series: {average, count}}`, with `score` null when the reader hasn't rated it |
| `PUT /api/v1/me/ratings/{slug}` | Signed in, CSRF, confirmed email. Body `{score}` (1–5). Answers like `GET`, with the new average |
| `DELETE /api/v1/me/ratings/{slug}` | Signed in, CSRF. Removes the reader's score (no score: no change) |
| `GET /api/v1/series/{slug}/chapters/{number}/comments?cursor=&limit=` | Public, the same for everyone: `{items: [{id, author: {handle, displayName}, body, createdAt}], nextCursor}`, newest first, 20 a page by default (at most 50). CDN-cacheable for 15 seconds. `404` for a chapter that isn't out |
| `POST /api/v1/series/{slug}/chapters/{number}/comments` | Signed in, CSRF, confirmed email. Body `{body}`. `201` with the comment; `400 comment_empty` or `comment_too_long` |
| `DELETE /api/v1/me/comments/{id}` | Signed in, CSRF. Deletes the reader's own comment; `404` for anyone else's |

The `me` routes and comment posts are `no-store`. Rating changes are limited to 30 a minute per reader.

## Data (schema `engagement`)
Migration `V202610161800__engagement_ratings.sql`. There are no foreign keys to other schemas.

| Table | Key | Notes |
|---|---|---|
| `ratings` | (user_id, series_id) | `score` 1–5 (`smallint`, checked), `created_at`, `updated_at` |
| `series_ratings` | series_id | `ratings` (count) and `score_sum`, neither below zero |
| `comments` | id (UUIDv7) | `series_id`, `chapter_id`, `user_id`, `body` (1–2000 characters, checked), `created_at`. Indexes `(chapter_id, id desc)` and `(user_id)`. Migration `V202610161900__engagement_comments.sql` |

## Web
- **Series banner.** The average and the number of ratings (`3.4 (748 ratings)`) are rendered on the server, the same for every visitor. If the rating can't be loaded, the page renders without it.
- **`SeriesRating` client component.** It loads after hydration and shows:
  - signed-out visitors: "Sign in to rate this series"
  - readers with an unconfirmed email: a link to confirm it
  - everyone else: five stars as a native radio group ("Your rating"), so arrow keys work, plus "Remove rating"
- **After a change,** the average updates from the API's answer. Other visitors see it within the cache minute.
- **Reader: `ChapterComments`.** A client section under every chapter that isn't locked.
  - The reader has no site layout, so the section brings its own `SessionProvider`.
  - The list loads when the reader is near the end of the chapter, without cookies, 20 at a time with "Load more comments".
  - Signed-in readers with a confirmed email get a text box with a character count. Others get a link to sign in or to confirm their email.
  - A new comment appears at the top straight away. Authors see "Delete" on their own comments, matched by handle.
