# Backend tasks (server/)

Work top to bottom. Tick `[x]` when done and keep this file in the repo as the progress log. Each phase ends with its **Done when** check; tell the owner when a phase is done. Phase 2 can start right away, in parallel with frontend Phase 1.

## Phase 2 — Basic backend (no sign-in yet)

- [x] **B2.1 Skeleton.** Ktor server module, config from env (`server/CLAUDE.md`), `docker-compose.yml` with Postgres 16 for development, `GET /api/v1/health` → `{"data":{"status":"ok"}}`.
- [x] **B2.2 Database.** Flyway runs every migration in `db/migration` at startup (V1–V4 all at once is fine; the schema for later phases simply waits). Exposed table objects for V1 tables. HikariCP.
- [x] **B2.3 Plumbing.** `respondData` helper, `ApiException` + StatusPages mapping to `ApiErrorResponse`, validation via `shared/src/commonMain/kotlin/app/ransel/shared/domain/Validation.kt` (422 + details), request logging, CORS for the web dev origin. A dummy principal = `LOCAL_USER_ID` until Phase 3.
- [x] **B2.4 Trips.** `GET/POST /trips`, `GET/PUT/DELETE /trips/{id}` with `TripDetailDto` (summary numbers computed in SQL). Creating a trip also inserts the OWNER row in `trip_members`, so `requireRole` works the same in every phase.
- [x] **B2.5 Itinerary.** List (ordered by `day_date NULLS FIRST, sort_order`), create, replace (also used to schedule), delete, `PUT /trips/{id}/days/{date}/order` in one transaction.
- [x] **B2.6 Packing.** List, create, PATCH, DELETE, `POST .../packing/uncheck-all`.
- [x] **B2.7 Expenses.** List, create, replace, delete (splits stored but only validated: empty or sum = amount → `SPLIT_TOTAL_MISMATCH`), `GET .../expenses/summary` (all categories, by day, converted with `convertMinor`).
- [x] **B2.8 Tests.** `testApplication` + real Postgres for every route: happy path, validation error, not found, soft delete hidden from lists. `.http` files per resource.
- [ ] **B2.9 Deploy.** Dockerfile, `docker-compose.prod.yml` (server, db, caddy), GitHub Actions deploy, daily `pg_dump`. Needs VPS + domain from the owner — ask, do not guess.

**Done when:** every Phase 2 endpoint works against Postgres, tests are green, and the API is reachable over HTTPS for the web app.

## Phase 3 — Auth and sync

- [x] **B3.1 Auth.** `register`, `login`, `refresh` (rotating refresh tokens stored as SHA-256 hashes), `logout`; bcrypt; short-lived JWT access token; `INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`, `TOKEN_EXPIRED`. Rate-limit login attempts.
- [x] **B3.2 Me.** `GET/PATCH /me` (display name, default currency for new trips).
- [x] **B3.3 Real principal.** Replace the dummy user with the JWT subject; every route except health/auth requires a token; `requireRole` per trip.
- [x] **B3.4 Sync.** `POST /sync` (per-row last-write-wins, access check per trip, one transaction per batch, accepted/rejected lists) and `GET /sync?since=` (every visible row changed after `since`, deletes included, `serverTime`). Test the scenarios in TRD "Testing".
- [ ] **B3.5 Password reset.** `forgot` (always 204, token valid 30 min, single use, stored hashed) and `reset` (new hash, token used, revoke all refresh tokens). Email sender behind an interface; until the provider is chosen, a dev implementation that logs the link.

**Done when:** a trip created offline on a phone shows up on the web once the phone is online.

## Phase 4 — Multi-member

- [ ] **B4.1 Invites.** Create (OWNER, code from `generateInviteCode`, EDITOR/VIEWER), list with usage, revoke, preview, accept (checks in order: revoked → expired → full → already member; insert member + `used_count + 1` in one transaction).
- [ ] **B4.2 Members.** List, change role, remove (keeps their expenses and splits), leave (`OWNER_CANNOT_LEAVE`), transfer ownership (old owner → EDITOR). Sync pull includes `members`.
- [ ] **B4.3 Shared expenses.** `paidByUserId` must be a member; splits only for members; validation as Phase 2.
- [ ] **B4.4 Balances & settlements.** `GET /balances` with `computeBalances` + `fewestTransfers` (base currency), `GET/POST /settlements`; settlements also sync.
- [ ] **B4.5 Packing templates.** Seed the three built-in templates (owner_id NULL) in a repeatable seed, list (built-in + own + shared with my trips), create, apply (`skipExisting`).
- [ ] **B4.6 App links.** Serve `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` for `https://ransel.app/j/*` (values from the frontend agent).

**Done when:** two accounts manage one trip and the split-bill result is correct (unit + API tests with the PRD example numbers).

## Phase 5 — Explore AI

- [ ] **B5.1 Quota.** `ai_usage` upsert per user per WIB date; 10 per day; `newSearch=false` batches do not count; 429 `AI_QUOTA_EXCEEDED` with the reset time; `QuotaDto` in every response.
- [ ] **B5.2 LLM client.** Prompt asking for JSON matching the shared schema; parse into `@Serializable` classes; retry once; then 503 `AI_UNAVAILABLE`. Model and key from env.
- [ ] **B5.3 Grounding.** Places Text Search (IDs Only) per candidate; drop places not found; exclude liked/skipped placeIds for the trip; lazy details (photo, rating) for the top 2–3 only. Never expose the Places key in `photoUrl` (add a proxy endpoint through a contract change if photos need it).
- [ ] **B5.4 Cache.** `recommendation_cache` keyed by the normalized hash, expiry a few days, cleanup job.
- [ ] **B5.5 Feedback.** `POST /recommendations/feedback` (upsert), `DELETE .../{tripId}/{placeId}` (undo).
- [ ] **B5.6 Observability & cost.** Metrics for LLM/Places calls, cache hit rate, 429s; remind the owner to set a Google Cloud budget alert.

**Done when:** recommendations are relevant, every place is real (validated), and saved places can be scheduled in the itinerary.
