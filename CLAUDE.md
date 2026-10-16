# CanReadIt: project context for Claude Code

Read this file before doing anything. It is the source of truth for what we are building, the decisions already made, and how code in this repo must be written. When a decision changes, update this file in the same PR.

The product is **CanReadIt** (repo and URL slug: `can-read-it`, Java package: `com.canreadit`). Keep the display name in one config value (`app.brand.name`) so styling changes stay trivial.

Related docs: [`docs/architecture.md`](docs/architecture.md) (system design) and [`docs/decisions/`](docs/decisions/) (ADRs).

---

## 1. What we are building

A reading platform for **manhwa** (vertical-scroll webtoons) and **web novels**. Readers discover series, read chapters, follow series, and come back when new chapters are released. Creators and publishers upload chapters and schedule releases. Later phases add paid early access (coins), subscriptions, ads, a Creator Studio, a moderation console, and native mobile apps.

The core product loop every feature should serve: **discover → read → follow → get notified → return.**

### Content rule (non-negotiable)
The platform only hosts content we have the rights to publish: original work, licensed work, or creator uploads with a rights attestation. **Never build scrapers, mirrors, or importers that pull chapters, images, or metadata from other reading sites.** All development and seed data must be invented placeholder content with generated placeholder images.

---

## 2. Decisions

