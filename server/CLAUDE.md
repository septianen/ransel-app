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
  db/migration/V1__init.sql … (one file per change, V31 on 31 Oct 2026)
server/src/test/kotlin/  testApplication + Postgres (Testcontainers or docker compose)
```

## Rules

- **Paths and bodies come from `shared`** (`ApiRoutes`, `dto/`). Do not declare server-only request/response classes for public endpoints. Server-internal classes (rows, entities) stay in `server/`.
- **Responses**: success via a `call.respondData(status, data)` helper that wraps `ApiResponse`; every failure through StatusPages from typed exceptions (`ApiException(status, ErrorCode.X, message, details)`). Never leak stack traces or SQL messages. 204 has an empty body.
- **Validation** uses `shared/src/commonMain/kotlin/app/ransel/shared/domain/Validation.kt` (same rules as the forms) → 422 `VALIDATION_FAILED` with `details` per field.
- **Authorization**: every trip route calls one `requireRole(tripId, minRole)`; non-members get 404 `TRIP_NOT_FOUND`, members with a too-low role get 403 `FORBIDDEN`. The caller is the JWT subject (`call.userId()`). The V1 dummy user `00000000-0000-0000-0000-000000000001` only exists as the owner of guest data before the first sign-in; it has no email and cannot log in.
- **Migrations**: run with Flyway at startup. Production has applied them, so never edit an existing migration: add the next number (V31, …) and tell the frontend agent if a column changes.
- **Soft delete** everywhere (`deleted_at`); lists exclude deleted rows; `/sync` pulls include them.
- **`updated_at`** is set by the DB trigger (server clock). Pull cursors (`GET /sync?since=`) use server time. Last-write-wins on push compares the incoming `updatedAt` with the stored one.
- **Transactions**: day reorder (`PUT .../days/{date}/order`), sync push batches, invite accept (`trip_members` insert + `used_count + 1`), expense + splits — each in one transaction.
- **Money**: amounts stay in the currency they were recorded in; summaries/balances convert with `convertMinor` in `shared/src/commonMain/kotlin/app/ransel/shared/domain/Money.kt` and `fx_rate_to_base`.
- **Secrets from env only**: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `PHOTO_SIGNING_SECRET`, `JWT_ISSUER`, `LLM_API_KEY`, `PLACES_API_KEY`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `APP_BASE_URL` (https://ransel.mietek.tech). Provide `.env.example`, never commit `.env`.
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

## Deploy

Live at `https://ranselapi.mietek.tech` (VPS `sen@187.127.97.109`, SSH port 2212, folder `/opt/ransel`). Full runbook, secrets list and rollback: `deploy/README.md`.

- The VPS already runs nginx for other sites, so nginx is the public entry (`deploy/nginx/`), not Caddy. `docker-compose.prod.yml` runs `server` (published on `127.0.0.1:8080` only) and `db` (Postgres 16, private volume).
- `.github/workflows/deploy-server.yml`: on push to `main` touching server/shared/deploy it runs the tests, builds `:server:installDist`, pushes `ghcr.io/septianen/ransel-server:<sha>` and rolls it out over SSH. Gradle does not run inside Docker because `:shared` configures the Android plugin.
- Sign-up is open (`REGISTRATION_ENABLED` defaults to true; set `false` to close it). New accounts verify their email (SMTP via `SMTP_*`) before Explore and the signup credits; `SEED_USER_*` accounts start verified.
- Migrations V1–V4 are now applied in production: **never edit them again**, add V5, V6, ...
- Daily `pg_dump` via `deploy/backup-db.sh` (cron on the VPS); copying dumps off the VPS is still manual.
