# CanReadIt architecture

This document describes how the system fits together. [`CLAUDE.md`](../CLAUDE.md) holds the rules and decisions; the ADRs in [`decisions/`](decisions/) explain why. When they disagree, CLAUDE.md wins and this file gets fixed.

## 1. Guiding principles

1. **One deployable backend, many modules.** A Spring Modulith monolith with enforced module boundaries. It can be split later, but not before a module needs it. ([ADR 0001](decisions/0001-modular-monolith.md))
2. **Free tier now, portable always.** Every external dependency is reached through an open protocol behind an interface we own. ([ADR 0002](decisions/0002-free-tier-first-vendor-neutral-seams.md))
3. **Reading is anonymous and cacheable.** The hot path (home, series, chapter) needs no account, and responses are identical for every visitor, so the CDN absorbs release spikes.
4. **Every module owns its data.** Each module has its own schema and there are no cross-schema joins. Modules communicate through public services and domain events.

## 2. Surfaces and request flow

```
                         ┌──────────────────────── Cloudflare (free) ────────────────────────┐
 Browser / later apps ──►│ DNS · CDN cache · WAF · Turnstile                                 │
                         │                                                                   │
                         │  canreadit.<tld>/api/*  ──┐                                       │
                         │  canreadit.<tld>/*      ──┤ Tunnel (outbound-only from the VM)    │
                         │  cdn.canreadit.<tld>    ──┼──────────────► R2 bucket (public)     │
                         └───────────────────────────┼───────────────────────────────────────┘
                                                     ▼
            ┌──────────────────────── Oracle Always Free VM (Docker Compose) ─────────────────────┐
            │  cloudflared ──► web:3000 (Next.js SSR) ──internal HTTP──► api:8080 (Spring Boot)    │
            │             └──────────────────────────────────────────► api:8080                    │
            │                                                          │        │                  │
            │                                                   postgres:5432  redis:6379          │
            └──────────────────────────────────────────────────────────┼──────────────────────────┘
                                                                       └──► R2 via S3 API (writes, signed reads)
                                                                       └──► Resend via SMTP
```

- **Browser → `/api/*`:** always same-origin, so CORS stays closed and cookies are first-party.
- **Next.js SSR → API:** over the Docker network (`API_INTERNAL_URL=http://api:8080`). Public pages do not forward cookies.
- **Images** are served by the CDN from R2 and never pass through the VM. The API only returns URLs built by `media.MediaUrls`.
- **Mobile apps (phase 4)** use the same `/api/v1` with `Authorization: Bearer` tokens.

## 3. Backend modules

```
 feature modules   reading      discovery      notifications      (+ stubs: engagement, monetization,
                      │           │     │         │        │          moderation, rights, analytics)
                      ▼           ▼     │         ▼        ▼
 core modules      catalog ◄──────┘     │      identity    │
                      │  ▲──────────────┼──────────────────┘
                      ▼                 ▼         │
                    media ◄─────────────┘◄────────┘
                      │
                      ▼
                    shared   (ids, errors, time, web/security config, base events; usable by all)
```

Arrows point from the caller to the called module's **public API**. Feature modules depend on core modules, never the reverse. The table below is authoritative:

| Module | May call | Listens to events from |
|---|---|---|
| `shared` | nothing | nothing |
| `media` | `shared` | nothing |
| `identity` | `shared`, `media` (avatar URLs) | nothing |
| `catalog` | `shared`, `media` | `media` (`AssetReady`) |
| `discovery` | `shared`, `catalog`, `media` | `catalog` (`ChapterPublished`, series changes), `reading` (follows) |
| `reading` | `shared`, `catalog` | `identity` (`UserDeleted`) |
| `notifications` | `shared`, `identity`, `catalog` | `catalog` (`ChapterPublished`), `identity` (codes, resets) |
| stubs | defined when built | defined when built |

A dependency cycle, or an arrow pointing upward (for example `catalog` calling `discovery`), is a design bug. Use an event instead.

Inside each module:
```
com.canreadit.<module>/            ← public API: services, DTO records, events
com.canreadit.<module>.internal/   ← entities, repositories, controllers, adapters, jobs
```

### Ports for external services (ADR 0002)

| Port (interface) | Module | Protocol | Local adapter target | Beta target |
|---|---|---|---|---|
| `ObjectStorage` | media | S3 API | MinIO | Cloudflare R2 |
| `MediaUrls` | media | none (URL building) | MinIO public URL | `cdn.` R2 custom domain |
| `EmailSender` | notifications | SMTP | Mailpit | Resend |
| `IdTokenVerifier` | identity | OIDC + JWKS | Google/Apple (test clients) | Google/Apple |
| `BotChallengeVerifier` | identity | HTTPS siteverify | no-op | Cloudflare Turnstile |

## 4. Data ownership

One PostgreSQL 18 database with one schema per module. No foreign keys cross a schema boundary; references across modules are plain `uuid` columns. ([ADR 0003](decisions/0003-postgres-schema-per-module-uuidv7.md))