### Decided
| Area | Decision |
|---|---|
| Name | **CanReadIt**, slug `can-read-it`, package `com.canreadit` |
| Repo | Monorepo: `backend/`, `web/`, `infra/`, `docs/` |
| Guiding principle | **Free tier now, portable always.** Every external service is reached through an open protocol (S3, SMTP, OIDC, OCI images, HTTP caching, Prometheus/OpenTelemetry) behind a small interface owned by the module that uses it. Changing vendor means new config or a new adapter, never edits to domain code ([ADR 0002](docs/decisions/0002-free-tier-first-vendor-neutral-seams.md)) |
| Backend shape | Spring Boot **modular monolith** (Spring Modulith). Split out services only when a module needs independent scaling (media processing and notification fan-out are the likely first candidates) ([ADR 0001](docs/decisions/0001-modular-monolith.md)) |
| Database | **PostgreSQL 18**, one schema per module, no cross-schema foreign keys ([ADR 0003](docs/decisions/0003-postgres-schema-per-module-uuidv7.md)) |
| Sign-in | Email + password, email one-time code, Google, Apple. **Reading never requires an account** |
| Sessions | Short-lived JWT access token + rotating opaque refresh token (details in section 7). A spent refresh token presented again within 20 s is a race between tabs (`409`, session kept); later, it revokes the session. Cookie-authenticated writes carry the `XSRF-TOKEN` double-submit header ([docs/modules/identity.md](docs/modules/identity.md)) |
| IDs | UUIDv7 everywhere (time-ordered, index-friendly), generated in the application. `uuidv7()` is the column default only for rows inserted by SQL |
| Content model | Series → Edition (one per language) → Chapter. Designed in from day one even if we launch with one language |
| Chapter numbers | Decimal (`12.5` for extras), stored as `numeric(8,2)`, shown without trailing zeros |
| Chapter visibility | A chapter is public once its `publish_at` has passed. The scheduler only emits events and never gates visibility ([ADR 0006](docs/decisions/0006-scheduled-publishing-visibility.md)) |
| Media URLs | Only the `media` module builds image URLs (`MediaUrls`). Assets are `PUBLIC` (CDN, cached forever) or `PROTECTED` (signed, short-lived) ([ADR 0004](docs/decisions/0004-media-url-resolver-and-asset-visibility.md)) |
| Home and rankings | Owned by `discovery`: daily view counters in Redis, rolled up into Postgres ([ADR 0005](docs/decisions/0005-discovery-owns-home-and-rankings.md)). Featured is automatic for now (the week's most viewed) |
| View counting | A view counts once per visitor, series and UTC day. The visitor is a daily-salted HMAC of IP and user agent (nothing personal stored), crawlers are ignored, and one IP counts at most 30 visitors per series and day ([docs/modules/discovery.md](docs/modules/discovery.md)) |
| Image pipeline | Uploads are checked (20 MB, 50 MP, PNG/JPEG/WebP by magic bytes, never SVG), resized down and re-encoded to lossy WebP inside the upload request, which strips all metadata. Pages: one variant 1080 px wide. Covers: 1200, 600 and 300 px. No original is kept. The codec is libwebp through JNI (`webp-imageio`), which needs an `exec` tmpfs in the read-only API container ([docs/modules/media.md](docs/modules/media.md)) |
| Staff access | Roles map to a fixed set of `Permission`s, checked with `@PreAuthorize("hasAuthority(...)")`; `ADMIN` has all of them and `MODERATOR` has `CONTENT_MODERATE`. `ADMIN_EMAILS` grants `ADMIN` at sign-in to listed addresses, only once confirmed. Staff edit the catalog at `/admin` through `/api/v1/admin/*`; moderators (and admins) work the queue at `/admin/moderation` ([docs/modules/identity.md](docs/modules/identity.md), [docs/modules/catalog.md](docs/modules/catalog.md)) |
| Reading sync | Signed-in readers keep their library (following = in the library), progress and read chapters in the `reading` schema, by series and chapter ID; the API speaks slugs and chapter numbers. Progress is one row per series and the latest timestamp wins. The web store uses the account when signed in and `localStorage` for guests, and imports the guest history once after sign-in ([docs/modules/reading.md](docs/modules/reading.md)) |
| Analytics | Anonymous events from a fixed list (page views by route template, series views, chapter opens and finishes, searches by result count only, follows, sign-ups counted by the API) in the `analytics` schema, sent with `sendBeacon` and never when Do Not Track or Global Privacy Control is on. Visitors are the same daily fingerprints as view counting (`shared.Visitors`). Raw events are kept 90 days, daily counts forever; staff read them at `/admin/analytics` ([docs/modules/analytics.md](docs/modules/analytics.md)) |
| Rate limits | `shared.RateLimiter`: Bucket4j token buckets in Redis, keyed by a hash of the client key, answering 429 with `Retry-After`, and allowing requests when Redis is down. Pages rendered by Next.js call the API from the web container, so per-visitor limits on them belong at the edge |
| Email | Sent over SMTP only. **Resend** for the beta, Mailpit locally ([ADR 0007](docs/decisions/0007-email-over-smtp.md)). Account emails (codes, notices) are a plain async event (`identity.AccountEmailRequested`), not a persisted module event, so codes never reach the event table; a failed send is logged and dropped ([docs/modules/notifications.md](docs/modules/notifications.md)) |
| New-chapter emails | Followers of a series get its new chapters (original edition only) in one digest email, at most every 3 hours (`TODO(product)`), and only at a confirmed address. Each email carries an RFC 8058 one-click `List-Unsubscribe`; `/account` has the switch. Links need `SITE_URL`, so nothing is sent without it ([docs/modules/notifications.md](docs/modules/notifications.md)) |
| Ratings | One score from 1 to 5 per reader and series, owned by `engagement`, with per-series totals kept in the same transaction. Rating needs a confirmed email (`TODO(product)`: section 7 names only comments and purchases). The average is public and CDN-cached for a minute; the reader's own stars load after hydration ([docs/modules/engagement.md](docs/modules/engagement.md)) |
| Comments | Flat, plain-text comments under chapters (1–2000 characters), newest first, owned by `engagement`. Public to read (cached 15 s) and written by readers with a confirmed email; authors delete their own, and comments show only the author's handle and display name ([docs/modules/engagement.md](docs/modules/engagement.md)) |
| Reports and moderation | Signed-in readers report comments with a reason and an optional note; reporters stay anonymous to staff. `moderation` owns the queue at `/admin/moderation` (permission `CONTENT_MODERATE`: `MODERATOR` and `ADMIN`) and an append-only audit log; removing a comment hides it for good and closes its reports, keeping it dismisses them ([docs/modules/moderation.md](docs/modules/moderation.md)) |
| Account deletion | `DELETE /me` starts a 30-day grace period; signing in again cancels it. Afterwards a daily job erases the account to a tombstone row (ID kept for other modules) and publishes `UserDeleted`. Password changes and deletion need the current password, or a sign-in within 10 minutes for accounts without one ([docs/modules/identity.md](docs/modules/identity.md)) |
| Social sign-in | The server issues a one-time nonce (`POST /auth/oauth/nonce`, Redis, 10 minutes) that the ID token must carry, so captured tokens can't be replayed. A provider is off until its client IDs are configured; the API never needs a client secret ([docs/modules/identity.md](docs/modules/identity.md)) |
| Registration | `POST /auth/password/register` always answers `202`: a new account gets a confirmation code (and can already sign in with its password), an existing email gets a notice. A sign-in code used on an account whose email was never verified removes its password and sessions (the section 7 linking rule applied to codes) ([docs/modules/identity.md](docs/modules/identity.md)) |
| Money (later) | Append-only double-entry coin ledger, idempotent payment webhooks. Never a mutable balance column |
| Time | `Instant` in UTC in Java, `timestamptz` in Postgres |
| Design direction | Bulma-inspired: turquoise primary, system font stack, 4px spacing grid, small radii, soft card shadow. The tokens and page structure in section 8 are the whole spec; there is no prototype ([ADR 0008](docs/decisions/0008-design-from-tokens.md)) |
| Beta hosting | Free tier: Oracle Cloud Always Free Arm VM for compute, Cloudflare free plan at the edge, Cloudflare R2 for images (section 9) |

### Open (do not decide these in code; leave a clear seam and a `TODO(product):` comment)
- Content model: open creator platform, licensed publisher, or own studio. The `rights` module stays a stub until this is decided.
- Domain and trademark clearance for CanReadIt in the launch markets.
- Launch markets, languages and payment provider.
- UI language(s) of the web app. Build strings so they can be extracted later, but ship English only.
- Paid hosting for public launch (planned: AWS compute and data behind Cloudflare). Keep storage behind the S3 API and all services in containers so the move is configuration, not code.

---

## 3. Tech stack

Use the **latest stable GA release** of each item at the time you scaffold, and pin exact versions (Gradle version catalog, `package.json`). Do not use milestones, RCs or snapshots.

### Backend (`backend/`)
- Java 25 (LTS), toolchain configured in Gradle
- Spring Boot 4.x, Gradle with **Kotlin DSL** and a version catalog (`gradle/libs.versions.toml`), a single Gradle project (modules are packages, enforced by Spring Modulith)
- Spring Web MVC with virtual threads enabled (`spring.threads.virtual.enabled=true`)
- Spring Security 7 (lambda DSL only), OAuth2 resource server for our own JWTs, Nimbus JOSE for signing
- Spring Data JPA (Hibernate) for aggregates the app writes, `JdbcClient` for read models and list queries (keyset pagination, trigram search), PostgreSQL 18, Flyway migrations. JPA arrived with the first write path (identity, step 2a)
- Spring Data Redis (cache, counters, rate limits, token denylist), Bucket4j for rate limiting
- Spring Modulith (module verification, event publication registry, `@ApplicationModuleListener`)
- springdoc-openapi (OpenAPI spec at `/v3/api-docs`, used to generate the web client types)
- AWS SDK v2 S3 client (works against SeaweedFS locally and Cloudflare R2 in the beta)
- `webp-imageio` (libwebp through JNI) to decode and encode WebP in the media pipeline; PNG and JPEG use the JDK's ImageIO
- Spring Mail (`JavaMailSender`) for SMTP. No email-vendor SDKs
- Testing: JUnit 5, AssertJ, Testcontainers (Postgres, Redis), Spring Modulith test support
- Formatting: Spotless with palantir-java-format
- Notes: Boot 4 splits auto-configuration into smaller modules, so add the specific starters you need. Boot 4 defaults to Jackson 3; do not mix in Jackson 2 unless a library forces it. No Lombok: use records for DTOs and plain classes for entities.

### Web (`web/`)
- Next.js 16.x (App Router), React, TypeScript in strict mode, pnpm (version pinned in `packageManager`). Always use the **latest patch release** of Next.js: it ships regular security patches.
- TypeScript is pinned to the latest **6.x**, not 7.x: TypeScript 7 (the native port) has no JavaScript compiler API yet, which Next.js type-checking and `openapi-typescript` need. Revisit when they support it.
- Tailwind CSS v4 with design tokens as CSS variables (section 8). UI primitives follow shadcn/ui conventions (copied-in, token-styled components in `web/src/components`); step 1 needed only a few, written by hand. Add shadcn components through its CLI when a richer primitive (dialog, dropdown) is needed
- No web fonts: system sans stack for UI and titles, system serif stack for novel reading (fast first paint, nothing to download)
- API types generated from the backend OpenAPI spec (`openapi-typescript` + `openapi-fetch`)
- Biome for lint and format, Vitest for unit tests, Playwright with axe for end-to-end smoke and accessibility tests (`web/e2e`)
- Next.js 16 changed several APIs (for example `proxy.ts` replaced middleware, and `params`/`searchParams` are promises). Its version-matched docs ship in `web/node_modules/next/dist/docs/`; check them before relying on memory.
- Images come from our CDN with known dimensions, so `next/image` optimization is off (`unoptimized` or a pass-through loader). The web container needs no image-processing native dependencies.

### Local infrastructure (`infra/local/docker-compose.yml`)
| Service | Purpose | Ports |
|---|---|---|
| postgres (18) | primary database | 5432 |
| redis | cache, counters, rate limits | 6379 |
| seaweedfs (+ one-shot bucket init) | S3-compatible storage for covers and pages ([ADR 0009](docs/decisions/0009-seaweedfs-for-local-object-storage.md)) | 8333 S3 API |
| mailpit | catches outgoing email (codes, password resets) | 1025 SMTP, 8025 UI |

---

## 4. Repository layout

```
/
├─ CLAUDE.md                     ← this file
├─ README.md                     ← how to run everything locally
├─ docs/
│  ├─ architecture.md            ← system design (surfaces, modules, scale, roadmap)
│  ├─ modules/                   ← one design doc per module as it gets built
│  └─ decisions/                 ← short ADRs: NNNN-title.md
├─ infra/
│  ├─ local/                     ← docker-compose.yml, .env.example
│  └─ prod/                      ← beta compose file, pull-based deploy, backup and bootstrap scripts, systemd timers (docs/deploy.md)
├─ backend/
│  ├─ build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
│  └─ src/main/java/com/canreadit/
│     ├─ CanReadItApplication.java
│     ├─ shared/                 ← ids, errors, time, web + security config, base event types
│     ├─ identity/               ← accounts, credentials, sessions, roles
│     ├─ catalog/                ← series, editions, chapters, genres, series members
│     ├─ media/                  ← uploads, image processing, storage, public URLs
│     ├─ reading/                ← library, follows, progress, history
│     ├─ engagement/             ← ratings, comments, reports
│     ├─ discovery/              ← home feed, search, rankings, trending, view counters
│     ├─ monetization/           ← coins, entitlements, subscriptions (stub for now)
│     ├─ notifications/          ← email, push, in-app (email only for now)
│     ├─ moderation/             ← review queue and audit log (bans to come)
│     ├─ rights/                 ← rights holders, takedowns (stub, see open decisions)
│     └─ analytics/              ← anonymous product events and daily counts
├─ web/                          ← Next.js app
└─ .github/workflows/ci.yml
```

Stub modules contain only a `package-info.java` with a one-line description so the module map is visible from day one.

---

## 5. Backend rules

### Module boundaries (Spring Modulith)
- Every top-level package under `com.canreadit` is a module. Types in the module's root package are its **public API**. Everything under `<module>.internal` is private to that module.
- A module never touches another module's entities or repositories. To read data from another module, call that module's public service. For side effects, publish a domain event and handle it with `@ApplicationModuleListener`.
- `ModularityTests` (`ApplicationModules.of(CanReadItApplication.class).verify()`) must stay green. Treat a failure as a design problem, not a test to skip.
- Each module owns its own **Postgres schema** (`identity`, `catalog`, `media`, `reading`, `discovery`, ...). There are no foreign keys across schemas; other modules store plain `uuid` references such as `user_id`.
- Allowed dependency directions are listed in `docs/architecture.md`. `catalog` and `media` sit at the bottom; `discovery`, `reading` and `notifications` depend on them, never the other way round.

### Portability (see ADR 0002)
- Every external service sits behind an interface owned by the module that uses it:
  - object storage: `media.internal.ObjectStorage` (S3 API)
  - email: `notifications.EmailSender` (SMTP)
  - social login: `identity.internal.IdTokenVerifier` (OIDC + JWKS)
  - image URLs: `media.MediaUrls`
- Domain code never imports a vendor SDK. The AWS S3 SDK and Jakarta Mail are allowed only in the adapter classes, because they are protocol clients.
- Every vendor endpoint, bucket, host and credential is configuration under `app.*` or an environment variable. Nothing vendor-specific is hard-coded: no R2 account IDs, no Resend hosts.
- Everything runs as an OCI container image built for `linux/amd64` and `linux/arm64`.

### Database
- A single Flyway instance manages all module schemas (`spring.flyway.schemas` lists them; the history table lives in `public`).
- Flyway migrations live in `src/main/resources/db/migration`, named `V<yyyyMMddHHmm>__<module>_<description>.sql`.
- Never edit a migration that has been merged. Add a new one.
- Spring Modulith's `event_publication` table lives in a `modulith` schema and is created by a Flyway migration (Modulith's own schema initialization stays off).
- Placeholder seed data runs only in the `local` profile (development) and the `demo` profile (the placeholder beta, which is `noindex`), through seeder components. It is never part of the migration path.
- Use `citext` for emails, `numeric(8,2)` for chapter numbers, `jsonb` only for genuinely schemaless data (for example image variants).
- Extensions: `citext` and `pg_trgm`, created by the first migration.

