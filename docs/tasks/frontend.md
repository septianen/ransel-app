# Frontend tasks (composeApp/)

Work top to bottom. Tick `[x]` when done and keep this file in the repo as the progress log. Screen files are in `docs/design/screens/` (index: `docs/design/README.md`). Each phase ends with its **Done when** check; tell the owner when a phase is done. Screen names below (`Masuk`, `AnggotaTrip`, ...) are the design file names; in code use the English **Code name** from the design README (`SignInScreen`, `MembersScreen`, ...).

## Setup (once, before Phase 1)

- [x] **F0.1 Project.** If the repo is still empty: generate from kmp.jetbrains.com (Android, iOS, Web, Server; package `app.ransel`), then copy this handoff package over it (`docs/`, `CLAUDE.md` files, `shared/src`, `server/src/main/resources/db/migration`, `composeApp/src/commonMain/kotlin/app/ransel/ui/theme`).
- [x] **F0.2 Shared module.** Dependencies: kotlinx-serialization (plugin + json), kotlinx-datetime; targets android, iOS, wasmJs, jvm (server). Make `./gradlew :shared:allTests` pass (8 domain tests). *(Done: jvm, Android unit, wasm pass; iOS targets only compile on macOS.)*
- [x] **F0.3 Source sets.** `mobileMain` intermediate set for Android + iOS; Room 3 + KSP only there.
- [x] **F0.4 Shell.** Koin, Navigation Compose with the four tabs (Beranda, Trip, Jelajah, Akun), `IrisTheme` with Plus Jakarta Sans fonts, Material Symbols Rounded, `values/` + `values-in/` strings.
- [x] **F0.5 Shared components.** Build these in `ui/components/` before the first screen, each stateless and each with an `IrisPreview` (light + dark) `@Preview`. Take sizes, colors and states from the screens listed:
  - `FullScreenState`: illustration + headline + body + optional primary button (`KosongBeranda`, `StateError`, `SwipeKuota`, `StateComingSoon`, ...)
  - `TripHeader`: trip title, dates, tabs Itinerary / Packing / Expenses, `group_add` slot hidden until Phase 4 (`Itinerary`, `Packing`, `Pengeluaran`)
  - `CategoryIcon`: colored tile per `Category` / `PackingCategory` (`Itinerary`, `Pengeluaran`, `Packing`)
  - `MoneyText`: formats `amountMinor` + currency with `shared/domain/Money.kt` (`Rp50.000`)
  - `BudgetProgress`: remaining budget + bar (`Main`, `Pengeluaran`)
  - `AdaptiveSheet`: `ModalBottomSheet` on compact; the expanded variant (side sheet / centered dialog) is added in Phase 2
  - `ConfirmDialog`: title, body, destructive or normal confirm (`DialogHapusTrip`, `DialogUbahUrutan`)
  - `SectionHeader`: title + optional "see all" link (`Main`, `Itinerary`)
  - `UndoSnackbar`: message + Undo action (`HasilUrutan`)
  - `FormScaffold`: scrolling form + bottom save button that is never squeezed (`BuatTrip`, `TambahAktivitas`)
  - **Component Gallery**: a debug-only screen listing every component above in both themes, for checking against the design.

  `MemberAvatar` (initials + fixed color per person) waits for F4.1.

## Phase 1 — Offline app (Android + iOS, no server, no account)

- [x] **F1.1 Local data.** Room entities for trips, itinerary items, packing items, expenses (columns mirror V1 + a local `syncState`, unused until Phase 3); repositories behind interfaces; `LOCAL_USER_ID` as owner/payer.
  - Done. Shared DTOs are the domain models; Room 3 in `mobileMain`, in-memory fakes in `commonMain` (previews, tests, web until F2.2).
- [x] **F1.2 Home & My trips.** `Main`, `KosongBeranda`, `DaftarTrip`, `KosongTrip`, `TripSelesaiSaja`. Hide AI entry points (Coba callout, "Cari inspirasi dengan AI") until Phase 5.
  - Done. Hidden until Phase 5: the AI callout, "Cari inspirasi dengan AI" buttons, the unscheduled column of "Persiapan trip". Hidden because their targets are not designed: the notification bell and the search button.
- [x] **F1.3 Trip CRUD.** `BuatTrip`, `PilihRentangTanggal`, `EditTrip`, `DialogHapusTrip` (counts from the local DB).
  - Done. Not built: cover **photo** upload (needs a platform file picker + storage); a generated cover is shown instead. Added: the pencil in the trip header opens Edit trip (the design shows no entry); the base currency is locked once a trip has expenses; shortening the dates moves activities to Belum dijadwalkan (`shared/domain/TripDates.kt`).
