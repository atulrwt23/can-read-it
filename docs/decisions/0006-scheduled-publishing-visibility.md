# 0006. Scheduled publishing visibility rule

**Status:** Accepted (2026-09-26)

## Context
Creators schedule chapters for an exact time, and readers wait for it. If visibility depended on a background job flipping a status, a late or failed job would delay releases.

## Decision
- A chapter is visible to readers when `status <> 'DRAFT' AND publish_at <= now()`. Read queries apply this rule directly.
- A job runs every minute and, for chapters whose time has passed:
  - sets `status = PUBLISHED` and `published_at`
  - updates `series.last_published_at`
  - emits `ChapterPublished` through the Modulith outbox
- `ChapterPublished` drives notifications, discovery updates and web cache revalidation.

## Consequences
- Releases happen on time even if the job is late. Only side effects (emails, "latest updates" ordering) can lag by up to about a minute.
- Cached pages can show a chapter a little late, until revalidation or TTL expiry. Keep short `s-maxage` values on chapter lists.
- Queries must always include the visibility predicate. It belongs in one repository method or specification, not repeated by hand.