### API
- Base path is `/api/v1`. JSON only.
- Errors are RFC 9457 Problem Details (Spring `ProblemDetail`) with a stable `type` URI and a machine-readable `code`.
- Feeds and chapter lists use cursor pagination (`?cursor=&limit=`). Admin tables may use offset pagination.
- Request and response DTOs are records. Entities are never serialized directly.
- Validate input with Jakarta Validation. Never trust client-supplied IDs for ownership: check them on the server.
- Public `GET`s for catalog content are cacheable. Set `Cache-Control` deliberately, and never vary a public response on cookies.

### Configuration
- `application.yml` plus environment variables. Profiles: `local`, `test`, `prod`.
- No secrets in the repo, not even encrypted ones. `infra/local/.env.example` and `infra/prod/.env.example` document every variable; production settings live in Oracle Cloud Vault (docs/deploy.md).
- All app-specific properties live under `app.*` and are bound to `@ConfigurationProperties` records (for example `app.media.public-base-url` or `app.auth.access-token-ttl`).

### Observability
- Actuator `health`, `info` and `prometheus` endpoints. Structured JSON logs in `prod`.
- Every request carries a request ID that is added to the logs.
- Metrics and logs leave the box only through open formats (Prometheus scrape, OTLP), so any backend can collect them.

