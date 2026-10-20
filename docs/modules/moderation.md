# moderation

**Purpose.** The staff side of keeping comments civil:
- the queue of reported comments
- the decisions moderators make: remove the comment, or keep it
- an audit log of every decision

Bans, suspensions and appeals come later.

**Not owned:** comments and reports themselves belong to [engagement](engagement.md). This module reads and changes them only through `engagement.CommentModeration`.

## Who can moderate
- The permission is `CONTENT_MODERATE`, held by `MODERATOR` and `ADMIN` (`identity.internal.Permission`).
- `TODO(product)`: a way for admins to grant the `MODERATOR` role. Today it is granted with SQL (`identity.user_roles`), and admins come from `ADMIN_EMAILS`.
- Staff 2FA and re-authentication for destructive actions remain the identity TODO from CLAUDE.md section 7.

## Uses
- `engagement.CommentModeration`:
  - `reported(offset, limit)` and `reportedCount()`: comments with open reports, the one waiting longest first, with counts per reason and the reporters' notes. Never who reported.
  - `remove(commentId, moderator, at)`: hides the comment and closes its reports as `REMOVED`.
  - `keep(commentId, moderator, at)`: closes its reports as `DISMISSED`.
- `catalog.CatalogQueries.chaptersByIds` and `readableCardsByIds`: where the comment was posted (series and chapter).
- `identity.UserDirectory.profiles`: the author's and moderators' public names.

Each decision and its audit row are written in one transaction.

## Endpoints (`/api/v1/admin/moderation`, `CONTENT_MODERATE`, `no-store`)
| Route | Notes |
|---|---|
| `GET /comments?page=&size=` | `{total, items: [{commentId, body, createdAt, author, place: {seriesSlug, seriesTitle, chapterNumber}, reports, reasons, notes, firstReportedAt}]}`. Offset paging, 20 a page (at most 100) |
| `POST /comments/{id}/remove` | Body `{note?}` (at most 500 characters) for the log. `404` if no visible comment has this ID |
| `POST /comments/{id}/keep` | Body `{note?}`. `404` if the comment has no open reports |
| `GET /actions?limit=` | The latest decisions, newest first (50 by default, at most 200): `{id, moderator, action, commentId, note, at}` |

## Data (schema `moderation`)
Migration `V202610162001__moderation_actions.sql`.

| Table | Key | Notes |
|---|---|---|
| `actions` | id (UUIDv7) | `moderator_id`, `action` (`REMOVE_COMMENT`, `KEEP_COMMENT`), `target_type` (`COMMENT`), `target_id`, `note`, `created_at`. Rows are never changed. Indexed by `created_at desc` |

## Web
- `/admin/moderation` is open to moderators and admins (`canModerate`).
- Each reported comment is a card with:
  - the author, and a link to the chapter
  - the comment text
  - counts per reason and the reporters' notes
  - an optional note for the log
  - "Remove comment" and "Keep comment"
- Below the cards, "Recent decisions" lists the audit log.
- The account menu shows "Moderation" to staff. The catalog's page links to it.
