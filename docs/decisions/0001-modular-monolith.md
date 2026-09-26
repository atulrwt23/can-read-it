# 0001. Modular monolith with Spring Modulith

**Status:** Accepted (2026-09-26)

## Context
CanReadIt has many domains: identity, catalog, media, reading, discovery, notifications and, later, monetization and moderation. The team is small and the beta runs on one free VM. Microservices would add network hops, distributed transactions, more deployables and more memory, all before we know where the load is.

## Decision
- The backend is one Spring Boot application. Each top-level package under `com.canreadit` is a module, verified by Spring Modulith (`ModularityTests`).
- A module's root package is its public API. `<module>.internal` is private.
- Modules talk through public services (queries) and domain events (`@ApplicationModuleListener`, persisted in the Modulith event publication registry, which acts as an outbox).
- Each module owns its own Postgres schema (see [0003](0003-postgres-schema-per-module-uuidv7.md)).

## Consequences
- One build, one deploy, one process: this fits the 12 GB free VM.
- Boundaries are enforced by tests, so a later split into services (media processing, notification fan-out) means moving a package and turning events into broker messages, not untangling shared tables.
- Cross-module reads cost a service call instead of a SQL join. Denormalized read models (for example `discovery.series_stats`) are the accepted fix when that becomes slow.