---

## 6. Domain model (first cut)

**catalog**
- `series`:
  - id, slug (unique), title, alt_titles (text[]), synopsis
  - type `MANHWA|NOVEL`
  - status `ONGOING|COMPLETED|HIATUS`
  - visibility `DRAFT|PUBLISHED|UNLISTED|REMOVED`: only `PUBLISHED` appears in lists and search; `UNLISTED` works by direct link; `REMOVED` returns 410
  - age_rating `ALL|TEEN|MATURE`
  - cover_asset_id, first_released_year, release_cadence
  - last_published_at: denormalized from chapters, drives "latest updates"
  - created_at, updated_at
- `series_slug_history`: old_slug (PK), series_id, changed_at. Old slugs return a 301 to the current one.
- `genres`, `series_genres`
- `editions`: id, series_id, language (BCP-47), is_original, translator_credit
- `chapters`:
  - id, edition_id, number `numeric(8,2)`, title
  - status `DRAFT|SCHEDULED|PUBLISHED`
  - publish_at (planned), published_at (actual)
  - access `FREE|EARLY_ACCESS`, price_coins
  - unique (edition_id, number)
- `chapter_pages` (manhwa): chapter_id, page_index, asset_id, width, height
- `novel_chapter_bodies` (novels): chapter_id, body_markdown, word_count
- `series_members`: series_id, user_id, role `OWNER|EDITOR|UPLOADER`. Per-series creator permissions live here, not in Identity.

**Visibility rule:** a chapter is visible to readers when `status <> 'DRAFT' AND publish_at <= now()`, so a scheduled chapter goes live on time even if a job is late. A scheduler runs every minute:
- sets `status = PUBLISHED` and `published_at`
- updates `series.last_published_at`
- publishes `ChapterPublished`, which drives notifications and cache revalidation

**Search (phase 1):** `pg_trgm` GIN indexes on `title` and `alt_titles` (these work for Korean and other CJK titles), plus Postgres full-text search on `synopsis`. Titles also match by trigram word similarity, so small typos still find them, and results come best match first unless another sort is chosen (docs/modules/catalog.md). For now `catalog` runs these queries over its own tables (`GET /api/v1/series?q=`). When search moves to a dedicated engine (phase 4), it becomes a `discovery` read model fed by catalog events, behind the same endpoint.

**media**
- `assets`:
  - id, kind `COVER|PAGE|AVATAR`, visibility `PUBLIC|PROTECTED`
  - storage_key, sha256, content_type, width, height, bytes
  - status `UPLOADED|PROCESSING|READY|FAILED`
  - variants (jsonb), created_at
- Storage keys are immutable and content-addressed, so the CDN can cache public ones forever.
- Only `media.MediaUrls` turns asset IDs into URLs, as a batch call (`resolve(ids, variant)`). `PUBLIC` assets get a plain CDN URL; `PROTECTED` assets (early-access pages) will get short-lived signed URLs from a separate private bucket or prefix. No other module builds image URLs.
- Pages are stored as one WebP variant about 1080px wide. Covers get a small set of sizes. Never accept or serve user-uploaded SVG.

**discovery**
- `series_stats`: series_id (PK), views_today, views_7d, views_all, follows, rating_avg, rating_count, updated_at (a read model fed by counters and events)
- `series_views_daily`: series_id, day, views, PK (series_id, day)
- Live view counts go to Redis (a sorted set per day). A job rolls them up into `series_views_daily` and `series_stats`, and hands the 7-day views to `catalog` (`series.popularity`) for the "Popular" browse sort.

**reading**
- `library_entries`: user_id, series_id, added_at
- `reading_progress`: user_id, series_id, chapter_id, position (0–1), updated_at (latest timestamp wins)
- `read_chapters`: user_id, chapter_id, read_at, PK (user_id, chapter_id)

---

## 7. Identity design (decided)

### Sign-in methods
- **Email + password.** Hash with Argon2id (Spring Security `Argon2PasswordEncoder`, which needs BouncyCastle). Passwords must be 10–128 characters. No composition rules; a breached-password check can be added later.
- **Email one-time code.** 6 digits, expires in 10 minutes, at most 5 attempts, stored hashed. The same endpoint handles sign-up and sign-in.
- **Google and Apple.** The client gets an ID token from the provider's SDK and posts it to the backend. The backend verifies signature (JWKS), issuer, audience, expiry and nonce, then finds or creates the user. Match users on (provider, provider `sub`), never on email alone. Apple sends the email only on the first sign-in, so store it then. Providers are configuration entries (issuer, JWKS URL, audiences) behind `IdTokenVerifier`, so adding one needs no new code path.
- New accounts start with an unverified email. Unverified users can read, bookmark and follow. **Commenting and purchasing require a verified email.**