| Schema | Owner | Main tables |
|---|---|---|
| `identity` | identity | users, credentials, user_identities, user_roles, sessions, refresh_tokens, verification_codes, auth_events |
| `catalog` | catalog | series, series_slug_history, genres, series_genres, editions, chapters, chapter_pages, novel_chapter_bodies, series_members |
| `media` | media | assets |
| `reading` | reading | library_entries, reading_progress, read_chapters |
| `discovery` | discovery | series_stats, series_views_daily |
| `modulith` | Spring Modulith | event_publication (transactional outbox for domain events) |

Redis holds only data that can be rebuilt or thrown away:
- caches
- per-day view counters (sorted sets)
- rate-limit buckets
- the access-token denylist

Losing Redis never loses committed data. At worst, rankings lose up to one roll-up interval of views.

## 5. Key flows

### Read a chapter (anonymous)
1. The browser requests `/series/{slug}/chapter/{n}`. The CDN serves cached HTML if present; otherwise Next.js renders it.
2. Next.js calls `GET /api/v1/series/{slug}/chapters/{n}`. `catalog` checks the visibility rule, loads the page rows, and calls `MediaUrls.resolve(assetIds, PAGE)` once for the whole chapter.
3. The response contains page URLs with `width` and `height`, so the page renders with no layout shift. Images load straight from `cdn.` (R2).
4. The client sends a view beacon. `discovery` increments the Redis counter for the series and day. Guests keep progress in localStorage.

### Publish a scheduled chapter ([ADR 0006](decisions/0006-scheduled-publishing-visibility.md))
1. The chapter is saved with `status = SCHEDULED` and `publish_at = T`.
2. At T it becomes visible immediately, because read queries check `publish_at <= now()`.
3. Within a minute, the publish job:
   - flips the status to `PUBLISHED` and sets `published_at`
   - updates `series.last_published_at`
   - emits `ChapterPublished` through the Modulith outbox
4. Listeners react:
   - `discovery` refreshes "latest updates"
   - `notifications` emails followers (phase 2)
   - a web revalidation hook purges the Next.js cache for the series pages

### Home and rankings ([ADR 0005](decisions/0005-discovery-owns-home-and-rankings.md))
- `GET /api/v1/home` is served by `discovery`. It reads featured and latest series from `catalog`'s public API and rankings from `discovery.series_stats`.
- View counters go from Redis (`views:{yyyy-mm-dd}` sorted sets) to Postgres through a roll-up job every few minutes. "Today", "week" and "all time" are computed from `series_views_daily`.
- The response is cached briefly (`Cache-Control: public, s-maxage=60`).

### Media URL resolution ([ADR 0004](decisions/0004-media-url-resolver-and-asset-visibility.md))
- `MediaUrls.resolve(ids, variant)` does a single batched lookup.
- `PUBLIC` assets return `{app.media.public-base-url}/{storage_key}`, which is immutable and cached forever.
- `PROTECTED` assets (early access, phase 3) return short-lived signed URLs from a private bucket or prefix. Callers don't change.

## 6. Deployment

### Beta ($0)
| Piece | Choice | Swap path |
|---|---|---|
| Compute | Oracle Always Free Arm VM (2 OCPU / 12 GB), Docker Compose | any Docker host, ECS, k8s |
| Database | Postgres 18 container on the VM, nightly `pg_dump` to R2 | managed Postgres (RDS etc.): change the JDBC URL |
| Cache | Redis container on the VM | ElastiCache or Valkey: change the host |
| Object storage | Cloudflare R2 | S3 or any S3 API: change the endpoint and keys |
| Edge | Cloudflare free + Tunnel | any CDN or load balancer that routes `/api/*` |
| Email | Resend over SMTP | any SMTP relay: change `spring.mail.*` |
| Registry / CI | GHCR + GitHub Actions | any OCI registry |
| Secrets | SOPS + age (`infra/prod/.env.prod.sops`) | a cloud secrets manager injected as env vars |
| Monitoring | uptime check (free) + optional Grafana Cloud free via Alloy | any Prometheus or OTLP backend |

VM memory budget (12 GB):

| Process | Budget |
|---|---|
| Postgres | about 3 GB |
| JVM | about 2.5 GB |
| Next.js | about 1 GB |
| Redis | 256 MB (`maxmemory` set) |
| OS, page cache, headroom | the rest |

### Public launch (planned, not decided)
- Compute moves to AWS: ECS Fargate or EKS for `api` and `web`, RDS Postgres and ElastiCache.
- Cloudflare stays in front. R2 can stay, since it has no egress fees.
- Because every dependency is behind config (section 3 ports), the move is new infrastructure code and environment variables, with no application changes.

## 7. Scaling path

Scale in this order, and only when metrics say so:

1. **CDN first:**
   - catalog GETs and SSR pages are public and cacheable
   - images never hit the origin
   - release spikes are absorbed at the edge
2. **Vertical, then horizontal `api`:** the API is stateless (sessions are in Postgres and Redis), so run N replicas behind the edge.
3. **Postgres:**
   - add read replicas for catalog reads
   - partition `read_chapters` and `auth_events` when they get large
   - use PgBouncer if connection counts grow
4. **Split modules** that need independent scaling into services:
   - media processing first (CPU-heavy)
   - notification fan-out second (bursty)
   - Modulith events become messages on a broker; their shapes don't change
5. **Search** moves from `pg_trgm` and FTS to a dedicated engine behind the `discovery` search service (phase 4).
