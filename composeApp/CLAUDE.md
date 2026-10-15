# composeApp/ — frontend agent

Read the root `CLAUDE.md` first. Your task list: `docs/tasks/frontend.md`. Your references: `docs/design/README.md` (screens), `docs/PRD.md` (behaviour), `docs/TRD.md` → "Client", "Offline-first sync".

## Stack

Compose Multiplatform (Android, iOS, Web/Wasm) · Material 3 · Navigation Compose (multiplatform) · Lifecycle ViewModel + `StateFlow<UiState>` · Koin · Ktor Client + kotlinx.serialization · kotlinx-datetime · Compose Resources (strings, fonts, drawables) · **Room 3 (`androidx.room3`) on Android + iOS only** · Reorderable (`sh.calvin.reorderable`) for drag and drop. Current stable versions at setup; Kotlin ≥ 2.3.

## Source sets

```
commonMain   UI, ViewModels, repository INTERFACES, Ktor API client, theme, navigation
mobileMain   (custom intermediate set: androidMain + iosMain depend on it)
             Room database, DAOs, local repository implementations, sync worker
androidMain / iosMain   platform bits (secure token storage, Ktor engine, DB builder)
wasmJsMain   remote-only repository implementations (call the API directly), no Room
```

Room must not be referenced from `commonMain` or `wasmJsMain`. Each repository has one interface in `commonMain` and two implementations: local-first (`mobileMain`, Room + pending flag + sync) and remote (`wasmJsMain`, plain API calls). Koin picks the implementation per platform.

Room 3 notes: package `androidx.room3`, KSP only, DAO functions are `suspend` or return `Flow`, `withWriteTransaction { }` replaces `runInTransaction`, a `SQLiteDriver` (bundled) is required to build the database.

## Building screens

1. Open the screen file listed in `docs/design/README.md` (HTML, inline styles, 1 px = 1 dp) and the related PRD section. Name the composable, ViewModel, UiState and route after the English **Code name** column (`Masuk.html` → `SignInScreen`, `SignInViewModel`, `SignInUiState`), never after the Indonesian file name.
2. Map every hex to a token (`docs/design/iris/tokens.md`) and use `MaterialTheme.colorScheme` / `IrisTheme.accents` / `MaterialTheme.typography`. No hex in screens. Theme lives in `ui/theme/` (`IrisTheme`, `IrisColors`, `IrisType`), already written.
3. Use the built-in M3 components where the design shows them: `DatePickerDialog`, full-screen `DateRangePicker`, 24-hour `TimePicker` in an `AlertDialog`, `ModalBottomSheet`, `AlertDialog`, `NavigationBar` (phone) / `NavigationRail` (≥ 1240 dp web).
4. Icons: Material Symbols **Rounded** with the same names as the design (`class="ic"`, `fill` = filled).
5. Strings: Indonesian copy from the design goes to `values-in/strings.xml`; write the English in `values/strings.xml`. Category labels: two sets over one enum (see `docs/specs/itinerary-spec.md`).
6. Illustrations: decorative (`contentDescription = null`), WebP at 2× (see design README). Ask the owner for the PNGs if they are not in `composeResources/drawable/` yet; use an empty `Box` placeholder of the right size meanwhile.
7. Long forms: `Column` + `verticalScroll` so buttons are never squeezed. Touch targets ≥ 48 dp, no text under 12 sp.

## Components

- Two levels only: `ui/components/` for anything used by two or more features, `feature/<name>/components/` for parts used by one feature. No atoms/molecules/organisms folders.
- Check `ui/components/` before writing a new component. When a feature component is needed by a second feature, move it to `ui/components/` instead of copying it.
- Material 3 components are the atoms; do not wrap them unless IRIS needs something the theme cannot express.
- Components are stateless: data and lambdas in, no ViewModel, no repository. Pass `Modifier` as the first optional parameter.
- **Every shared component and every screen's stateless content composable has a `@Preview` wrapped in `IrisPreview { }`** (`ui/preview/IrisPreview.kt`), which renders light and dark together. Add previews for the important states too (empty, error, long text).
- Keep the debug Component Gallery screen up to date when a shared component is added or changed.

## Architecture

MVVM with unidirectional data flow: one `UiState` data class per screen exposed as one `StateFlow`; UI actions are plain ViewModel functions; one-off events (snackbar, navigation) go through one `Channel` per ViewModel. Screens are split into a thin `XScreen(viewModel)` that collects state and a stateless `XContent(state, ...)` that previews can render.

