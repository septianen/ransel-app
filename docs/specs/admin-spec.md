# Admin App Spec ("Karung") — RANSEL

Oct 21, 2026 · @SEN · Status: **approved 21 Oct 2026; stage A built on the server** (contract `56e9993`) (tasks: `docs/tasks/backend.md` and `docs/tasks/frontend.md`, "Phase 6 — Admin"). Design: Claude Design canvas "Karung" (https://claude.ai/artifact/GJbx3VCHbQUVPgkHzmnHbu, version 24).

## Summary

Karung is RANSEL's internal admin web app. It lets the owner watch the product (users, Explore AI usage and cost, errors), help a single user (adjust credits, sign them out, send a password reset, deactivate the account), change credit settings without a deploy, and prepare app copy that the server will deliver later. It is web only, online only, and used by admins, never by travellers.

Decisions agreed on 21 Oct 2026:

- Explore uses **credits** everywhere in Karung; there is no daily quota any more (TRD "Quota").
- **Adjust credits** replaces "reset quota": an admin can add **or** subtract wallet credits for one user, with a required reason.
- The error message list in Karung is the full `ErrorCode` list in `shared/` (no invented codes).
- Password reset emails are sent from **`no-reply@mietek.tech`**.
- **Credit settings**: `CREDIT_*` environment variables stay the defaults; values set in Karung are stored in the database and win over them.
- **Remote content** (strings, error messages, categories, destination interests) is built in Karung and the server first; the apps start reading it in a later, separate frontend task.
- Karung needs no new design tokens: it uses IRIS like the apps.

Also agreed on 21 Oct 2026 (the former open items):

| Topic | Decision |
| --- | --- |
| Who is an admin | `ADMIN_EMAILS` env var (comma separated, case-insensitive) on the server. An admin signs in with their normal RANSEL account; every `/admin` request looks the account up and checks its email against the list (no token claim, so removing an email works at once). The account must be active. No UI grants admin, so a bug in Karung cannot create admins. |
| Where Karung runs | Its own subdomain `karung.mietek.tech` behind the host's nginx, same Docker Compose. A separate origin from the traveller web app, so their sessions never mix. |
| Code module and owner | New module `adminApp/` (Compose for Web, Wasm) with the IRIS theme, owned by the **frontend** agent. It reads `shared/` like `composeApp/`. |
| Phase | "Phase 6 — Admin", after Phase 5, built in the stages under "Rollout". Stage A starts now. |
| Design items with no backing feature | "masuk dengan Google" and "Antrean sinkron" were removed from the design (version 24). "Mode perawatan" stays in the design but is not built in this phase (the apps have no maintenance banner yet). |

## Still open

- **Email provider** (CLAUDE.md "Still open"). Until it is chosen, "Send password reset" is hidden in Karung and the server keeps logging the link (`LoggingEmailSender`).

## Screens

Code names follow `docs/design/README.md` (English names in code, design file names are references only).

| Design file | Code name | Stage | What it shows |
| --- | --- | --- | --- |
| `Main` (Ringkasan) | `OverviewScreen` | B | Daily/7-day active users, new trips, AI searches today, credits used today, AI cost vs monthly budget, active users chart, top destinations, errors of the last 24 h, cost per service |
| `Pengguna` | `UsersScreen` | A | Searchable, filterable, paged user table; CSV export |
| `PenggunaDetail` | `UserDetailScreen` | A (recent activity: B) | Profile, trips (metadata only), credit history, devices, actions; the recent activity list needs the stage B logs |
| `PenggunaAturKredit` | `AdjustCreditsDialog` | A | Add or subtract credits (rules below) |
| `JelajahAI` | `ExploreAiScreen` | B | Searches, cache rate, saved cards, latency, users out of credits, credits used, daily cost, user choices, recent searches |
| `Sistem` | `SystemScreen` | B (health, errors), C (settings) | Health, errors per code, settings including credit settings |
| `KontenTeks` | `ContentStringsScreen` | D | Server-delivered UI strings, ID and EN, draft/publish |
| `KontenError` | `ContentErrorsScreen` | D | Title/message/button per `ErrorCode`, ID and EN, draft/publish, phone preview |
| `KontenKategori` | `ContentCategoriesScreen` | D | Label, icon, color, order per `Category` / `PackingCategory` |
| `KontenMinatKhas` | `ContentInterestsScreen` | D | Curated "Khas <destination>" chips per destination and month |
| `Sidebar` | `AdminSidebar` | A | Navigation, signed-in admin, environment badge |

Common rules: every page needs a signed-in admin; a 401 goes to sign-in, a 403 shows "Bukan akun admin". Lists are paged on the server (25 per page). Times show in WIB. Money in USD for provider costs (that is how providers bill), credits as plain integers.

## Adjust credits

| Topic | Rule |
| --- | --- |
| What changes | Only the wallet balance (`credit_wallets.balance`). The free daily allowance is never touched. |
| Direction | `ADD` or `SUBTRACT`, amount an integer 1–10,000. |
| Floor | A subtraction may not make the balance negative. More than the balance → 422 `VALIDATION_FAILED`, `details: {"amount": "EXCEEDS_BALANCE"}`; the dialog shows "Saldo tidak bisa minus. Paling banyak <n> kredit." No partial subtraction. |
| Reason | Required, 3–200 characters, stored on the ledger row and in the audit log. |
| Idempotency | The dialog sends a client UUID `requestId`; the same id twice changes the balance once (`credit_ledger.idempotency_key = "admin:<requestId>"`). |
| Locking | Same as every wallet change: lock the wallet row (`SELECT ... FOR UPDATE`), write the ledger row with `balance_after`. |
| Ledger | `kind = ADJUSTMENT`, `delta` = +amount or −amount, `ref` = admin user id, new `note` = reason. |
| Who sees it | The user's own credit history (`GET /credits/ledger`) shows the row as "Penyesuaian admin" / "Admin adjustment" with the sign; the reason is admin-only. |
| Deleted or deactivated user | Allowed (support may need to correct a balance before reactivating). |

Today `credit_ledger` has `CHECK ((kind = 'SPEND') = (delta < 0))`, which forbids a negative ADJUSTMENT. The migration changes it to: SPEND is always negative, ADJUSTMENT may be either sign, every other kind is positive. `REFUND` stays positive.

## Credit settings

| Key | Env default (today) | Allowed |
| --- | --- | --- |
| `signupGrant` | `CREDIT_SIGNUP_GRANT` = 30 | 0–1,000 |
| `dailyFree` | `CREDIT_DAILY_FREE` = 3 | 0–100 |
| `costSearch` | `CREDIT_COST_SEARCH` = 3 | 0–100 |
| `costNextBatch` | `CREDIT_COST_NEXT_BATCH` = 1 | 0–100 |
| `costPlaceDetail` | `CREDIT_COST_PLACE_DETAIL` = 0 | 0–100 |

- Effective value = database override if set, else the env var, else the built-in default. "Kembalikan bawaan" removes the override.
- Stored in a new `app_settings` table (key, JSON value, `updated_by`, `updated_at`); read through one `SettingsService` with an in-memory cache that is refreshed on every write (one server instance today).
- A change applies to the next charge or grant; existing balances and already granted signup credits do not change. `GET /credits` returns the effective costs, so the apps follow without a release.
- The other settings on the System screen use the same table: AI monthly budget (USD), alert threshold (80/90/100%), behaviour when the budget is used up (cache only vs keep going), suggestions limit per hour, LLM model (from an allow-list in server config, never free text). Each value overrides its env var the same way.

The TRD "Quota" paragraph ("All numbers come from server config") must be updated to describe this override.

As built (B9.8, 30 Oct 2026): keys are `SettingKey` constant names (`SIGNUP_GRANT`, `DAILY_FREE`, `COST_SEARCH`, `COST_NEXT_BATCH`, `COST_PLACE_DETAIL`, `COST_RECEIPT`, `AI_MONTHLY_BUDGET_USD`, `AI_BUDGET_ALERT_PERCENT`, `AI_BUDGET_MODE`, `SUGGESTIONS_PER_HOUR`, `LLM_MODEL`), values are text checked by `shared/domain/AdminSettings.kt`. The receipt photo cost (`COST_RECEIPT`, 0–100) was added at the owner's request (30 Oct 2026): it uses AI too. `AI_BUDGET_MODE` = `KEEP_GOING` (warning only) or `CACHE_ONLY`: once the month's estimated spend reaches the budget every LLM call answers 503 `AI_PAUSED` (cached searches still answer, new searches and receipt photos stop, no credits charged; interest suggestions and place texts fall back as when the LLM is down). The model allow-list is `LLM_MODELS` plus `LLM_MODEL`. The history is the audit log (`GET /admin/audit?action=SETTINGS_UPDATE`, before/after as text).

## Other user actions

| Action | Effect | Needs |
| --- | --- | --- |
| Add user | Admin enters name + email (`POST /admin/users`). The account is created with the email already verified and a password nobody knows; the user gets an invite email from `no-reply@mietek.tech` with a 7-day link to set the password (the `/reset-password` flow). Works while public sign-up is closed. 409 `EMAIL_ALREADY_REGISTERED` for a taken email (case-insensitive). No credits beyond the signup grant: use "Adjust credits". Decided 22 Oct 2026. | Email set up (button hidden and 409 otherwise) |
| Sign out of all devices | Revokes all of the user's refresh tokens; their apps get `TOKEN_EXPIRED` at the next refresh | Nothing new |
| Send password reset email | Same flow as `POST /auth/password/forgot`, started by an admin; rate limited to 3 per user per hour | Email provider (still open) |
| Deactivate / reactivate account | New `users.disabled_at`. Deactivating revokes refresh tokens; sign-in (with the right password) and refresh then return 403 `ACCOUNT_DISABLED`; an access token already issued keeps working until it expires (15 minutes); data is kept. An admin cannot deactivate their own account. Reactivating clears the field. | New error code, app handling (show "Akun dinonaktifkan" on the sign-in screen) |

Every action asks for confirmation in Karung and writes an audit row.

## Audit log

New table `admin_audit_log`: id, admin user id, action (`CREDITS_ADJUST`, `SETTINGS_UPDATE`, `USER_SIGN_OUT_ALL`, `USER_DISABLE`, `USER_ENABLE`, `PASSWORD_RESET_SEND`, `CONTENT_PUBLISH`, `USERS_EXPORT`), target (user id / setting key / content key), JSON before/after, created_at. Append-only; nothing in Karung edits or deletes it. Shown on the user detail page (actions on that user) and on the System page (settings changes).

## Monitoring data (stage B)

Today the server keeps AI counters only in memory and logs them hourly (`AiMetrics`), so nothing survives a restart. Karung needs stored data:

| Need | Source |
| --- | --- |
| Active users | New `users.last_seen_at`, updated at most once per hour per user on an authenticated request |
| Platform per user | Most recent `devices.platform` or refresh token `device_name`; "Web" when the last sign-in was the web app |
| AI searches, cache rate, latency, saved cards, credits charged, estimated provider cost | New `ai_search_log` row per search: user, trip id, destination, price level, interests, new/next batch, source (`NEW`, `CACHE`, `FAILED`, `NO_CREDITS`), cards, latency ms, credits charged, estimated cost in micro-USD. Saved count from `recommendation_feedback`. No prompt text. |
| Errors per code | New `error_counts` table: hour, code, route template, count, distinct users; incremented by `StatusPages`. Routes as templates (`/trips/{id}/expenses`), never with ids. |
| Cost per service | LLM and Places from `ai_search_log` estimates (per-call prices in server config); "Server dan database" is a fixed monthly amount from settings |
| Health | Uptime and p95 latency from the server process (since start), DB size from Postgres, last backup time from the backup script's marker file, Flyway version from the schema history table |

Retention: `ai_search_log` 180 days, `error_counts` 90 days, cleaned by the existing scheduler.

As built (B9.6/B9.7, 29 Oct 2026): ranges are `AdminRange` constant names; `user_active_days` (one row per user per WIB day) was added because `last_seen_at` alone cannot draw active users per day; provider cost comes from `provider_usage` (every paid call per day, photos and interest suggestions included), the search rows keep their own estimate; the AI budget for the warning is `AI_MONTHLY_BUDGET_USD` until stage C; health shows requests, 5xx and p95 of the last 24 hours since the process started (no 30-day uptime: that needs an outside probe).

## Remote content (stage D: Karung and server only)

- Tables: `content_strings` (key, locale `id`/`en`, value, status `DRAFT`/`PUBLISHED`, updated_by, updated_at), `content_errors` (code, locale, title, message, button, illustration, display `FULL_SCREEN`/`SNACKBAR`/`DIALOG`, status), `content_categories` (enum type + constant, locale label, icon name, color token, sort order), `destination_interests` (destination, month, chips, curated by).
- Only keys and codes the app already knows can be edited: string keys from a list the frontend exports, codes from `ErrorCode`, categories from the enums. Karung cannot add a category, only change how it looks.
- Placeholders (`{name}`, `{cost}`, `{available}`, `{resetsAt}`, ...) must match the default text; publishing with a missing or unknown placeholder is refused (422 `VALIDATION_FAILED`, `details: {"value": "PLACEHOLDER_MISMATCH"}`).
- "Terbitkan" publishes all drafts at once and bumps a content version number.
- The read endpoint for the apps (`GET /content?version=`) is specified now but the apps do not call it until the later frontend task; until then the bundled strings stay the only source. Curated destination interests are used by the server's `GET /explore/suggestions` as soon as they are published (server side only, no app change).

As built (B9.9, 30 Oct 2026): the editable keys are every `<string>` of composeApp with its bundled ID and EN text, exported by `scripts/export-content-catalog.py` to `server/src/main/resources/content/catalog.json` (run it after adding strings or drawables; it also lists the illustrations an error screen may use). A string row holds both languages (`value_id`, `value_en`) instead of one row per locale; placeholders are the app's own (`%1$s`, `%1$d`) and must match the bundled text, checked when a draft is saved and again at publish. Error messages may use `{cost}`, `{available}`, `{resetsAt}`, `{maxBytes}`, `{usedBytes}`, `{limitBytes}`; codes come from `ErrorCode.ALL`. Categories (itinerary and expense labels over `Category`, plus `PackingCategory`) and destination interests are saved live, without drafts, as in the boards ("Simpan dan terbitkan", "Simpan kurasi"); every live change is a `CONTENT_PUBLISH` audit row, and categories and publishes bump the version. Interests are curated per destination, month of the year (the AI cache is per `YYYY-MM`) and language; `GET /explore/suggestions` puts pinned and manual chips first and never shows hidden ones. "Buat ulang dengan AI" drops the cached answer for the coming such month and asks again. Not built: "Dipakai" and "dipilih %" per chip (searches do not record which month a destination was asked for).

## API

All under `/admin`, bearer token with the admin claim (403 `FORBIDDEN` otherwise), JSON, same error envelope. Paths go into a new `AdminRoutes` object in `shared/` with DTOs in `shared/.../dto/AdminDtos.kt`.

| Method | Path | Purpose | Stage |
| --- | --- | --- | --- |
| GET | /admin/me | Admin profile and environment (production/staging) | A |
| GET | /admin/users?query=&status=&platform=&joinedFrom=&joinedTo=&noCredits=&page= | User list | A |
| POST | /admin/users | Add a user: `CreateUserRequest(email, displayName)` → 201 `AdminUserDto`, sends the invite (audited `USER_CREATE`) | A |
| GET | /admin/users/export.csv (same filters) | CSV export (audited) | A |
| GET | /admin/users/{id} | Profile, counts, devices, trips metadata (recent activity comes with stage B) | A |
| GET | /admin/users/{id}/credits/ledger?page= | Credit history with reasons | A |
| POST | /admin/users/{id}/credits | Adjust credits `{requestId, direction, amount, reason}` → `CreditsDto` | A |
| POST | /admin/users/{id}/sign-out-all | Revoke refresh tokens | A |
| POST | /admin/users/{id}/disable · /enable | Deactivate / reactivate | A |
| POST | /admin/users/{id}/password-reset | Send reset email | A (hidden until provider) |
| GET | /admin/audit?targetId=&action=&page= | Audit log | A |
| GET | /admin/overview?range=TODAY\|LAST\_7\_DAYS\|LAST\_30\_DAYS | Overview numbers and charts (`AdminOverviewDto`) | B (built) |
| GET | /admin/explore?month=YYYY-MM | Explore AI page (`AdminExploreDto`) | B (built) |
| GET | /admin/explore/searches?userId=&page= | Recent searches (`AdminSearchDto`); `userId` for the user page's recent activity | B (built) |
| GET | /admin/system?range=LAST\_24\_HOURS\|LAST\_7\_DAYS | Health + errors per code (`AdminSystemDto`) | B (built) |
| GET · PUT | /admin/settings | Effective settings with their source (`ENV`/`OVERRIDE`); PUT sets or clears overrides | C |
| GET · PUT | /admin/content/strings, /errors, /categories, /interests | Read and save drafts | D |
| POST | /admin/content/publish | Publish all drafts | D |
| GET | /content?version= | Published content for the apps (public, cached) | D (apps later) |

Admin endpoints are rate limited per admin (60 requests a minute) and never cached by nginx. `ADMIN_APP_ORIGIN` is added to the CORS origins; CORS is not the security boundary (bearer tokens, no cookies), the per-request admin check is.

## Contract changes (`shared/`, all additive)

- `AdminRoutes` and `dto/AdminDtos.kt` (new).
- `CreditLedgerEntryDto.note: String? = null` (filled only in admin responses) and the KDoc rule that ADJUSTMENT may be negative.
- `ErrorCode.ACCOUNT_DISABLED` (403).
- New `VALIDATION_FAILED` detail values (no new code): `EXCEEDS_BALANCE`, `PLACEHOLDER_MISMATCH`.
- `ContentDto` for `GET /content` (stage D).

**Frontend (traveller app):** label negative ADJUSTMENT rows in the credit history; handle `ACCOUNT_DISABLED` on sign-in/refresh. Remote content stays a later task.

## Privacy and security

- Karung never shows trip content (itinerary text, notes, packing items, expense descriptions or amounts), only counts and dates, as in the design.
- Search rows show parameters only, never prompt text.
- CSV export contains name, email, joined, last active, platform, trips count, credits; every export is audited.
- Admin JWTs live as long as normal access tokens; no "remember me" in Karung.
- The environment badge ("Produksi"/"Staging") comes from the server, so an admin always sees which database they are changing.
- One Karung manages both servers: on `karung.mietek.tech` the admin picks Production or Staging on the sign-in screen (or from the badge menu in the sidebar). The choice is per browser tab; switching **signs out** of the current server first and reloads on the other one, and refresh tokens are stored per server. While Staging is chosen an amber strip names the server; if the server reports a different `APP_ENV` than the one picked, the strip turns red.

## Rollout

| Stage | Contents | Depends on |
| --- | --- | --- |
| A — Users and credits | Admin auth, sidebar, users list/detail, adjust credits, sign out all, deactivate, audit log, ledger migration | — |
| B — Monitoring | `last_seen_at`, `ai_search_log`, `error_counts`, overview, Explore AI, system health and errors | A |
| C — Settings | `app_settings`, credit settings override, AI budget settings | A |
| D — Content (admin side) | Content tables, editors, publish, `GET /content`, curated interests used by suggestions | A |
| E — Content in the apps | Apps read `GET /content` with bundled strings as fallback | D; separate frontend task |
| — | Password reset email from Karung | Email provider |

## Docs updated with this spec

PRD (Phase 6, "Admin (Karung)"), TRD (Quota override, API, data model, auth), APP-SPEC (Phase 6, `adminApp/`), `docs/design/README.md` (Karung), CLAUDE.md (decisions), `docs/tasks/backend.md` and `docs/tasks/frontend.md` (Phase 6 tasks). The admin API rows are marked "6" in the TRD and are not in `shared/` yet: they go in with stage A's contract commit.
