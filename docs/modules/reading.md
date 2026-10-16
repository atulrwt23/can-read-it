# reading

**Purpose.** A signed-in reader's library (followed series), where they are in each series, the chapters they finished, and the import of the history a guest kept in the browser. Reading itself never needs an account (CLAUDE.md section 2): guests keep the same state in `localStorage` (`web/src/lib/history.ts`), and it moves into the account at sign-in. The module does not own series or chapters (see [catalog](catalog.md)); it stores their IDs.

## Public API (`com.canreadit.reading`)
- `ReadingQueries.followers(seriesId)`: the readers who follow a series ([notifications](notifications.md) emails them about new chapters).
- Events: `SeriesFollowed(userId, seriesId, at)` and `SeriesUnfollowed(userId, seriesId, at)`, published only when the library actually changed.

**Uses:** `catalog.CatalogQueries`:
- `readableSeriesId(slug)`
- `visibleChapter(seriesId, number)` and `visibleChapters(seriesId, numbers)`
- `chaptersByIds(ids)`
- `readableCardsByIds(ids)` (published and unlisted)

**Listens to:** `identity.UserDeleted`. `ReadingDataEraser` deletes the reader's rows.

## Data (schema `reading`)
Migration `V202610140900__reading_tables.sql`. There are no foreign keys to other schemas.

| Table | Key | Notes |
|---|---|---|
| `library_entries` | (user_id, series_id) | `added_at`; index (user_id, added_at desc, series_id desc) for the library pages |
| `reading_progress` | (user_id, series_id) | one row per series: the chapter, `position` 0–1 (`numeric(5,4)`), `updated_at`. An upsert replaces it only with a newer `updated_at`, so the latest timestamp wins |
| `read_chapters` | (user_id, chapter_id) | `series_id` is kept so one query loads a series' marks. The first `read_at` stays |

- Writes are single-statement SQL upserts through `JdbcClient` (`ReadingRepository`). These rules ("latest wins", "read once") are plainer in SQL than in an entity.
- Series and chapters are stored by ID, so renamed slugs keep their history.
- The API speaks slugs and chapter numbers, like the reader's URLs.

## Endpoints (`/api/v1/me/…`, signed in)
All responses are `no-store`. Writes need the CSRF header. No confirmed email is needed.

| Route | Notes |
|---|---|
| `GET /me/library` | followed series, newest first: `items[{series (card), addedAt, progress?}]`, `nextCursor`; `limit` ≤ 50 |
| `PUT /me/library/{slug}`, `DELETE /me/library/{slug}` | follow and unfollow. Following twice is a no-op. `404 series_not_found` for a series readers can't open |
| `GET /me/series/{slug}` | `{following, progress?: {number, position, updatedAt}, readChapters}` (numbers in numeric order), for the series page and the reader |
| `PUT /me/progress/{slug}` | `{number, position, finished}`. The server's clock stamps it. `finished` also marks the chapter read. `404 chapter_not_found` for a chapter that isn't visible |
| `GET /me/history` | recently read series, newest first: `items[{series, progress}]`, `nextCursor` ("Continue reading") |
| `POST /me/history/import` | `{series: [{slug, number, position, updatedAt, read?}]}`, up to 200 series and 5,000 read marks (newest kept). Newer progress replaces older; read marks add up; future timestamps are clamped to now. Unknown series or chapters are counted in `skipped` |

**Rate limits** (per user):
- progress: 120 a minute
- follow and unfollow: 60 a minute
- import: 10 an hour

## Web
- **`lib/reading/store.ts`** decides where reading state lives:
  - **Signed in** (the `signed-in` hint): the account.
  - **Guest:** `localStorage`.
  - If the session has ended (a `401` after refresh), writes fall back to `localStorage`, so the next sign-in imports them.
  - Concurrent reads of one series share a request.
- **Reader** (`ReadingTracker`):
  - Guests: progress is saved locally on every scroll pause.
  - Signed-in readers: progress is sent at most every 10 s, and on leaving (`keepalive` on `pagehide`).
  - Reaching the end panel sends `finished`.
- **Series page:**
  - "Continue Ch. N" and the read and last-read marks come from the store.
  - **Follow** toggles the library (`aria-pressed`). Guests get a sign-in link back to the series.
- **Home** "Continue reading" and **`/library`** (Following with "Load more", History) read the account when signed in.
  - Guests see the browser history and a sign-in prompt.
  - `/library` is `noindex`.
- **Import:** `SessionProvider` imports the guest history once whenever the session becomes signed in.
  - On success the browser copy is cleared; on failure it stays for the next page load.
  - Account reads wait for a running import.

## Open questions
- `TODO(product)`: following and the library are one thing. Notifications for followed series arrive in phase 2. The follower count in discovery's `series_stats.follows` is not fed yet.
- `TODO(product)`: a followed series that is later drafted or removed drops out of the library list, but its row is kept, in case it comes back.
- `TODO(product)`: history has no "remove from history" or "mark unread" yet.