- [x] **F1.4 Itinerary.** `Itinerary` (day chips, timeline, category tiles), `KosongItinerary`, `TambahAktivitas`, `PilihJam`; drag reorder `SeretAktivitas` → `DialogUbahUrutan` (with "Jangan tanya lagi") → `HasilUrutan` (Undo) using `shared/domain/reorder`; drag onto a day chip to move day; "Belum dijadwalkan" box, `BelumDijadwalkanSemua`, `JadwalkanTempat` using `scheduleAtEndOfDay` (manual unscheduled items for now).
  - Done. Drag and drop is custom `pointerInput` (TRD), not Reorderable: dragging onto a day chip needs hit-testing against the chips. Also built: activity form with edit/delete, move-to-day with Undo, new/re-timed activities slotted by start time, activities that cross midnight (see `itinerary-spec.md`). The AI filter in the unscheduled sheet and "Cari tempat lain" wait for Phase 5; place-specific tile icons are not offered.
- [x] **F1.5 Packing.** `Packing`, `KosongPacking`, `TambahBarang`, tick, progress, "Lengkap" tag; ⋮ menu with only "Hapus semua centang" for now.
  - Done. The ⋮ menu ("Hapus semua centang") sits in the progress card; long-press a row to edit or delete it.
- [x] **F1.6 Expenses.** `Pengeluaran` (remaining budget, donut by category, list by day), `StateEmpty`, `CatatPengeluaran` (amount, currency, category with expense labels, date via `PilihTanggal`, "Bayar pakai", note).
  - Done. Added a manual exchange-rate field for expenses in a currency other than the trip's (needed for `fxRateToBase`). "Lihat semua transaksi" is not designed yet, so it is omitted.
- [x] **F1.7 Onboarding & Account.** `Onboarding1–3` on first launch, then straight into the app; `Akun` without sync/sign-in rows; `SheetTema` (system/light/dark), `SheetMataUang` (default currency for new trips), language.
  - Done. Reminders (packing/activity) are left out: they need platform notification scheduling. Added "Konfirmasi ubah urutan" (re-enables the reorder dialog). The language sheet reuses the theme sheet pattern.
- [x] **F1.8 Quality.** Dark theme on every Phase 1 screen (`GelapBeranda`, `GelapPengeluaran` show the pattern); Compose UI tests: create trip, add activity, reorder, tick packing, record expense.
  - Done. Every screen uses scheme tokens, so dark works everywhere (checked on Home, Itinerary, Expenses, Account). UI tests: `composeApp/src/androidInstrumentedTest/.../Phase1FlowTests.kt` (run with `ANDROID_SERIAL=<emulator> ./gradlew :composeApp:connectedDebugAndroidTest`).

**Done when:** one real trip can be planned start to finish on an Android phone and on the iOS simulator.

> Status: all F1.x built and checked on an Android emulator (API 36). Still to confirm by the owner: a real trip on a physical phone, and everything on the iOS simulator (needs a Mac).

## Phase 2 — Web joins (online-only)

- [x] **F2.1 API client.** Ktor Client per platform, base URL per build type, `ApiResult` mapping from `ApiResponse`/`ApiErrorResponse`, timeouts → `NETWORK_TIMEOUT`.
  - Done. `RanselApi` + `createApiHttpClient` (commonMain `data/remote`), `ApiResult`/`ApiFailure`; timeouts → `NETWORK_TIMEOUT`, no answer → `NETWORK_UNAVAILABLE`. Base URL: `http://localhost:8080` on localhost, else the page origin, or `window.RANSEL_API_ORIGIN`. MockEngine tests in `RanselApiTest` (written, not run yet).
- [x] **F2.2 Web repositories.** `wasmJsMain` implementations that call the Phase 2 endpoints (dummy user, no login yet).
  - Done. `Remote*Repository` in `wasmJsMain` behind the same interfaces; every screen re-fetches after any write (shared invalidation counter). Preferences live in `localStorage`. `TripRepository.create` now returns Unit. Known cost: the trips list makes one detail request per trip.
- [x] **F2.3 Web layout.** ≥ 1240 dp: navigation rail 96 dp, content in the middle, 360 dp side panel (`WebDetailTrip`); ✕/♥ buttons and arrow keys where swiping is used later.
  - Done. ≥ 1240 dp: 96 dp rail replaces the bottom bar, trip detail gets a 360 dp side panel (budget + record expense, packing summary), sheets become centered dialogs, single-column pages are capped at 720 dp. The Unscheduled box stays in the itinerary column (scheduling lives there). ✕/♥ and arrow keys come with Explore (Phase 5).
