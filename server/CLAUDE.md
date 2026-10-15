# server/ — backend agent

Read the root `CLAUDE.md` first. Your task list: `docs/tasks/backend.md`. Your reference: `docs/TRD.md` (Data model, API, Offline-first sync, Domain logic, Auth and security, AI feature).

## Stack

Ktor Server (Netty) · kotlinx.serialization · Ktor plugins: ContentNegotiation, StatusPages, CallLogging, Authentication (JWT, from Phase 3), RateLimit (Phase 5) · PostgreSQL 16 · Exposed · Flyway · HikariCP · bcrypt · Ktor Client for LLM/Places (Phase 5) · Docker Compose. Use current stable versions at setup; Kotlin must be ≥ 2.3 (the contract uses `kotlin.time.Instant`).

## Layout

```
server/src/main/kotlin/app/ransel/server/
  Application.kt        module wiring, config from env
  plugins/              Serialization, StatusPages, Auth, RateLimit, CallLogging
  routes/               one file per resource, paths only from shared ApiRoutes
  service/              business rules, transactions, requireRole
  db/                   Exposed table objects + repositories
  ai/                   LLM + Places clients, prompt, cache (Phase 5)
server/src/main/resources/
  application.yaml
  db/migration/V1__init.sql … V4__ai_recommendation.sql
server/src/test/kotlin/  testApplication + Postgres (Testcontainers or docker compose)
```

## Rules

- **Paths and bodies come from `shared`** (`ApiRoutes`, `dto/`). Do not declare server-only request/response classes for public endpoints. Server-internal classes (rows, entities) stay in `server/`.
- **Responses**: success via a `call.respondData(status, data)` helper that wraps `ApiResponse`; every failure through StatusPages from typed exceptions (`ApiException(status, ErrorCode.X, message, details)`). Never leak stack traces or SQL messages. 204 has an empty body.
- **Validation** uses `shared/src/commonMain/kotlin/app/ransel/shared/domain/Validation.kt` (same rules as the forms) → 422 `VALIDATION_FAILED` with `details` per field.
- **Authorization**: every trip route calls one `requireRole(tripId, minRole)`; non-members get 404 `TRIP_NOT_FOUND`, members with a too-low role get 403 `FORBIDDEN`. Before Phase 3 every request acts as the dummy user `00000000-0000-0000-0000-000000000001` (seeded in V1).
- **Migrations**: V1–V4 already contain the whole schema (one file per phase). Run them with Flyway at startup. Until the first production deploy you may fix V1–V4 in place (tell the frontend agent if a column changes); after that, never edit an applied migration — add V5, V6, …
- **Soft delete** everywhere (`deleted_at`); lists exclude deleted rows; `/sync` pulls include them.
- **`updated_at`** is set by the DB trigger (server clock). Pull cursors (`GET /sync?since=`) use server time. Last-write-wins on push compares the incoming `updatedAt` with the stored one.
- **Transactions**: day reorder (`PUT .../days/{date}/order`), sync push batches, invite accept (`trip_members` insert + `used_count + 1`), expense + splits — each in one transaction.
- **Money**: amounts stay in the currency they were recorded in; summaries/balances convert with `convertMinor` in `shared/src/commonMain/kotlin/app/ransel/shared/domain/Money.kt` and `fx_rate_to_base`.
- **Secrets from env only**: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER`, `LLM_API_KEY`, `PLACES_API_KEY`, `EMAIL_API_KEY`, `APP_BASE_URL` (https://ransel.app). Provide `.env.example`, never commit `.env`.
- **Logging**: structured, one line per request with method, path, status, duration and `error.code`. Never log passwords, tokens or full request bodies.

## Run / test

Needs JDK 17+ (`JAVA_HOME`), and Docker for the dev database and the tests (Testcontainers starts its own Postgres 16).

```
docker compose up -d db          # Postgres for development (repo root)
./gradlew :server:run            # http://localhost:8080/api/v1/health
./gradlew :server:test           # testApplication + real Postgres via Testcontainers
./gradlew :shared:jvmTest        # contract + domain tests
```

Config comes from env vars (defaults match the compose database); see `.env.example`.

`server/http/*.http` has one file per resource with example requests (they double as documentation for the frontend agent); select the `local` environment in `http-client.env.json`.

## Deploy (end of Phase 2, owner's VPS)

Docker image for the server (multi-stage Gradle build), `docker-compose.prod.yml` with `server`, `db` (Postgres 16, volume, not exposed publicly) and `caddy` (reverse proxy, automatic HTTPS for `api.ransel.app`; later also serves the web app and `/.well-known/assetlinks.json` + `apple-app-site-association` for invite links). Daily `pg_dump` via cron copied off the VPS. GitHub Actions: build → push image to GHCR → SSH `docker compose pull && up -d`. Ask the owner for host, user and domain; never guess them.
