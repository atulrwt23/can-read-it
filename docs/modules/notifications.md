# notifications

**Purpose.** Gets messages to readers. Email only for now:
- the account emails identity asks for: sign-in, sign-up, confirmation and reset codes, and notices
- new-chapter emails for followed series (phase 2)

Push and in-app notifications come later. The module reads nothing from other modules' tables.

## Public API
- `EmailSender`: sends one `EmailMessage(to, subject, text, headers)`. The only way email leaves the app ([ADR 0007](../decisions/0007-email-over-smtp.md)). `headers` carries extra headers such as `List-Unsubscribe`.

**Uses:**
- `reading.ReadingQueries.followers(seriesId)`: who follows a series
- `identity.UserDirectory.contacts(userIds)`: the email and display name of active accounts with a confirmed email (nobody else)
- `catalog.CatalogQueries`: `visibleChapter` and `readableCardsByIds`

**Listens to:**
- `catalog.ChapterPublished`
- `reading.SeriesUnfollowed`
- `identity.UserDeleted`
- `identity.AccountEmailRequested` (a plain event, see below)

## How account emails go out
1. Identity publishes `identity.AccountEmailRequested(to, kind, code, codeValidFor)` after its transaction commits.
2. `AccountEmails` handles it on another thread (`@Async @EventListener`), renders plain text and calls `EmailSender`.
3. A failed send is logged (kind and error, never the code) and dropped. The event is deliberately not a persisted Modulith event, so codes are never written to `modulith.event_publication`; codes expire in minutes, so retrying later would not help anyway.

Emails are plain text, with the brand from `app.brand.name`, and never put the code in the subject (it shows in lock-screen previews). `TODO(product)`: an HTML version.

## New-chapter emails
1. **Queue.** `NewChapterQueue` handles `ChapterPublished` (a persisted module event, so it survives a restart).
   - It handles only chapters of a series' original edition, because that is what readers open. `TODO(product)`: per-language emails once translations are readable.
   - It queues one `pending_chapters` row for each follower, skipping readers who turned the emails off.
2. **Digest.** `ChapterDigests` runs every `app.notifications.digest-interval` (10 minutes).
   - It picks readers with queued chapters whose last digest was more than `app.notifications.min-gap` ago (3 hours, `TODO(product)`), at most 200 a run.
   - Each reader gets one email listing every queued chapter, grouped by series. Each series shows up to 5 chapter links and then a link to the series. Series readers can no longer open are left out.
   - Each reader is handled in its own transaction that locks their `preferences` row (`for update skip locked`), so two API instances never send the same digest.
   - After a send, the listed chapters are deleted and `last_digest_at` is set. A failed send leaves them queued for the next run.
3. **Who gets one.** Only active accounts with a confirmed email (`UserDirectory`). Chapters queued for anyone else are dropped at the digest.
4. **What is dropped.** Unfollowing drops that series' queued chapters. Turning the emails off or unsubscribing drops all of them. A deleted account drops everything. Chapters queued for longer than `app.notifications.pending-ttl` (7 days) are dropped rather than sent late.
5. **Links.** Links need the public address `app.notifications.site-url` (`SITE_URL`, the same as the web app's). Without it the digest job sends nothing, and chapters wait until they expire.
6. **Unsubscribing.**
   - Every email carries `List-Unsubscribe` and `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058). Mail apps can turn the emails off with one POST, which Gmail and Yahoo expect from bulk senders.
   - The text links to `/unsubscribe?token=…`, which asks for a click before it posts, because mail scanners open links on their own.
   - The token is random, one per reader and never changes.

## Endpoints
| Route | Notes |
|---|---|
| `GET /api/v1/me/notifications` | Signed in. `{newChapterEmails}`, `true` until turned off. `no-store` |
| `PATCH /api/v1/me/notifications` | Signed in, CSRF. Body `{newChapterEmails}`; turning it off drops queued chapters. 30 a minute per reader |
| `POST /api/v1/notifications/unsubscribe?token=` | Public, reads no cookie (no CSRF). `204`; `404 unsubscribe_link_invalid` for an unknown token, `400` for a malformed one. 30 an hour per IP |

## Data (schema `notifications`)
Migration `V202610161700__notifications_chapter_digests.sql`. There are no foreign keys to other schemas.

| Table | Key | Notes |
|---|---|---|
| `preferences` | user_id | `new_chapter_emails` (default on), `unsubscribe_token` (unique), `last_digest_at`. Created on first use |
| `pending_chapters` | (user_id, chapter_id) | `series_id`, `number`, `published_at`, `queued_at` (indexed for expiry) |

## Web
- `/account` has an **Emails** section with the new-chapter toggle, saved as soon as it changes. It says when nothing will arrive because the email isn't confirmed.
- `/unsubscribe?token=` is client-rendered, `noindex`, and sends no `Referer`. It posts without cookies, so it works signed out.

## External port
| Interface | Adapter | Local | Beta |
|---|---|---|---|
| `EmailSender` | `SmtpEmailSender` (`JavaMailSender`, `spring.mail.*`) | Mailpit, http://localhost:8025 | Resend over SMTP, once a domain is verified ([deploy.md](../deploy.md#email-settings)) |

Without `spring.mail.host` (`SMTP_HOST`), `LoggingEmailSender` logs each subject and drops the email, so the app runs without email. The sender is `app.mail.from` (`MAIL_FROM`). Spring Boot's mail health check is off, so an email outage never marks the API down.

## Open questions
- `TODO(product)`: how often a reader may get a new-chapter email (3 hours today), and whether readers choose per series.
- `TODO(product)`: `followers(seriesId)` loads every follower at once. Page through them, or move fan-out to its own worker, once a series has tens of thousands of followers.
