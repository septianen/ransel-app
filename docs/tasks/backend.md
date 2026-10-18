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
- [x] **B2.9 Deploy.** Dockerfile, `docker-compose.prod.yml` (server, db; the VPS's existing nginx terminates HTTPS instead of Caddy), GitHub Actions deploy, daily `pg_dump`. Live at https://ranselapi.mietek.tech (closed beta: sign-up off, one seeded account). See `deploy/README.md`.

**Done when:** every Phase 2 endpoint works against Postgres, tests are green, and the API is reachable over HTTPS for the web app.

## Phase 3 — Auth and sync

- [x] **B3.1 Auth.** `register`, `login`, `refresh` (rotating refresh tokens stored as SHA-256 hashes), `logout`; bcrypt; short-lived JWT access token; `INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`, `TOKEN_EXPIRED`. Rate-limit login attempts.
- [x] **B3.2 Me.** `GET/PATCH /me` (display name, default currency for new trips).
- [x] **B3.3 Real principal.** Replace the dummy user with the JWT subject; every route except health/auth requires a token; `requireRole` per trip.
- [x] **B3.4 Sync.** `POST /sync` (per-row last-write-wins, access check per trip, one transaction per batch, accepted/rejected lists) and `GET /sync?since=` (every visible row changed after `since`, deletes included, `serverTime`). Test the scenarios in TRD "Testing".
- [x] **B3.5 Password reset.** `forgot` (always 204, token valid 30 min, single use, stored hashed) and `reset` (new hash, token used, revoke all refresh tokens). Email sender behind an interface; until the provider is chosen, a dev implementation that logs the link.

**Done when:** a trip created offline on a phone shows up on the web once the phone is online.

## Phase 4 — Multi-member

- [x] **B4.1 Invites.** Create (OWNER, code from `generateInviteCode`, EDITOR/VIEWER), list with usage, revoke, preview, accept (checks in order: revoked → expired → full → already member; insert member + `used_count + 1` in one transaction).
- [x] **B4.2 Members.** List, change role, remove (keeps their expenses and splits), leave (`OWNER_CANNOT_LEAVE`), transfer ownership (old owner → EDITOR). Sync pull includes `members`.
- [x] **B4.3 Shared expenses.** `paidByUserId` must be a member; splits only for members; validation as Phase 2.
- [x] **B4.4 Balances & settlements.** `GET /balances` with `computeBalances` + `fewestTransfers` (base currency), `GET/POST /settlements`; settlements also sync.
- [x] **B4.5 Packing templates.** Seed the three built-in templates (owner_id NULL) in a repeatable seed, list (built-in + own + shared with my trips), create, apply (`skipExisting`).
- [x] **B4.6 App links.** Serve `/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` for `https://ransel.mietek.tech/j/*` (values from the frontend agent). *Served from env (`ANDROID_CERT_SHA256`, `IOS_APP_IDS`); 404 until set. Release SHA-256 and Apple team id still missing, see `docs/specs/app-links.md`.*

- [x] **B4.7 Several payers per expense** (contract commit `fc96169`). `expense_payers` (**V7**): an expense paid by two or more members (amounts in the expense currency, each > 0, summing to the amount: 422 `PAYER_TOTAL_MISMATCH`; `NEEDS_AT_LEAST_TWO`, `DUPLICATE_USER`, `NOT_A_MEMBER` under `details.payers`; `paidByUserId` is always the first payer, `MUST_BE_FIRST_PAYER` if a client sends another). REST, sync push/pull and balances carry them (`convertPayers` keeps the converted parts exact; each payer is credited what they paid). No payers = `paidByUserId` paid all, so old rows and clients keep working. Tests: `MultiPayerRoutesTest` (database tests run in CI).

**Done when:** two accounts manage one trip and the split-bill result is correct (unit + API tests with the PRD example numbers).

## Phase 5 — Explore AI

- [x] **B5.1 Quota.** `ai_usage` upsert per user per WIB date; 10 per day; `newSearch=false` batches do not count; 429 `AI_QUOTA_EXCEEDED` with the reset time; `QuotaDto` in every response.
- [x] **B5.2 LLM client.** Prompt asking for JSON matching the shared schema; parse into `@Serializable` classes; retry once; then 503 `AI_UNAVAILABLE`. Model and key from env. *Provider is Gemini (Developer API), changed from DeepSeek on 2026-10-15; `LLM_MODEL`, `LLM_API_KEY`.*
- [x] **B5.3 Grounding.** Places Text Search (IDs Only) per candidate; drop places not found; exclude liked/skipped placeIds for the trip; lazy details (photo, rating) for the top 2–3 only. Never expose the Places key in `photoUrl` (add a proxy endpoint through a contract change if photos need it). *`photoUrl` is always null for now: photos need that proxy (open decision); lat/lng/address come from Place Details Essentials, rating only for the top 3 cards.*
- [x] **B5.4 Cache.** `recommendation_cache` keyed by the normalized hash, expiry a few days, cleanup job.
- [x] **B5.5 Feedback.** `POST /recommendations/feedback` (upsert), `DELETE .../{tripId}/{placeId}` (undo).
- [x] **B5.6 Observability & cost.** Metrics for LLM/Places calls, cache hit rate, 429s; remind the owner to set a Google Cloud budget alert. *Counters are logged per search and hourly (`ai.metrics ...`), not exposed over HTTP: there is no admin role to guard such an endpoint.*

- [x] **B5.7 Place photos.** Signed proxy `GET /api/v1/places/photo/{placeId}?exp=&sig=` (no bearer; HMAC-SHA256 over `placeId|exp`, valid 7 days, constant-time check). The server fetches the Place Photo (New) bytes and streams them, so neither the Places key nor Google URLs reach the app; 404 when the place has no photo or Google rejects (quota 429/403), never 5xx. `RecommendationItem.photoUrl` is set for every card (relative signed path), `photoAttribution` for the top 3 (photos added to the existing `withRating` field mask: no extra SKU). Small in-memory LRU cache (about 200 entries, 1 h), rate limit per IP, metrics `placesPhotoCalls` / `placesPhotoQuotaHits`. Tests: valid signature → 200 + bytes (fake `PlacesClient`), bad or expired signature → rejected, no photo → 404. Setup note in `deploy/README.md`: cap Place Photo requests per day (about 30) in Google Cloud Quotas + budget alert, so the free tier (1,000 photos/month) is never exceeded. Contract change (additive: `ApiRoutes.placePhoto`, `photoAttribution`) in its own commit with a `shared/CONTRACT_CHANGES.md` line and the TRD "API" row. *Own secret `PHOTO_SIGNING_SECRET` (required in production, 32+ chars; added to compose, deploy workflow and `deploy/README.md`: set the GitHub secret before the next deploy). The proxy needs two Google calls per uncached photo (resource name, then bytes), so the cache matters. Rate limit 120/min per IP.*

- [x] **B5.8 Explore per trip** (`docs/specs/AI-Recommendation-Spec.md`; contract commit `d01bdfc`, breaking, approved through the spec). Done: `GET /explore/suggestions` (cache table `explore_suggestions_cache` in **V5**, not V4: V1–V4 are already applied in production), one LLM call per destination + month + language (retry once, empty list on failure, not cached), 60 per user per hour → 429 `RATE_LIMITED`; price level from the trip budget through ECB rates (`EcbFxRates`, refreshed every 24 h in memory); cache warm-up when a trip is created or saved (`TripEvents`, also on sync push; the cache lookup makes repeats cheap); search uses the trip's destination and dates, role EDITOR, `TRIP_ENDED`/`EMPTY`/`TOO_MANY` validation, new cache key, prompt with focus area and extra interests, price filter on the LLM cost estimate (see cost decision). *Cost decision (16 Oct, owner): Google `rating` and `priceLevel` are Enterprise-tier Place Details fields (1,000 free a month, then $20 per 1,000), so neither is fetched. Place Details stays Essentials (10,000 free a month; `photos` is IDs Only and does not raise the tier). `rating` in `RecommendationItem` is always null. The price filter runs on the LLM's `costUsd` per place against the destination price guide (`explore_suggestions_cache`: `price_low_max_usd` / `price_mid_max_usd`): below the lower edge = LOW, above the upper = HIGH, else MEDIUM; drop more than one step from the chosen level (so MEDIUM keeps everything); free places and an unknown guide keep the place. Spec acceptance check "Tokyo `$`: none above Places level 2" can no longer be guaranteed (SEN to decide whether to make it a target, for example 90% of cards). The spec's "DeepSeek" price remark is outdated (Gemini).*

- [x] **B5.9 Place detail text** (contract commit `9706bc0`; design `DetailTempat`). The search prompt asks the LLM for `about`, `highlights`, `openingHours` (typical hours, an AI estimate), `bestTime` per candidate (malformed detail fields are dropped, never fail the search); `RecommendationItem` carries them plus `estimatedCostMinor`/`costCurrency` (the LLM `costUsd` converted to the trip currency with `FxRates.fromUsd`). Each search also upserts the text into `place_detail_cache` (**V6**, 30 days, by place id + language `id`). `GET /places/{placeId}/detail?name=&currency=` reopens a place from the itinerary: cache hit = no LLM call; missing/expired row = rebuilt by one small `LlmClient.describe` call (needs `name`, else 404 `NOT_FOUND`); an LLM failure still answers with what is known. Same per-user hourly limit as `/explore/suggestions`. Cache key got a `detail-v1` part so entries cached before this refill. *Not compiled or run here (no JDK/Docker on the dev machine): run `./gradlew :server:test --tests "*PlaceDetailTest*"` first.*

**Done when:** recommendations are relevant, every place is real (validated), and saved places can be scheduled in the itinerary.
