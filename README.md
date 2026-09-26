# CanReadIt

A reading platform for manhwa (vertical-scroll webtoons) and web novels. Readers discover series, read chapters, follow them, and come back when new chapters are released.

## Documentation
- [`CLAUDE.md`](CLAUDE.md): project context, decisions and coding rules (the source of truth)
- [`docs/architecture.md`](docs/architecture.md): system design, module map, data ownership, deployment
- [`docs/decisions/`](docs/decisions/): architecture decision records
- [`docs/modules/`](docs/modules/): one design doc per backend module

## Stack
- **Backend** (`backend/`): Java 25, Spring Boot 4 (Spring Modulith monolith), PostgreSQL 18, Flyway
- **Web** (`web/`): Next.js 16, React 19, TypeScript, Tailwind CSS v4
- **Local infrastructure** (`infra/local/`): Postgres, Redis, SeaweedFS (S3 API) and Mailpit in Docker Compose

## Run it locally

Prerequisites: Docker, JDK 25, Node.js 22+ with Corepack (`corepack enable` gives you the pinned pnpm).

```bash
# 1. Infrastructure
cp infra/local/.env.example infra/local/.env
docker compose -f infra/local/docker-compose.yml up -d

# 2. API on http://localhost:8080 (the local profile seeds placeholder series on first start)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

# 3. Web on http://localhost:3000
cd web && pnpm install && pnpm dev
```

| What | Where |
|---|---|
| Web app | http://localhost:3000 |
| API | http://localhost:8080/api/v1/home |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Emails (Mailpit) | http://localhost:8025 |
| Images (SeaweedFS, S3 API) | http://localhost:8333/canreadit-media/ |

All seed content is invented placeholder text and generated images. To reseed from scratch, drop the volumes and start again:

```bash
docker compose -f infra/local/docker-compose.yml down -v
```

## Checks

```bash
cd backend && ./gradlew spotlessCheck build          # unit, modularity and Testcontainers integration tests
cd backend && ./gradlew build -x integrationTest     # without Docker
cd web && pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

After changing an API, regenerate the web client types with the API running: `cd web && pnpm gen:api`.
