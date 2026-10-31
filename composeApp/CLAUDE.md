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
2. Map every hex to a token (`docs/design/iris/tokens.md`) and use `MaterialTheme.colorScheme` / `IrisTheme.accents` / `MaterialTheme.typography`. No hex in screens. Theme lives in `ui/theme/` (`IrisTheme`, `IrisColors`, `IrisType`) in the `iris` module (shared with `adminApp`, same packages), together with `ui/symbols` and `ui/preview` and the fonts.
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
- Section titles inside a screen use `SectionHeader` (IRIS `headline-small`, `onSurface`; owner, 27 Oct 2026). Not for app bar or sheet titles, nor for collapsible category headers of a list. Big top-of-tab cards use `HeroCard`; status pills use `StatusTag`.

## Architecture

MVVM with unidirectional data flow: one `UiState` data class per screen exposed as one `StateFlow`; UI actions are plain ViewModel functions; one-off events (snackbar, navigation) go through one `Channel` per ViewModel. Screens are split into a thin `XScreen(viewModel)` that collects state and a stateless `XContent(state, ...)` that previews can render.

## Notes from Phase 1

- Drag and drop in the itinerary is custom (`feature/itinerary/DayDragState` + one gesture detector on the day container), not the Reorderable library: rows are translated while dragging, so gestures must be detected on a stable ancestor, and dragging onto day chips needs hit-testing. `sh.calvin.reorderable` is not a dependency.
- Debug builds: Account → "Load sample trip" seeds the "Bali 4 hari" example; Account → component gallery.
- UI tests run only on the emulator: set `ANDROID_SERIAL` so Gradle does not install test APKs on a physical phone.

## Web (Phase 2)

- Needs the API: `docker compose up -d db` and `./gradlew :server:run` (see `server/CLAUDE.md`), then the web dev server on **8081** (8080 is the API; `CORS_ORIGINS` already allows it).
- The web app is online-only: `wasmJsMain` has `Remote*Repository` (Ktor) instead of Room, preferences in `localStorage`, no onboarding. Language: "Ikuti sistem" follows the browser; a picked language is stored in `localStorage` and `index.html` puts it in place of `navigator.languages` before the app loads, so changing it reloads the page (owner, 27 Oct 2026).
- Compose resources load asynchronously on the web: string arrays start empty, so never index them without a fallback (`DateFormatter` does).
- Wide windows (≥ 1240 dp): navigation rail, side panel on trip detail, dialogs instead of bottom sheets (`ui/adaptive`).

## Auth and sync (Phase 3)

- `data/auth`: `SessionManager` (state: Loading / SignedOut / SignedIn / Expired), `TokenStore` per platform, `AuthRepository`, `SessionHooks` (mobile: guest data re-owned on sign-in, wiped on sign-out).
- `RanselApi` adds the bearer token and refreshes once on 401; ViewModels only see `ApiResult`.
- Mobile sync lives in `mobileMain/data/sync/SyncCoordinator`; the UI reads `SyncStatus` (no-op on web). A row is pending while `syncState = PENDING`; REJECTED rows are kept but never re-sent.
- Android build types: **debug** = `app.ransel.debug` ("RANSEL Dev", Chucker, cleartext allowed) against staging `https://ranselapi-dev.mietek.tech`; **release** = `app.ransel` against production, R8 (`proguard-rules.pro`) and signed from `keystore.properties` / `RANSEL_KEYSTORE_*` when present (`keystore.properties.example`). Both install side by side. `BuildConfig.API_ORIGIN` per type; override with `-Pransel.apiOrigin.debug=http://10.0.2.2:8080` (emulator against a local server) or `-Pransel.apiOrigin.release=...`. Smoke-test a release build after adding a library or a reflection-based feature: R8 problems only show at runtime. CI (*Distribute Android*) sends debug builds from `develop` and release builds from `main` to Firebase App Distribution (`deploy/README.md`).
- Debug builds on Android use Chucker (OkHttp interceptor registered as the `HttpClientEngine` in the Android platform module; release gets the no-op artifact). Open it from the "Network" notification or the Chucker launcher shortcut. Authorization headers are redacted.
- iOS `KeychainTokenStore` and the iOS origin are unverified on Linux.
- Email verification (F3.5): `EmailVerification` holds the sign-up prompt and an opened `/verify-email?token=` link until the navigation shows `VerifyEmailScreen` / `EmailVerifiedScreen`. Explore shows a verify gate while `MeDto.emailVerified` is false.

## Analytics and crash reports

- `data/analytics`: `Analytics` (screen, event, user) and `CrashReporter`; Android = Firebase Analytics + Crashlytics, web = `WebAnalytics` (JS SDK in `index.html`, production host only), iOS = no-op. Android sends from debug and release (debug is the Firebase app `app.ransel.debug`, so filter by app in the console).
- A new event: add a case to `AnalyticsEvent` (snake_case name, params = enums/flags/counts, never names, emails, free text or amounts) and call `analytics.event(...)` from the ViewModel; ViewModels take `analytics: Analytics = NoAnalytics` as their last parameter so tests can omit it or pass `FakeAnalytics` (`AnalyticsEventsTest`). Add it to the list in `docs/TRD.md` too.
- Screen views are automatic (`navigation/TrackScreens`, route class name). User id, user properties (`UserProperty`) and crash keys (`CrashKey`) come from `applySessionContext` in `App.kt`; on Android `BreadcrumbAnalytics` also logs every screen and event to Crashlytics.
- Non-fatal: `crashes.record(e)` only for errors that point to a bug (unparseable response, corrupt saved data), never for offline or HTTP errors. No personal data in exception messages.

