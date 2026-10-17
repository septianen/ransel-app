# Design — RANSEL (IRIS)

Source of truth: the Claude Design canvas **RANSEL — IRIS** (https://claude.ai/artifact/46hiHks1W4WkYjVdhqmtJp). Every board is exported here as one HTML file in `screens/` (snapshot of 14 Oct 2026). If the canvas and this folder disagree, ask the product owner which one is current.

## How to read a screen file

- Each file is one phone screen (390 × 844, or taller when the title says *konten penuh / scroll penuh* = full scroll content). **1 CSS px = 1 dp.** Web board `WebDetailTrip` is 1280 wide.
- All styles are inline. Map hex values to IRIS tokens with `iris/tokens.md` (e.g. `#008808` = `primary`, `#525252` = `on-surface-variant`, `#E2E2E2` = `outline-variant`, `#FFE6FF` = `tertiary-container`). Never hard-code hex in Compose; use `MaterialTheme.colorScheme` / `IrisTheme` tokens.
- Typography is Plus Jakarta Sans; font-size/line-height/weight map 1:1 to IRIS type styles (22/28/800 → headline-medium with weight 800 used for screen titles, 16/24/700 → title-medium, etc.).
- Icons: `<span class="ic">name</span>` is a **Material Symbols Rounded** ligature name; `class="ic fill"` = filled variant. Use the same names in Compose (Material Symbols font or the generated vector set).
- `<dc-import name="X">` means the screen is an overlay (dialog, sheet, snackbar) drawn on top of screen `X`.
- `href` between files shows navigation intent (which screen a tap opens).
- Illustrations are referenced as `../illustrations/<name>.png`. The PNGs are not in this package: export them from the IRIS design system asset group **Ilustrasi** (https://claude.ai/artifact/2DRVmvBjQ7Jru4SCDbLA2m), convert to WebP at 2× display size (440 px for 220 dp, 400 px for 200 dp), and put them in `composeApp/src/commonMain/composeResources/drawable/` with the same names (underscores instead of dashes). Illustrations are decorative: `contentDescription = null`.
- Texts on screens are the Indonesian (`values-in`) copy. English strings are written by the frontend agent; keep the same meaning and tone.

## Screens

**Naming.** File names and board titles are Indonesian because they come from the design canvas. They are references only. Every class, composable, route, variable, resource ID and test name in code is English: use the **Code name** column. A name followed by a note in brackets is a state of that screen, not a separate screen. Names ending in `Sheet`, `Dialog`, `Menu`, `Banner` or `Snackbar` are separate composables shown on top of the screen in the "Overlay on" column.

Phase = the roadmap phase in which the screen must be implemented.


### Home & trips

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Main.html` | `HomeScreen` | Beranda | 1 |  |  |
| `screens/KosongBeranda.html` | `HomeScreen` (empty) | Kosong: Beranda (belum ada trip) | 1 | `capybara-phone` |  |
| `screens/DaftarTrip.html` | `TripListScreen` | Trip saya (daftar semua trip) | 1 |  |  |
| `screens/KosongTrip.html` | `TripListScreen` (empty) | Kosong: Trip saya (belum ada trip) | 1 | `capybara-reading-map-compass` |  |
| `screens/TripSelesaiSaja.html` | `TripListScreen` (no upcoming) | Trip saya: tanpa trip mendatang | 1 |  |  |
| `screens/GelapBeranda.html` | `HomeScreen` (dark) | Tema gelap: Beranda | 1 |  |  |

### Create/edit/delete trip

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/BuatTrip.html` | `CreateTripScreen` | Buat trip | 1 |  |  |
| `screens/PilihRentangTanggal.html` | `TripDateRangePicker` | Pilih tanggal trip (date range picker) | 1 |  |  |
| `screens/EditTrip.html` | `EditTripScreen` | Edit trip (konten scroll penuh) | 1 |  |  |
| `screens/DialogHapusTrip.html` | `DeleteTripDialog` | Dialog hapus trip | 1 |  | EditTrip |

### Itinerary

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Itinerary.html` | `ItineraryScreen` | Itinerary | 1 |  |  |
| `screens/KosongItinerary.html` | `ItineraryScreen` (empty) | Kosong: Itinerary | 1 | `capybara-world-map` |  |
| `screens/TambahAktivitas.html` | `AddActivityScreen` | Tambah aktivitas (konten scroll penuh) | 1 |  |  |
| `screens/PilihJam.html` | `ActivityTimePickerDialog` | Pilih jam (time picker) | 1 |  | TambahAktivitas |
| `screens/SeretAktivitas.html` | `ItineraryScreen` (dragging) | Seret aktivitas (sedang digeser) | 1 |  |  |
| `screens/DialogUbahUrutan.html` | `ConfirmReorderDialog` | Dialog konfirmasi ubah urutan | 1 |  | Itinerary |
| `screens/HasilUrutan.html` | `ItineraryScreen` (undo snackbar) | Setelah urutan diubah (snackbar) | 1 |  |  |
| `screens/BelumDijadwalkanSemua.html` | `UnscheduledPlacesSheet` | Belum dijadwalkan: semua (bottom sheet) | 1 (5 for AI items) |  | Itinerary |
| `screens/JadwalkanTempat.html` | `SchedulePlaceSheet` | Jadwalkan tempat (dari Belum dijadwalkan) | 1 |  | Itinerary |
| `screens/WebDetailTrip.html` | `ItineraryScreen` (expanded) | Detail trip versi web (1280) | 2 |  |  |

### Packing

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Packing.html` | `PackingScreen` | Packing | 1 |  |  |
| `screens/KosongPacking.html` | `PackingScreen` (empty) | Kosong: Packing | 1 | `capybara-bedroll-lantern` |  |
| `screens/TambahBarang.html` | `AddPackingItemSheet` | Tambah barang (bottom sheet) | 1 |  | Packing |
| `screens/MenuPacking.html` | `PackingMenu` | Menu packing (template) | 4 |  | Packing |
| `screens/PilihTemplate.html` | `ApplyTemplateSheet` | Pakai template packing (bottom sheet) | 4 |  | Packing |
| `screens/SimpanTemplate.html` | `SaveTemplateSheet` | Packing: simpan sebagai template (bottom sheet) | 4 |  | Packing |

### Expenses

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Pengeluaran.html` | `ExpensesScreen` | Pengeluaran | 1 |  |  |
| `screens/StateEmpty.html` | `ExpensesScreen` (empty) | Kosong: Pengeluaran | 1 | `capybara-cash-backpack` |  |
| `screens/CatatPengeluaran.html` | `AddExpenseSheet` | Catat pengeluaran (bottom sheet) | 1 |  | Pengeluaran |
| `screens/PilihTanggal.html` | `ExpenseDatePickerDialog` | Pilih tanggal (date picker) | 1 |  | CatatPengeluaran |
| `screens/GelapPengeluaran.html` | `ExpensesScreen` (dark) | Tema gelap: Pengeluaran | 1 |  |  |

### Explore AI

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/PilihTripJelajah.html` (canvas board) | `ExploreTripPickerScreen` | Jelajah: pilih trip | 5 |  |  |
| `screens/JelajahLanjut.html` | `ExploreTripPickerScreen` (resume card) | Jelajah: ada sesi belum selesai | 5 |  |  |
| `screens/JelajahTamu.html` | `ExploreTripPickerScreen` (guest) | Jelajah: belum masuk (tamu) | 5 |  |  |
| `screens/PreferensiAI.html` | `ExplorePreferencesScreen` (existing trip) | Jelajah: cari untuk trip (Bali, harga dari budget, Khas Bali) | 5 |  |  |
| `PreferensiAITokyo` (canvas board) | `ExplorePreferencesScreen` (existing trip) | Tokyo tanpa budget, Khas Tokyo (Sakura) | 5 |  |  |
| `JelajahTripBaru`, `JelajahTripBaruMemuat` (canvas boards) | `ExplorePreferencesScreen` (new trip) | Trip baru, minat khas dimuat / memuat | 5 |  |  |
| `WebJelajah*` (canvas boards) | same screens, wide window | Jelajah di web | 5 |  |  |
| `screens/JelajahTanpaTrip.html` | superseded: no eligible trip goes straight to `ExplorePreferencesScreen` (new trip) | Jelajah: belum ada trip | 5 |  |  |
| `screens/MemuatRekomendasi.html` | `SwipeScreen` (loading) | Swipe: memuat | 5 |  |  |
| `screens/Swipe.html` | `SwipeScreen` | Swipe rekomendasi | 5 |  |  |
| `screens/SwipeKanan.html` | `SwipeScreen` (dragging right) | Swipe: geser kanan (simpan) | 5 |  |  |
| `screens/SwipeKiri.html` | `SwipeScreen` (dragging left) | Swipe: geser kiri (lewati) | 5 |  |  |
| `screens/SwipeTersimpan.html` | `SwipeScreen` (saved snackbar) | Swipe: snackbar setelah simpan | 5 |  |  |
| `screens/SwipeHabis.html` | `SwipeScreen` (finished) | Swipe: rekomendasi habis | 5 | `capybara-pinning-map` |  |
| `screens/SwipeError.html` | `SwipeScreen` (error) | Jelajah AI: error | 5 | `capybara-umbrella-rain` |  |
| `screens/SwipeTidakAda.html` | `SwipeScreen` (no results) | Jelajah AI: tidak ada hasil | 5 | `capybara-magnifier-map` |  |
| `screens/SwipeKuota.html` | `SwipeScreen` (quota exceeded) | Jelajah AI: kuota habis | 5 | `capybara-firefly` |  |
| `screens/GelapSwipe.html` | `SwipeScreen` (dark) | Tema gelap: Swipe | 5 |  |  |
| `screens/DetailTempat.html` | `PlaceDetailScreen` | Detail tempat: foto, harga, deskripsi, jam buka (perkiraan AI), lokasi, Lewati/Simpan | 5 |  | Swipe |
| `screens/DetailTempatMemuat.html` | `PlaceDetailScreen` (loading) | Detail tempat: kondisi cadangan saat teks dibangun ulang (hanya dari itinerary, cache kedaluwarsa) | 5 |  | Swipe |
| `screens/DetailTempatGelap.html` | `PlaceDetailScreen` (dark) | Tema gelap: Detail tempat | 5 |  | Swipe |
| `screens/Voting.html` | `SwipeScreen` (vote mode) | Voting: swipe dari rekomendasi teman, sisa waktu | 5 |  | Itinerary |
| `screens/VotingBanner.html` | `VoteBanner` on `ItineraryScreen` | Itinerary: banner "Rina meminta pilihanmu" | 5 |  |  |
| `screens/VotingMenunggu.html` | `SwipeScreen` (voted all) | Voting: suaramu sudah masuk | 5 |  | Voting |
| `screens/VotingHasil.html` | `VoteResultScreen` (owner) | Voting: hasil, siapa belum voting, masukkan ke itinerary | 5 |  | Itinerary |
| `screens/VotingDitutup.html` | `VoteResultScreen` (member, closed) | Voting: ditutup, pilihan owner | 5 |  | Itinerary |
| `WebDetailTempat`, `WebGelapDetailTempat` (canvas boards) | `PlaceDetailScreen` (wide window) | Web: detail tempat, terang dan gelap | 5 |  | WebSwipe |
| `WebVoting`, `WebVotingBanner`, `WebVotingMenunggu`, `WebVotingHasil`, `WebVotingDitutup` (canvas boards) | `SwipeScreen` (vote mode), `VoteBanner`, `VoteResultScreen` (wide window) | Web: voting | 5 |  | WebDetailTrip |

### Onboarding, account, sync

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Onboarding1.html` | `OnboardingScreen` (page 1) | Onboarding 1 | 1 | `capybara-world-map` |  |
| `screens/Onboarding2.html` | `OnboardingScreen` (page 2) | Onboarding 2 | 1 | `capybara-opening-backpack` |  |
| `screens/Onboarding3.html` | `OnboardingScreen` (page 3) | Onboarding 3 | 1 | `capybara-counting-coins` |  |
| `screens/Masuk.html` | `SignInScreen` | Masuk | 3 |  |  |
| `screens/Daftar.html` | `SignUpScreen` | Daftar | 3 |  |  |
| `screens/LupaSandi.html` | `ForgotPasswordScreen` | Lupa kata sandi | 3 |  |  |
| `screens/CekEmail.html` | `CheckEmailScreen` | Lupa kata sandi: cek email | 3 |  |  |
| `screens/SandiBaru.html` | `NewPasswordScreen` | Kata sandi baru | 3 |  |  |
| `screens/SesiBerakhir.html` | `SessionExpiredScreen` | Sesi berakhir | 3 | `capybara-tired-phone` |  |
| `screens/Akun.html` | `AccountScreen` | Akun (konten scroll penuh) | 1 (sync rows 3) |  |  |
| `screens/SheetMataUang.html` | `CurrencySheet` | Akun: pilih mata uang (bottom sheet) | 1 |  | Akun |
| `screens/SheetTema.html` | `ThemeSheet` | Akun: pilih tema (bottom sheet) | 1 |  | Akun |
| `screens/Offline.html` | `OfflineBanner` (on `ItineraryScreen`) | Sinkronisasi: offline di Itinerary | 3 |  |  |
| `screens/KembaliOnline.html` | `BackOnlineSnackbar` | Sinkronisasi: kembali online (snackbar) | 3 |  | Itinerary |

### Members & cost splitting

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/AnggotaTrip.html` | `MembersScreen` (owner) | Anggota trip | 4 |  |  |
| `screens/MenuAnggota.html` | `MemberRoleMenu` | Anggota: menu ubah peran | 4 |  | AnggotaTrip |
| `screens/DialogKeluarkan.html` | `RemoveMemberDialog` | Anggota: keluarkan dari trip | 4 |  | AnggotaTrip |
| `screens/AnggotaEditor.html` | `MembersScreen` (editor) | Anggota trip (dilihat editor) | 4 |  |  |
| `screens/DialogKeluarTrip.html` | `LeaveTripDialog` | Anggota: keluar dari trip | 4 |  | AnggotaEditor |
| `screens/UndangAnggota.html` | `InviteMembersSheet` | Undang anggota (bottom sheet) | 4 |  | AnggotaTrip |
| `screens/GabungTrip.html` | `JoinTripScreen` | Gabung trip (dari link undangan) | 4 | `capybara-with-duck` |  |
| `screens/UndanganTidakBerlaku.html` | `InvalidInviteScreen` | Undangan tidak berlaku | 4 | `capybara-scanning-qr` |  |
| `screens/PengeluaranBersama.html` | `SharedExpensesScreen` (Summary tab) | Pengeluaran bersama: Ringkasan | 4 |  |  |
| `screens/CatatPengeluaranBersama.html` | `AddSharedExpenseScreen` (split equally) | Catat pengeluaran bersama (konten penuh) | 4 |  | PengeluaranBersama |
| `screens/CatatPengeluaranNominal.html` | `AddSharedExpenseScreen` (custom, not balanced) | Atur nominal: belum pas | 4 |  | PengeluaranBersama |
| `screens/CatatPengeluaranNominalPas.html` | `AddSharedExpenseScreen` (custom, balanced) | Atur nominal: pas | 4 |  | PengeluaranBersama |
| `screens/SaldoTrip.html` | `SharedExpensesScreen` (Balances tab) | Saldo dan pelunasan | 4 |  |  |

### Common states

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/StateError.html` | `ErrorState` (connection) | State: error koneksi | all | `capybara-sleeping` |  |
| `screens/StateServerError.html` | `ErrorState` (server) | State: error server | all | `capybara-tent` |  |
| `screens/StateComingSoon.html` | `ComingSoonState` | State: segera hadir | all | `capybara-compass-map` |  |

## Not designed yet

Build these from existing patterns; ask the product owner before inventing new layouts: enter invite code, activity detail/edit (reuse *Tambah aktivitas* form), notifications, edit profile, language sheet (reuse *Sheet tema* pattern), search trips, all transactions list, dark theme for Itinerary/Packing/Explore/sheets/dialogs (apply the IRIS dark tokens; see *GelapBeranda*, *GelapPengeluaran*, *GelapSwipe* for the pattern).

## Design decisions

See `../specs/design-decisions-id.md` (Indonesian; the agreed design rules) plus `../specs/itinerary-spec.md` and `../specs/packing-spec.md` (English).
