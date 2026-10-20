# RANSEL

Trip planner: one trip holds its itinerary, packing list and expenses; friends can join a trip and split costs; Explore AI suggests places you pick by swiping. Compose Multiplatform app (Android, iOS, Web/Wasm) + Ktor backend, designed with the IRIS design system. Product owner: SEN (Indonesian; UI copy is Indonesian first, English second).

Two Claude Code agents work in this repo:

| Agent | Owns | Reads |
| --- | --- | --- |
| **backend** | `server/` | `shared/`, `docs/` |
| **frontend** | `composeApp/`, `iosApp/` | `shared/`, `docs/` |

`shared/` is the contract between them. See "Changing the contract" below.

## Where things are

| Need | File |
| --- | --- |
| What to build and why, business rules | `docs/PRD.md` |
| How to build it: architecture, data model, API, sync, auth, AI | `docs/TRD.md` |
| Roadmap, phases, AI cost notes, folder layout | `docs/APP-SPEC.md` |
| Your task list per phase | `docs/tasks/backend.md`, `docs/tasks/frontend.md` |
| Screens (HTML per screen) and how to read them | `docs/design/README.md` |
| IRIS tokens (color, type, spacing, radius, shadow) | `docs/design/iris/tokens.md` |
| Itinerary/packing category rules, drag-and-drop rules | `docs/specs/itinerary-spec.md`, `docs/specs/packing-spec.md` |
| Agreed design decisions (Indonesian) | `docs/specs/design-decisions-id.md` |
| Database schema | `server/src/main/resources/db/migration/V1..V4` |
| API paths, DTOs, enums, error codes, domain logic | `shared/src/commonMain/kotlin/app/ransel/shared/` |

The docs are exports of living documents (claude.ai Docs + Claude Design canvas, 14 Oct 2026). If a doc and the code disagree, the code in `shared/` and the migrations win for data shapes; the PRD wins for behaviour. Report the mismatch instead of silently picking one.

## Ground rules (both agents)

- **Phases are sequential product milestones** (PRD "Scope per phase"). Phase 1 is offline-only on Android + iOS; the server is not needed until Phase 2. Do not build features from a later phase early.
- **Web is online-only**: no local database, signing in required, joins in Phase 2. Android and iOS are offline-first with Room 3.
- **Money** is a `Long` in the minor unit of its currency (`amountMinor`; IDR has 0 decimals in this app: Rp50.000 = 50000). Never `Double` for stored amounts. Conversion helpers are in `shared/.../domain/Money.kt`.
- **IDs** are UUID strings created on the client, also for POST. Every synced table has `createdAt`, `updatedAt`, `deletedAt` (soft delete).
- **Enums** (`Category`, `PackingCategory`, `PaymentMethod`, `Role`, ...) serialize as their constant names and match the SQL CHECK constraints. Expenses and itinerary share `Category`; only the UI labels differ.
- **Dates/times on the wire**: `LocalDate` "2026-11-20", `LocalTime` "09:00", `Timestamp` (kotlin.time.Instant) "2026-10-13T05:30:00Z" UTC.
- **Errors**: HTTP status decides success; failure body `{"error":{"code","message","details"}}` with a code from `ErrorCode`. The app switches on the code, never on the message.
- **Business logic that must match on both sides lives in `shared/domain`** (reorder, scheduling, split bill, balances, password rule, invite codes). Do not re-implement it in `server/` or `composeApp/`. Tests: `shared/src/commonTest`.
- **Secrets** (JWT secret, DB password, LLM key, Places key, email key) only in server environment variables. Never in the app, never in git.
- **Language**: everything in code is English: package, class, function, composable, variable, route, DB column, API field, resource/string ID, test and file names, plus comments, commit messages, branch names and docs. Indonesian appears only as UI copy in `values-in/strings.xml` (default design copy; English copy in `values/`), and inside quotes when a comment cites on-screen text. Design file names (`Masuk.html`, `Pengeluaran.html`, ...) and the Indonesian design-decisions doc are references, not names to copy: use the English **Code name** column in `docs/design/README.md`. String IDs are English too (`sign_in_title`, not `masuk_judul`).

## Changing the contract (`shared/`)

1. Only change `shared/` in a commit of its own, with a line in `shared/CONTRACT_CHANGES.md` (date, what, why, which agent).
2. Additive changes (new optional field with a default, new endpoint, new error code) can go in directly; tell the other agent in the changelog.
3. Breaking changes (rename, remove, type change, new required field) need the product owner's OK first.
4. Keep `ApiRoutes`, the DTO KDoc and `docs/TRD.md` "API" in step; if you add an endpoint, add the row to the TRD export too.

## Decisions already made (do not reopen without the product owner)

Room 3 (`androidx.room3`) for local DB on Android/iOS only · web online-only · Explore requires an account and always saves into a trip · Explore costs credits (signup grant 30, free 3 per day reset 00:00 WIB, new search 3, next batch 1; top-up by voucher first, payment later; all in server config, see TRD "Quota") · expense categories reuse the itinerary enum with noun labels · drag reorder keeps durations and packs the range · unscheduled places are scheduled with a button + sheet (end of day, empty day 09:00) · swipe cards have no rotation · trip screens have a `group_add` members button in the header · invite links `https://ransel.mietek.tech/j/<code>` · deploy on the owner's VPS with Docker Compose behind the host's nginx (API at `ranselapi.mietek.tech`, web at `ransel.mietek.tech`).

## Still open (ask, do not decide)

Email provider for password reset (Phase 3) · VPS details and domain DNS (owner is preparing them) · success-metric targets · the screens listed under "Not designed yet" in `docs/design/README.md`.