### Account linking (security-critical)
- Auto-link a social login to an existing account only when **both** the provider says the email is verified **and** the existing account's email is verified.
- If an existing account has an **unverified** email and a verified social login arrives for that email, the social identity wins: attach it, mark the email verified, and **delete the unverified password credential and revoke its sessions**. This prevents account pre-hijacking.
- A sign-in method can be unlinked only if another one remains.

### Tables (schema `identity`)
- `users`:
  - id, email (citext, unique, nullable for Apple relay edge cases), email_verified_at
  - handle (unique), display_name, avatar_asset_id
  - status `ACTIVE|SUSPENDED|BANNED|PENDING_DELETION|DELETED`
  - birth_year, locale
  - created_at, updated_at, deleted_at
- `credentials`: user_id (PK), password_hash, password_updated_at
- `user_identities`: id, user_id, provider `GOOGLE|APPLE`, provider_subject, provider_email, linked_at, unique (provider, provider_subject)
- `user_roles`: user_id, role, granted_by, granted_at
- `sessions`: id, user_id, device_name, platform, ip, user_agent, created_at, last_used_at, expires_at, revoked_at
- `refresh_tokens`: id, session_id, token_hash (unique), issued_at, used_at, expires_at
- `verification_codes`: id, email, purpose `SIGN_IN|VERIFY_EMAIL|PASSWORD_RESET|EMAIL_CHANGE`, code_hash, attempts, expires_at, used_at
- `auth_events`: id, user_id, type, ip, user_agent, created_at (sign-ins, failures, links, password changes, role changes, revocations)
- Retention: a scheduled job deletes expired `verification_codes` and `refresh_tokens`, and `auth_events` older than the retention window (`TODO(product):` pick the window; default 180 days).

### Tokens and sessions
- **Access token:** JWT signed with ES256, 15-minute lifetime. Claims: `sub`, `sid`, `roles`, `ev` (email verified), `iat`, `exp`, plus `kid` in the header. Public keys are published at `/.well-known/jwks.json`. In `prod`, load it from a secret (`AUTH_JWT_PRIVATE_KEY`); without one (and in the `local` profile) the API generates a key pair at startup and logs a warning, so tokens don't survive a restart.
- **Refresh token:** 256-bit random opaque string, stored hashed, 30-day sliding lifetime, **rotated on every use**. If an already-used refresh token is presented again, revoke the whole session (it was probably stolen).
- **Web:** both tokens live in `httpOnly`, `Secure` (except in the local profile), `SameSite=Lax` cookies. The refresh cookie's path is `/api/v1/auth/refresh`. State-changing requests carry a CSRF token (double-submit cookie).
- **Mobile (later):** the refresh token goes in the Keychain or Keystore, and the access token is sent as `Authorization: Bearer`.
- **Revocation:** bans, suspensions, role changes, password changes and "sign out everywhere" write the affected session IDs to a Redis denylist with a TTL equal to the access-token lifetime. A security filter checks the denylist on every authenticated request.
- A password reset or password change revokes all other sessions.

### Roles
`READER` (default), `CREATOR`, `MODERATOR`, `ADMIN`. Permissions are an enum in code with a fixed role-to-permission map, checked with `@PreAuthorize("hasAuthority('...')")`. Staff roles (`MODERATOR`, `ADMIN`) will require TOTP 2FA, and re-authentication within 10 minutes for destructive actions; leave a seam for this.

### Endpoints (all under `/api/v1`)
```
POST   /auth/password/register    { email, password, handle }
POST   /auth/password/login       { email, password }
POST   /auth/password/forgot      { email }                  → always 202
POST   /auth/password/reset       { email, code, newPassword }
POST   /auth/email/start          { email }                  → always 202
POST   /auth/email/verify         { email, code }
POST   /auth/oauth/{provider}     { idToken, nonce }
POST   /auth/refresh
POST   /auth/logout
GET    /me                        PATCH /me
POST   /me/password               { currentPassword, newPassword }
GET    /me/sessions               DELETE /me/sessions/{id}   POST /me/sessions/revoke-all
POST   /me/identities/{provider}  DELETE /me/identities/{provider}
DELETE /me                                                  → starts 30-day deletion
POST   /me/history/import                                   → merges guest history after sign-in
```

### Guardrails
- Rate limits (Bucket4j + Redis) on login, register, code start and verify, forgot-password and refresh, applied per IP and per email.
- After 5 failed logins for one account within 15 minutes, apply exponential backoff. Never lock an account permanently.
- Responses never reveal whether an email is registered.
- Every auth event is written to `auth_events`.
- Public auth forms (register, code start, forgot-password) are protected by a bot challenge. The beta uses Cloudflare Turnstile, verified server-side behind a `BotChallengeVerifier` interface and switchable off in `local`.

### Events published
`UserRegistered`, `EmailVerified`, `PasswordChanged`, `RoleGranted`, `RoleRevoked`, `UserSuspended`, `UserBanned`, `UserDeletionRequested`, `UserDeleted`.

---

## 8. Web rules

