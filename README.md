# CanReadIt

A reading platform for manhwa (vertical-scroll webtoons) and web novels. Readers discover series, read chapters, follow them, and come back when new chapters are released.

## Documentation
- [`CLAUDE.md`](CLAUDE.md): project context, decisions and coding rules (the source of truth)
- [`docs/architecture.md`](docs/architecture.md): system design, module map, data ownership, deployment
- [`docs/decisions/`](docs/decisions/): architecture decision records

## Stack
- **Backend:** Java 25, Spring Boot 4 (Spring Modulith monolith), PostgreSQL 18, Redis
- **Web:** Next.js 16, TypeScript, Tailwind CSS v4
- **Storage:** S3-compatible object storage (SeaweedFS locally, Cloudflare R2 in the beta)

## Local development
The code skeleton is coming in roadmap step 1. Once it lands:

```bash
cp infra/local/.env.example infra/local/.env
docker compose -f infra/local/docker-compose.yml up -d
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # http://localhost:8080
cd web && pnpm install && pnpm gen:api && pnpm dev                       # http://localhost:3000
```