## Notes from Phase 1

- Drag and drop in the itinerary is custom (`feature/itinerary/DayDragState` + one gesture detector on the day container), not the Reorderable library: rows are translated while dragging, so gestures must be detected on a stable ancestor, and dragging onto day chips needs hit-testing. `sh.calvin.reorderable` is not a dependency.
- Debug builds: Account → "Load sample trip" seeds the "Bali 4 hari" example; Account → component gallery.
- UI tests run only on the emulator: set `ANDROID_SERIAL` so Gradle does not install test APKs on a physical phone.

## Web (Phase 2)

- Needs the API: `docker compose up -d db` and `./gradlew :server:run` (see `server/CLAUDE.md`), then the web dev server on **8081** (8080 is the API; `CORS_ORIGINS` already allows it).
- The web app is online-only: `wasmJsMain` has `Remote*Repository` (Ktor) instead of Room, preferences in `localStorage`, no onboarding, no in-app language switch (the browser language decides).
- Compose resources load asynchronously on the web: string arrays start empty, so never index them without a fallback (`DateFormatter` does).
- Wide windows (≥ 1240 dp): navigation rail, side panel on trip detail, dialogs instead of bottom sheets (`ui/adaptive`).

## Auth and sync (Phase 3)

- `data/auth`: `SessionManager` (state: Loading / SignedOut / SignedIn / Expired), `TokenStore` per platform, `AuthRepository`, `SessionHooks` (mobile: guest data re-owned on sign-in, wiped on sign-out).
- `RanselApi` adds the bearer token and refreshes once on 401; ViewModels only see `ApiResult`.
- Mobile sync lives in `mobileMain/data/sync/SyncCoordinator`; the UI reads `SyncStatus` (no-op on web). A row is pending while `syncState = PENDING`; REJECTED rows are kept but never re-sent.
- Local API origin for Android is `BuildConfig.API_ORIGIN` (emulator: `http://10.0.2.2:8080`).
- iOS `KeychainTokenStore` and the iOS origin are unverified on Linux.

## Members and invites (Phase 4)

- Members and invites are server-only (no Room tables): `MembersViewModel` and `InviteSheetViewModel` call `RanselApi` directly, on mobile too. The members button only shows for signed-in users.
- Invite links: `data/invite/InviteLinks` holds the code the user arrived with until the join screen is closed or accepted; `MainActivity` (Android) and `initialInviteCode()` (web) feed it. After accepting, mobile calls `syncNow()` and waits for the trip to appear locally.
- Point a debug build at the deployed API with `-Pransel.apiOrigin=https://ranselapi.mietek.tech`.

## Rules

- ViewModels get `ApiResult<T>` / domain models, never HTTP status codes. Map `ApiError.code` (`ErrorCode`) to the state screens; show the code small where the design does.
- Business rules come from `shared/domain` (reorder, scheduling, split, balances, password check). Do not copy them into the app.
- Guests (no account) use `LOCAL_USER_ID` as owner/payer; once signed in, new rows use the user id. Hide UI that needs later phases (Explore AI entry points, members button, sync rows in Account) until that phase, or show them disabled with the "Segera hadir" state if the design shows them.
- Local preferences (theme mode, "Jangan tanya lagi" for the reorder dialog, Explore swipe session for the "Lanjutkan" card) are per device, not synced.
- Tokens (Phase 3): Android Keystore / iOS Keychain; on web keep the access token in memory and the refresh token in storage with the accepted risk noted in TRD.

## Run

```
./gradlew :composeApp:assembleDebug                 # Android
open iosApp/iosApp.xcodeproj                        # iOS simulator
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # Web (from Phase 2): http://localhost:8081, API at http://localhost:8080
./gradlew :shared:allTests                          # on Linux: :shared:jvmTest :shared:testDebugUnitTest :shared:wasmJsBrowserTest
```

Needs JDK 17+ (`JAVA_HOME`) and `local.properties` with `sdk.dir`. Pinned stack: Gradle 8.14.6, AGP 8.13.2, Kotlin 2.4.21, Compose Multiplatform 1.11.1 (1.12 needs AGP 9 + compileSdk 37). iOS: Apple Silicon simulator and devices only (no `iosX64`; Compose and Room 3 do not publish it).