- Routes: `/`, `/browse`, `/series/[slug]`, `/series/[slug]/chapter/[number]`, `/library`, `/login` (with `/login/reset`), `/signup`, `/account`, `/unsubscribe`, and `/admin` (staff tools: series, chapters, uploads, releases, `/admin/analytics` and `/admin/moderation`; client-rendered and `noindex`). Studio comes later under `/studio`.
- Home, browse and series pages are **server-rendered** for SEO. The reader is a server-rendered shell with client components for scrolling, progress tracking and settings.
- **Public pages are identical for every visitor.** They never read auth cookies during render, so the CDN and Next.js can cache them. Anything personal (library state, "continue reading", the account menu) loads in client components after hydration. Catalog pages use time-based revalidation plus on-demand revalidation triggered by `ChapterPublished`.
- The browser only calls same-origin `/api/*`. In local development, a Next.js rewrite proxies `/api/*` to `http://localhost:8080`. In production, the edge routes `/api/*` to the backend (section 9). Server components call the backend through an internal base URL (`API_INTERNAL_URL`); only personalised server routes forward cookies.
- **Signed-in state (step 2):** personal data loads in client components through `lib/auth/client.ts`, which sends the `X-XSRF-TOKEN` header on writes and, on a `401`, refreshes the session once (`POST /api/v1/auth/refresh`, shared by concurrent calls) and retries. The refresh cookie is scoped to that path, so `proxy.ts` and Server Components never see it and don't refresh; a personalised server-rendered route would have to redirect through a client refresh first. `SessionProvider` (in the site layout) knows who is signed in after hydration; a localStorage hint (`signed-in`) lets signed-out visitors skip the `/api/v1/me` call. Auth and account pages are `noindex`, and `next` redirects only accept same-site paths (`lib/auth/next-path.ts`).
- `next build` runs without a backend (CI, image builds). Pages prerendered at build time must tolerate an unreachable API (`getHome` returns `null` during the build phase and the page shows a placeholder until the first revalidation). Series and chapter pages are generated on first request (`generateStaticParams` returns `[]`).
- The theme bootstrap script is the first child of `<body>`, never in `<head>`: React hydrates `<head>` as soon as cached app chunks run, which can happen before the parser reaches an inline script there (production-only hydration error #418).
- Chapter pages render with the explicit `width` and `height` returned by the API, so the layout never shifts. The first two pages load eagerly; the rest are lazy-loaded.
- Guests keep history and reading progress in localStorage, with each series' title, type and cover so the home page's "Continue reading" row needs no request. After sign-in, the app calls `POST /api/v1/me/history/import` once, then clears the local copy. Signed-in reading lives in the account (`lib/reading/store.ts` picks the place; the reader sends progress at most every 10 s and on leaving).
- Results added with "Load more" come back, at the same scroll position, when the reader returns with the back button (`lib/restore-on-back.ts`, keyed by history entry). A fresh visit starts over.
- Every route segment has a `loading.tsx` skeleton in flat token colours, shaped like the page. Because the skeleton starts the stream, a missing series or chapter answers 200 with `noindex` (Next.js's streamed not-found) rather than 404. `TODO(product):` if real 404s are needed (analytics, compliance), check slugs in `proxy.ts`.
- **SEO:** canonical URLs and `og:url` need the public origin, so they are only emitted when `SITE_URL` is set (the quick tunnel's address changes). Series pages carry Open Graph and Twitter cards and `ComicSeries`/`BookSeries` JSON-LD; `/sitemap.xml` lists home, browse and every published series from the API; search results and locked chapters are `noindex`. Icons live in `app/` (`favicon.ico`, `icon.png`, `apple-icon.png`).
- A removed series (the API answers 410) shows "This series was removed" with `noindex` on its series and chapter pages. Next.js can only send 404 for a missing page, so the status stays 200.
- Reaching a chapter's end panel marks it read in the guest history; the series page ticks read chapters, highlights the last one opened, and offers "Go to chapter".
- Reader robustness: the chapter picker fetches every chapter in the background (pages of 200), a progress line shows how far through the chapter the reader is, failed page images retry once and then offer "Try again", the next chapter (and its first three manhwa pages) is prefetched past 60% of the current one, and manhwa has a page-width setting saved in localStorage.
- **Page structure (the design spec; there is no prototype file):**
  - **Header:** sticky header with logo, primary nav (Home, Browse, Library; the current section is highlighted with `aria-current="page"`), search box with type-ahead suggestions (cover thumbnail, title, type badge), theme toggle, and sign-in / account menu. It collapses to a menu button and a search icon on mobile.
  - **Home:**
    - hero carousel of featured series (banner, title, genres, synopsis excerpt, "Read now")
    - "Latest updates" grid of cards (cover, title, the latest 2–3 chapters with relative times, and a "new" badge on chapters under 24h old): the first page of browse by update, 12 cards, with cursor-based "Load more"
    - ranked "Popular" side panel with Today / Week / All-time tabs and numbered entries
    - on mobile the panel stacks below the grid
  - **Browse:** a filter bar (type, status, genres, sort), a results grid with cursor-based "Load more", and filters mirrored in the URL query.
  - **Series:**
    - a wide banner (blurred cover background) with cover, title, alt titles, type/status/age badges, rating, genres, and the "Start reading" / "Continue" and "Follow" actions
    - synopsis with "show more"
    - chapter list with number, title, date, a lock icon on early access, and a sort toggle
  - **Manhwa reader:** a continuous vertical strip of pages with no gaps. A top and bottom reader bar (series title, chapter picker, previous/next) hides on scroll down and shows on scroll up or tap. There is an end-of-chapter panel with a next-chapter button.
  - **Novel reader:** a centred reading column with a max width of about 70ch, in a serif stack, and the same auto-hiding bar. A settings sheet offers text size, line spacing, font (serif/sans), and page color (light, sepia, dark), saved in localStorage.
  - **Auth pages:** a single centred card with the method choices (Google, Apple, email code, password).
- Build components from these tokens and the local primitives. Covers and banners use the real cover assets. Empty states and placeholders use flat token colors, never decorative art.
- **Design tokens (Bulma-inspired, adapted for a reading app):**

  | Token | Light | Dark |
  |---|---|---|
  | bg / surface / raised | `#F7F8FA` / `#FFFFFF` / `#F0F2F5` | `#14161A` / `#1C1F24` / `#262A31` |
  | line | `#DBDBDB` | `#343A43` |
  | text / muted | `#363636` / `#6B6B6B` | `#E6E8EB` / `#A0A7B1` |
  | primary fill / text on primary | `#00D1B2` / `#07332C` | same |
  | primary as text (links, active) | `#007A70` | `#00D1B2` |
  | link / "new" badge | `#485FC7` | `#94A6F2` |
  | rating stars | `#9A6B00` | `#FFE08A` |
  | success (completed) | `#257953` | `#7BDDB0` |

  Spacing uses a 4px base (4, 8, 12, 16, 24, 48). Radii are 4px by default and 6px for panels, with pill shapes only where deliberate. Card shadow: `0 .5em 1em -.125em rgba(10,10,10,.1), 0 0 0 1px rgba(10,10,10,.02)`. Type sizes: 48 / 40 / 32 / 24 / 20 / 16px body with a 24px line height. Titles use weight 700–800 with slight negative letter-spacing.
- **Contrast rule:** never put white text on the turquoise primary (about 2:1 contrast). Use the dark `#07332C` on turquoise fills, and the deeper `#007A70` whenever turquoise is used as text on light backgrounds.
- Accessibility: visible focus, keyboard navigation, `prefers-reduced-motion` respected, sufficient contrast in both themes.

---

## 9. Local development

```bash
cp infra/local/.env.example infra/local/.env
docker compose -f infra/local/docker-compose.yml up -d
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # http://localhost:8080
cd web && pnpm install && pnpm gen:api && pnpm dev                       # http://localhost:3000
```

- The `local` profile seeds about 16 invented placeholder series (manhwa and novels, several genres and statuses, varied update times) plus fake view stats for the rankings. The seeder generates simple placeholder page images and covers (for example flat-color PNGs drawn with the JDK's `ImageIO`, showing the series color and page number), uploads them to SeaweedFS, and records their dimensions. SVG is not used, because the media pipeline never serves SVG.
- Outgoing email goes to Mailpit at http://localhost:8025.

### Beta hosting (free tier)
All of it costs $0. Every piece can be swapped by configuration.

- **Compute:** one Oracle Cloud Always Free Ampere (Arm) VM. The beta VM is 1 OCPU / 6 GB in Mumbai, the smallest Always Free Arm shape (the allowance can be up to 2 OCPU / 12 GB). It runs `api`, `web`, `postgres`, `redis` and `cloudflared` with Docker Compose (`infra/prod/`). **Build every image for `linux/arm64` as well as `linux/amd64`.**
  - Memory budget (container limits, sized for 6 GB): Postgres 1.5 GB (`shared_buffers` 512 MB), JVM 2 GB (heap 75% via `-XX:MaxRAMPercentage`), Next.js 1 GB, Redis 320 MB with `maxmemory` set, plus 2 GB of swap. On a 12 GB VM, double them.
  - Arm capacity is often short: `infra/prod/launch-vm.sh` retries VM creation from Cloud Shell until it succeeds.
  - Optional: upgrade the Oracle account to Pay-As-You-Go with a $0 budget alert. Always Free resources stay free, the VM is no longer reclaimed for being idle, and Arm capacity is easier to get.
- **Edge:** the Cloudflare free plan. A Cloudflare Tunnel keeps the VM's inbound ports closed, so there is no reverse proxy on the VM ([ADR 0010](docs/decisions/0010-pull-based-deploys-and-quick-tunnel.md)):
  - **Now (no domain, $0):** a quick tunnel at a random `*.trycloudflare.com` address forwards everything to `web`, and Next.js proxies `/api/*` to `api`. Images are served from the R2 bucket's `r2.dev` address.
  - **With a domain:** a named tunnel routes `<domain>/api/*` → `http://api:8080` and `<domain>/*` → `http://web:3000`, and `cdn.<domain>` is an R2 custom domain. Switching is configuration only.
- **Images:** Cloudflare R2 through the S3 API. The free tier covers 10 GB of storage, 1M write and 10M read operations a month, with no egress fees. Budget storage accordingly: one WebP page variant, and no originals kept after processing (`TODO(product):` revisit if creators need originals back).
- **Email:** Resend over SMTP (`spring.mail.*`) with the domain verified (SPF, DKIM). The free tier is enough for the beta.
- **Images registry and deploys** ([docs/deploy.md](docs/deploy.md)):
  - On every green push to `main`, GitHub Actions builds the api and web images natively on amd64 and arm64 runners and publishes them to GHCR as `:<sha>`.
  - Deploys are **pull-based**: a systemd timer on the VM runs `infra/prod/deploy.sh` every 2 minutes. It deploys `origin/main` once its images exist, waits for health checks, and rolls back (remembering the failed SHA) if they fail. Operators can pin a release.
  - The VM checks out the same commit as the images, so `infra/prod/compose.yml` and the scripts always match the release.
  - Migrations run at API start and rollbacks don't undo them: every migration must keep the previous release working (expand, then contract later).
- **Secrets:** all production settings live in one **Oracle Cloud Vault** secret, read by the VM with its own identity (instance principal), so no credential is stored on the VM ([ADR 0011](docs/decisions/0011-production-settings-in-oracle-cloud-vault.md)). `infra/prod/lib.sh` fetches it into memory (`/run/canreadit/env`) on every deploy or backup run; `SECRETS_PROVIDER=file` (`/opt/canreadit/.env`) remains as a fallback. CI never holds production secrets, because it doesn't deploy.
- **Demo content:** the beta runs the `demo` profile (placeholder series seeded into an empty database) and is `noindex` until the web image is built with `ALLOW_INDEXING=true`.
- **Backups:** `infra/prod/backup.sh` runs nightly (03:17 UTC) and uploads a `pg_dump` to a private R2 bucket (RPO 24h; an R2 lifecycle rule keeps 30 days). Restore steps are in docs/deploy.md; rehearse them before the beta opens. WAL-based point-in-time recovery can be added later without app changes.
- **Monitoring:** a free external uptime check on `/actuator/health`. Optionally, Grafana Cloud's free tier, fed by Grafana Alloy scraping Prometheus metrics and shipping logs. It is optional and swappable because only open formats leave the box.
- **Everything is defined in code:** the compose file, deploy scripts and Cloudflare configuration, so the whole stack can move to paid hosting in an hour.
- If Docker is not available in the current environment (for example in a remote Claude Code session), still write the compose file and the Testcontainers tests, verify what you can (`./gradlew compileJava test -x integrationTest`, `pnpm typecheck`, `pnpm build`), and list in the PR description which checks could not run.

---

## 10. Quality bar

- **Backend:** unit tests for domain logic. Integration tests use `@SpringBootTest` with Testcontainers in a separate `integrationTest` source set or task. `ModularityTests` always runs.
- **Web:** `pnpm lint`, `pnpm typecheck`, `pnpm build` and `pnpm test` must pass. `pnpm e2e` (Playwright and axe, against the running local stack) must pass too; a new screen gets a smoke test with an accessibility check in both themes.
- **CI** (GitHub Actions, on every PR):
  - backend: `./gradlew spotlessCheck build`
  - web: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build`
  - end-to-end: the local compose services, the API with the `local` profile, a production web build, then `pnpm e2e`
- Conventional Commits (`feat(catalog): ...`, `fix(identity): ...`). Keep PRs small and focused on one module where possible.
- **Security baseline:**
  - no secrets in code
  - uploads validated by magic bytes and size, with metadata stripped, and SVG rejected
  - user uploads are never served from the API origin
  - CORS stays closed, because the browser talks same-origin

---

## 11. Roadmap and current focus

**Phase 1 (MVP: a readable product)**
1. **Skeleton + catalog read slice** (done)
2. **Identity** (all sign-in methods, tokens, sessions, account page) (done: 2a sessions and password sign-in, 2b email codes, confirmation and password reset, 2c Google and Apple sign-in, off until client IDs are configured, 2d account management, 2e web sign-in, sign-up, reset and account pages; session renewal happens in the browser, not `proxy.ts`)
3. **Media upload pipeline + minimal admin upload screen + scheduled publishing** (done: WebP pipeline, permissions and `ADMIN_EMAILS`, catalog admin API, `/admin` screens, scheduling and "release now")
4. **Reading: library, follows, progress sync, guest history import** (done: `reading` schema and `/api/v1/me/{library,series,progress,history}`, Follow button, `/library`, account-or-browser reading store, import after sign-in)
5. **SEO basics (metadata, sitemap), analytics events, beta deployment (`infra/prod`)** (done: SEO basics, the beta deployment, and anonymous analytics events with `/admin/analytics`)

Phases 1 and 2 are complete. Phase 2 shipped new-chapter emails for followed series, series ratings, chapter comments, and comment reports with the moderation queue; push and in-app notifications wait for the apps (phase 4). Next: phase 3, which starts with decisions still open in section 2 (content model, payment provider).

Phase 2: notifications, ratings, comments, reports and moderation queue.
Phase 3: Creator Studio, coins and early access, subscriptions, ads.
Phase 4: iOS then Android apps, recommendations, search upgrade, multi-language editions, load testing for release spikes.

### Definition of done for step 1 (skeleton + catalog read slice)
- [x] Architecture, database and deployment decisions recorded (`docs/architecture.md`, ADRs 0001–0008)
- [x] Monorepo layout from section 4, with root `README.md`, `.gitignore`, `.editorconfig`
- [x] `infra/local` compose file with postgres 18, redis, seaweedfs (+ bucket init) and mailpit, plus `.env.example`
- [x] Backend builds on Java 25 / Boot 4.x, with all module packages present (stubs where noted) and a passing `ModularityTests`
- [x] `shared`: UUIDv7 ID generation, Problem Details error handling, request-ID logging, `@ConfigurationProperties` records, security config that permits public catalog `GET`s and denies everything else by default
- [x] Flyway migrations for the `catalog`, `media`, `discovery` and `modulith` schemas from sections 5–6, and for the `identity` tables from section 7 (tables only; the auth flows are step 2)
- [x] `media.MediaUrls` and the `ObjectStorage` S3 adapter (public URLs only for now)
- [x] `local` profile seeder: placeholder series, editions, chapters, generated cover and page images in SeaweedFS, novel chapter bodies, and fake view stats
- [x] Catalog and discovery read APIs:
  - `GET /api/v1/home` (featured, latest updates, popular for today, week and all time)
  - `GET /api/v1/series` (q, type, status, genres, sort, cursor, limit)
  - `GET /api/v1/series/{slug}` (301 for old slugs)
  - `GET /api/v1/series/{slug}/chapters`
  - `GET /api/v1/series/{slug}/chapters/{number}` (pages with URL, width and height, or the novel body, plus previous and next chapter numbers)
  - `GET /api/v1/genres`
- [x] OpenAPI served at `/v3/api-docs`, and web types generated from it
- [x] Web: home, browse, series and reader pages rendering real API data, following the section 8 page structure and tokens, with light and dark themes and responsive layouts
- [x] CI workflow green

---

## 12. Working agreements for Claude Code

- Start each task by reading this file and any relevant `docs/modules/*.md`.
- For anything larger than a small fix, post a short plan (files, migrations, endpoints) before writing code.
- Respect module boundaries. If a change seems to need a cross-module repository call, stop and use a public service or an event instead.
- Respect the portability rules: new external services get an interface and a config-driven adapter, and are chosen from free tiers unless a decision in section 2 says otherwise.
- Don't make product decisions silently. When something is unspecified, choose the smallest reasonable option, mark it with `TODO(product):`, and mention it in the PR description.
- Don't add a dependency without a one-line justification in the PR description. Prefer what Spring and Next.js already provide.
- When you finish a step, tick its box in section 11, and record any new decision in section 2 (plus an ADR in `docs/decisions/` if it's significant).
- Never add code that fetches content from third-party reading sites (see section 1).
