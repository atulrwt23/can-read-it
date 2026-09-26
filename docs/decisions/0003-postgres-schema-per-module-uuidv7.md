# 0003. PostgreSQL 18, schema per module, app-generated UUIDv7

**Status:** Accepted (2026-09-26)

## Context
We need one relational store for the beta that still keeps module data separable. IDs must be globally unique, index-friendly and known before insert, so that domain events can carry them.

## Decision
- **PostgreSQL 18** (the latest GA). It has a built-in `uuidv7()` and asynchronous I/O. It runs as a container in the beta and must stay portable to any managed Postgres.
- **One schema per module:**
  - `identity`, `catalog`, `media`, `reading`, `discovery`
  - `modulith` for Spring Modulith's `event_publication`
  - more as modules are built
- No foreign keys across schemas. Cross-module references are plain `uuid` columns.
- A single Flyway instance manages all schemas. Migrations are named `V<yyyyMMddHHmm>__<module>_<description>.sql`, and merged migrations are never edited.
- **UUIDv7 generated in the application** (`shared` ID generator). `DEFAULT uuidv7()` on ID columns covers rows inserted directly by SQL (seeds, manual fixes).
- Extensions: `citext` (emails) and `pg_trgm` (title search).

## Consequences
- A module can later move to its own database by moving its schema, with no FK surgery.
- Referential integrity across modules is enforced in application code and events (for example `UserDeleted` → `reading` deletes that user's rows).
- Requires Postgres 18+ for the `uuidv7()` default. On an older managed Postgres, the default is dropped and the app still generates IDs.