## Members and invites (Phase 4)

- Members and invites are server-only (no Room tables): `MembersViewModel` and `InviteSheetViewModel` call `RanselApi` directly, on mobile too. Members open from the Group tab's "Anggota" section (signed-in users only; the header button was removed on 27 Oct 2026).
- Invite links: `data/invite/InviteLinks` holds the code the user arrived with until the join screen is closed or accepted; `MainActivity` (Android) and `initialInviteCode()` (web) feed it. After accepting, mobile calls `syncNow()` and waits for the trip to appear locally.
- Shared expenses: `ExpensesViewModel` loads members and balances from the API (empty = solo behaviour). Splits are stored per expense (`ExpenseEntity.splitsJson`, DB v2) and travel through the normal sync; `SplitState` holds the form, the maths is `shared/domain`.
- Explore AI (Phase 5): `ExploreTripPickerViewModel` (step 1, trip list), `ExplorePreferencesViewModel` (step 2, existing or new trip, suggestions with 8 s timeout), `SwipeViewModel` (cards, save/skip/undo, prefetch), both over `ExploreSessionStore` (unfinished session as JSON in the preferences, also drives the tab dot). The backend (B5.x) must be live for real recommendations; tests use a MockEngine.
- Place detail (`feature/explore/PlaceDetail*`): from a swipe card it reads the card from `ExploreSessionStore` (no request); from the itinerary it calls `RanselApi.placeDetail` (`ItineraryTab` provides `LocalOpenPlaceDetail`; only signed-in users). Skip/Save in the detail call the swipe screen's ViewModel via `navController.getBackStackEntry<SwipeRoute>()`. Place photos use `rememberPlacePhotoRequest` (stable cache key per place).
- Point a debug build at the production API with `-Pransel.apiOrigin.debug=https://ranselapi.mietek.tech`.

## Admin app (Phase 6)

- `adminApp/` is Karung, the admin web app (wasmJs only, `./gradlew :adminApp:wasmJsBrowserDevelopmentRun`, http://localhost:8082). It uses `iris` for the theme, plain presenters (`ui/Presenter.kt`) instead of Koin ViewModels, hash routes (`#/users/<id>`), and keeps the refresh token in sessionStorage (one key per server). On `karung.mietek.tech` it can switch between production and staging (`data/ApiEnvironment.kt`; switching signs out and reloads); locally it only knows `localhost:8080`. Run the server with `ADMIN_EMAILS=<your email>` and `ADMIN_APP_ORIGIN=http://localhost:8082`.
- Trip albums (F24.1): `feature/albums/`, server-only like documents (no Room). Drive photos load straight from Google with Coil (`AlbumPhoto`, the DTO's `thumbnailUrl` / `imageUrl`); never proxy them through the API. New "add" actions go in the existing + sheet or FAB, and an empty Ringkasan section is left out (owner, 31 Oct 2026).
- Photos (F22.1): pick with `PhotoSourceSheet` and upload what `preparePhoto(file, PhotoKind)` returns; show images from the API through `apiImageUrl(path)`. `MemberAvatar` finds a member's photo in `MemberPhotos` by its key (member id or user id), so pass the member id as `key`. `TripDto.coverUrl` is the server's: never send it, apply it with `TripRepository.applyCover`.
- Strings: write ' and " plainly in `composeResources/values*/strings.xml`. Compose Resources shows `\'` with its backslash (Android resources do not); `:checkStringEscapes` fails the build on one.
- Remote content: Karung may edit every composeApp `<string>` and pick error illustrations from `drawable/ic_*`. After adding or renaming strings or drawables, run `python3 scripts/export-content-catalog.py` and commit `server/src/main/resources/content/catalog.json`, or the new keys cannot be edited.
- The apps read it (stage E, `content/`): **import `app.ransel.content.stringResource` and `getString`, never the ones from `org.jetbrains.compose.resources`**, or Karung's text for that key is ignored. Category pickers use `itineraryCategoryChoices` / `expenseCategoryChoices` / `packingCategoryChoices` (Karung's order, hidden left out) instead of `entries`; an error screen gets Karung's copy when `FullScreenState` receives the `errorCode`.
- Icons: the bundled Material Symbols subset lacks some design icons (`dashboard`, `group`, `toll`, `devices`, `download`, `menu`, ...); Karung uses the nearest existing symbol until the subset is extended.
- Traveller app (F9.7): `SessionManager` ends the session locally on 403 `ACCOUNT_DISABLED` at refresh (no `SessionHooks.onSignedOut`, local data stays) and sign-in shows "Akun dinonaktifkan".

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