- [x] **F2.4 States.** `StateError` (connection), `StateServerError`, `StateComingSoon`, showing `error.code` small.
  - Done. `ConnectionErrorState` / `ServerErrorState` / `ComingSoonState` with the capybara art and the API error code shown small. Web: any failed request shows the connection or server state over the app, with "Coba lagi" (re-fetch) and "Kembali ke Beranda".

**Done when:** the same trip flows from Phase 1 work in the browser against the deployed (or local) server.

> Status: verified against the local server (Postgres in Docker) in headless Firefox: list/open a trip, tick a packing item, record an expense (85.000), connection-error state. Not yet checked: Chrome/Safari, the deployed server.

## Phase 3 — Auth and sync

- [ ] **F3.1 Auth screens.** `Masuk` (with "Pakai tanpa akun dulu" on mobile only), `Daftar` (live password checklist from `checkPassword`), `LupaSandi`, `CekEmail` (resend countdown), `SandiBaru`, `SesiBerakhir` ("Nanti saja, pakai offline").
- [ ] **F3.2 Tokens.** Secure storage per platform, refresh on 401, logout.
- [ ] **F3.3 Sync (mobile).** Mark local writes `pending`, push on reconnect/app start, pull with `since`, apply LWW; `Offline` banner + `cloud_upload` markers; `KembaliOnline` snackbar with the count.
- [ ] **F3.4 Guest → account.** On first sign-in rewrite `LOCAL_USER_ID` to the user id, then push.
- [ ] **F3.5 Account.** Profile, sync status, sign out; web requires sign-in (no guest option).

**Done when:** a trip created offline on a phone shows up on the web once the phone is online.

## Phase 4 — Multi-member

- [ ] **F4.1 Members.** `group_add` button on every trip screen → `AnggotaTrip` (owner) / `AnggotaEditor`; `MenuAnggota`, `DialogKeluarkan`, `DialogKeluarTrip`, transfer ownership.
- [ ] **F4.2 Invites.** `UndangAnggota` (code + link + share sheet), deep links `https://ransel.app/j/<code>` (Android App Links, iOS Universal Links; send the needed `.well-known` values to the backend agent), `GabungTrip`, `UndanganTidakBerlaku` (one screen, title per error code).
- [ ] **F4.3 Shared expenses.** `PengeluaranBersama` (Ringkasan + "Posisimu"), `CatatPengeluaranBersama`, `CatatPengeluaranNominal` / `…Pas` (difference from `splitDifference`, Save disabled until 0), `SaldoTrip` (balances, "Supaya lunas", Tandai lunas).
- [ ] **F4.4 Packing templates.** `MenuPacking`, `PilihTemplate`, `SimpanTemplate`.
- [ ] **F4.5 Roles in UI.** VIEWER sees everything read-only; owner-only actions hidden for others.

**Done when:** two accounts manage one trip and the split-bill result is correct.

## Phase 5 — Explore AI

- [ ] **F5.1 Explore tab.** `PreferensiAI` (form prefilled from the trip), `JelajahLanjut` (Continue card + magenta dot on the tab), `JelajahTamu` (guest), `JelajahTanpaTrip` (no trip).
- [ ] **F5.2 Swipe.** `MemuatRekomendasi`, `Swipe` (no rotation, outline + SIMPAN/LEWATI label, throw at 40% width), `SwipeKanan`, `SwipeKiri`, `SwipeTersimpan`, Undo, prefetch at 3 cards left (`newSearch=false`), `GelapSwipe`.
- [ ] **F5.3 Save.** Swipe right creates the itinerary item (unscheduled, `source=AI`, placeId, lat/lng, `durationMin`) through the normal repository + sends feedback; swipe left sends SKIP; undo deletes both.
- [ ] **F5.4 States.** `SwipeHabis`, `SwipeError`, `SwipeTidakAda`, `SwipeKuota` ("10 dari 10").
- [ ] **F5.5 Entry points.** Show the Home callout, "Cari inspirasi dengan AI" buttons and the unscheduled count in "Persiapan trip".

**Done when:** recommendations can be swiped, saved places appear in "Belum dijadwalkan", and they can be scheduled into a day.

## Integration checkpoints with the backend agent

| When | Check together |
| --- | --- |
| End of backend B2.8 | Frontend F2.1 runs against the local server using the `.http` examples |
| End of Phase 3 | Sync scenarios from TRD "Testing" pass on a real phone + web |
| End of Phase 4 | Same trip on two accounts: invite, split, balances, settle |
| End of Phase 5 | Quota, error and empty states reachable from the app |
