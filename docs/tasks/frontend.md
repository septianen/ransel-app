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

- [x] **F3.1 Auth screens.** `Masuk` (with "Pakai tanpa akun dulu" on mobile only), `Daftar` (live password checklist from `checkPassword`), `LupaSandi`, `CekEmail` (resend countdown), `SandiBaru`, `SesiBerakhir` ("Nanti saja, pakai offline").
  - Done 2026-10-15. Verified on web (register, wrong password, forgot → check email → reset via `/reset-password?token=`) and Android emulator. `feature/auth`; routes in `navigation/Routes.kt`; mobile shows them on top of the tabs, web gates the whole app (`SessionGate`). "Buka aplikasi email" opens `mailto:`.
- [x] **F3.2 Tokens.** Secure storage per platform, refresh on 401, logout.
  - Done 2026-10-15. `data/auth` (`SessionManager`, `TokenStore`): Android Keystore AES-GCM + private prefs (verified: session survives reinstall-update), web refresh token in localStorage + access token in memory, iOS Keychain (`KeychainTokenStore`, **not compiled on this Linux host: check on a Mac**). 401 → one refresh (single-flight) and retry; a rejected refresh token → `SessionExpired`. API origin: `BuildConfig.API_ORIGIN` (`-Pransel.apiOrigin=…`, default `http://10.0.2.2:8080`, cleartext only in debug).
- [x] **F3.3 Sync (mobile).** Mark local writes `pending`, push on reconnect/app start, pull with `since`, apply LWW; `Offline` banner + `cloud_upload` markers; `KembaliOnline` snackbar with the count.
  - Done 2026-10-15. `SyncCoordinator` (mobileMain): push pending in batches (rows stay pending if edited during the request), pull with `since`, LWW apply (a newer pending local row wins), CONFLICT → full pull. Triggers: sign-in/start, every 60 s, ~1.5 s after a local write, backoff 15 s→2 min while offline. UI: offline banner under trip tabs, `cloud_upload` marker (`LocalPendingIds`), back-online toast. Verified on the emulator with Wi-Fi/data off. No WorkManager/BGTaskScheduler: sync runs while the app process is alive.
- [x] **F3.4 Guest → account.** On first sign-in rewrite `LOCAL_USER_ID` to the user id, then push.
  - Done 2026-10-15. `RoomSessionHooks.onSignedIn`: only if guest rows exist, rewrite owner/payer to the user id and mark everything pending; new rows use the signed-in user id. Verified: guest "Bali 4 hari" appeared in the account on the server.
- [x] **F3.5 Account.** Profile, sync status, sign out; web requires sign-in (no guest option).
  - Done 2026-10-15. Account: profile row, sync row (status + "Sinkronkan"), sign out with confirm (mentions unsent changes; best-effort flush, then wipes local data). Signed-out mobile shows Masuk/Daftar. No profile editing screen (not designed).

**Done when:** a trip created offline on a phone shows up on the web once the phone is online.

## Phase 4 — Multi-member

- [x] **F4.1 Members.** `group_add` button on every trip screen → `AnggotaTrip` (owner) / `AnggotaEditor`; `MenuAnggota`, `DialogKeluarkan`, `DialogKeluarTrip`, transfer ownership.
  - Done (server calls only, no Room table). `group_add` shows for signed-in users; `MembersScreen` serves owner and editor/viewer by role; role menu, remove and leave dialogs built. Two additions not in the designs, for the owner to review: "Jadikan pemilik" (transfer ownership) as a menu item with a confirm dialog, and no "Keluar" button for the owner (blocked by `OWNER_CANNOT_LEAVE`). Left for later: invite button and "Undangan aktif" (F4.2), the balance line in the remove/leave dialogs (F4.3), dropping a left trip from the local DB (needs the sync pull to remove it).
- [x] **F4.2 Invites.** `UndangAnggota` (code + link + share sheet), deep links `https://ransel.mietek.tech/j/<code>` (Android App Links, iOS Universal Links; send the needed `.well-known` values to the backend agent), `GabungTrip`, `UndanganTidakBerlaku` (one screen, title per error code).
  - Done, except where noted. Invite sheet (role, code, link, copy, share) opens from `MembersScreen`, with the "Undangan aktif" list and "Cabut". Join screen and the invalid-invite state (title per `ErrorCode`) are built; a guest is sent to sign in and the invite comes back afterwards. Android: App Links intent filter + `MainActivity`; web: `/j/<code>`; iOS: `InviteLinkHook.open(url)` (needs `iosApp` + Associated Domains, unverified on Linux). `.well-known` values are in `docs/specs/app-links.md` for B4.6, with the release SHA-256 and the Apple team id still missing. Left out because not designed: the QR button, and "Masukkan kode lain" (enter-code screen). The invalid-invite screen cannot show the expiry date, since the preview fails with only the error code.
- [x] **F4.3 Shared expenses.** `PengeluaranBersama` (Ringkasan + "Posisimu"), `CatatPengeluaranBersama`, `CatatPengeluaranNominal` / `…Pas` (difference from `splitDifference`, Save disabled until 0), `SaldoTrip` (balances, "Supaya lunas", Tandai lunas).
  - Done on the app side; needs the backend's B4.3/B4.4 to work end to end (balances and settlements were still in progress). With more than one member the Expenses tab gets a Ringkasan | Saldo switch, the "Posisimu" card, "Dibayar kamu · 3 orang" / "bagianmu" on each row, and the record sheet gets "Dibayar oleh" + "Dibagi ke" (Bagi rata / Atur nominal, Save disabled until the difference is 0, using `splitEqually` / `splitDifference`). Balances come from `GET /balances`; "Tandai lunas" posts a settlement and shows "Lunas". Local DB v2 (auto-migration) stores `splitsJson` per expense so splits survive offline and sync. Notes: the three designs for the amount sheet are one composable (`SplitSection`); the "Posisimu" card and Saldo tab need a connection (members and balances are server data); editing a shared expense while offline keeps who paid and re-splits equally between the same people if the amount changed.
- [x] **F4.4 Packing templates.** `MenuPacking`, `PilihTemplate`, `SimpanTemplate`.
  - Done. ⋮ menu gets "Pakai template" and "Simpan sebagai template"; sheets follow `PilihTemplate` / `SimpanTemplate`. Templates are server data, so both entries need a signed-in editor/owner. Applying adds items through the normal local-first repository (works offline once the template is loaded, syncs like any item; skip-existing = same name, case-insensitive, same category). Deviations to review: an empty list also shows "Pakai template" under "Tambah barang" (the ⋮ menu is not on screen then), and built-in templates use a generic icon since the contract has no icon field.
- [x] **F4.5 Roles in UI.** VIEWER sees everything read-only; owner-only actions hidden for others.
  - Done. Rules from `trip.myRole`: VIEWER sees Itinerary, Packing and Expenses read-only (no add/record buttons, no drag, no edit sheets, no tick, no "Jadwalkan", no ⋮ menu); only the OWNER sees "Edit trip" (and with it delete). The role is now stored per trip locally (DB v3, `trips.myRole`) and filled from the sync pull; trips made on the device are OWNER. Depends on the backend returning `myRole` in the pulled `TripDto` and re-sending a trip when the role changes; until then the Members screen (live from the server) is the source of truth for a changed role.

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
