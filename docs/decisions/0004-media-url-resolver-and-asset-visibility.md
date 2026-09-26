# 0004. Media URL resolver and asset visibility

**Status:** Accepted (2026-09-26)

## Context
Page and cover images are stored under immutable, content-addressed keys and served from a CDN with long cache lifetimes. That is ideal for free content, but phase 3 adds paid early-access chapters: a public, forever-cached URL would leak paid pages to anyone who has the link. If other modules built URLs themselves, fixing that later would touch every module.

## Decision
- `assets` gets `visibility PUBLIC|PROTECTED` and `sha256`.
- `media.MediaUrls` is the **only** place that turns asset IDs into URLs. It resolves in batches: `resolve(Collection<UUID> ids, Variant variant)`.
  - `PUBLIC`: `{app.media.public-base-url}/{storage_key}`, immutable, CDN-cached.
  - `PROTECTED` (phase 3): short-lived signed URLs from a private bucket or prefix, issued only after an entitlement check.
- Pages are stored as one WebP variant (about 1080px wide) to fit the R2 free tier. User-uploaded SVG is never accepted or served.

## Consequences
- `catalog`, `discovery` and `reading` never know where images live or how they are protected.
- List endpoints call `resolve` once per response, not once per item.
- Switching storage, CDN domain or signing scheme changes only the `media` module.
