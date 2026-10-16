# analytics

**Purpose.** Anonymous product events, and the daily counts staff read at `/admin/analytics`. Nothing stored identifies a person:
- no user IDs, emails, IPs or full URLs
- no search text

**Not owned:** view counts for rankings, which belong to [discovery](discovery.md). Server metrics leave through Prometheus as usual.

## Events (`EventName`, a fixed list)
| Event | Sent by | Extra |
|---|---|---|
| `PAGE_VIEW` | every page (root layout) | `path`: the route template (`/series/[slug]`, `/[other]`); staff pages are skipped |
| `SERIES_VIEW` | series page | the series |
| `CHAPTER_OPEN`, `CHAPTER_FINISH` | reader (open; end panel reached) | the series and chapter number |
| `SEARCH` | browse with a query | `value`: the number of results, never the text |
| `FOLLOW` | Follow button | the series |
| `SIGN_UP` | the API, from `identity.UserRegistered` | nothing else; browsers can't send it |

## Data (schema `analytics`)
Migration `V202610161600__analytics_events.sql`.

- **`events`**:
  - Columns: id, `occurred_at` (the server's receive time, not the browser's clock), name, `visitor`, path, `series_id` (no foreign key), chapter, value.
  - `visitor` is the daily fingerprint from `shared.Visitors`: an HMAC of IP and user agent with a random key that changes every UTC day. Discovery's view counter uses the same thing. Visitors can't be linked across days.
  - Raw events are kept for `app.analytics.retention` (90 days, `TODO(product)`).
- **`daily_counts`**: (day, name) → events and distinct visitors.
  - Recomputed hourly for yesterday and today (`app.analytics.rollup-cron`). The recount writes absolute values, so it is safe to repeat.
  - Kept after the raw events are deleted (`app.analytics.cleanup-cron`, daily at 04:37 UTC).
- **Metrics:** every recorded event increments the Prometheus counter `canreadit_analytics_events_total{name}`.

## Endpoints
| Route | Notes |
|---|---|
| `POST /api/v1/analytics/events` | Public; reads no cookie (so no CSRF). Body: `{events: [...]}`, 1–20 events. Answers `204` whether or not the events were kept |
| `GET /api/v1/admin/analytics?days=` | `ANALYTICS_VIEW` (admins); `days` 1–90. Returns `{names, days: [{day, counts: {NAME: {events, visitors}}}], topSeries}` |

**`POST /api/v1/analytics/events`:**
- Paths must be route-template shaped: letters, digits, `/[]_-`, so no query strings.
- Dropped events:
  - everything from crawlers (`Visitors.isBot`)
  - `SIGN_UP` from browsers
  - events about series readers can't open
- Rate limit: 60 requests a minute per IP.

**`GET /api/v1/admin/analytics`:**
- Today is counted live from the raw events; earlier days come from `daily_counts`.
- `topSeries` is the 10 most opened series over the last 7 days.

## Web
- **`lib/analytics.ts`** (`track`):
  - Queues events and sends them in batches of up to 20 with `navigator.sendBeacon`, falling back to a `keepalive` fetch without cookies.
  - Sends after 5 s, or at once when the page is hidden or closed.
  - Sends nothing at all when the browser has Do Not Track or Global Privacy Control on.
- **Components:**
  - `PageViewTracker` (root layout) counts route templates.
  - `TrackEvent` sends one event from a server-rendered page (series view, search).
  - The reader tracker and the Follow button call `track` directly.
- **`/admin/analytics`:** the most opened series, and a table of daily counts (events, with distinct visitors in brackets) for 7, 30 or 90 days.

## Open questions
- `TODO(product)`: a privacy notice describing these events before the beta opens to the public.
- `TODO(product)`: how long to keep raw events (90 days today).
- `TODO(product)`: funnels or retention cohorts would need a visitor that lasts longer than a day. That trades privacy away, and nothing here does it.
