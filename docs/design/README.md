# Design — RANSEL (IRIS)

Source of truth: the Claude Design canvas **RANSEL — IRIS** (https://claude.ai/artifact/46hiHks1W4WkYjVdhqmtJp). Every board is exported here as one HTML file in `screens/` (snapshot of 14 Oct 2026, refreshed from the canvas on 26 Oct 2026). If the canvas and this folder disagree, ask the product owner which one is current.

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
| `screens/JelajahLanjut.html` | `ExploreTripPickerScreen` (resume card) | Jelajah: sesi belum selesai di dalam trip (satu per trip, bisa lebih dari satu) | 5 |  |  |
| `screens/JelajahTamu.html` | `ExploreTripPickerScreen` (guest) | Jelajah: belum masuk (tamu) | 5 |  |  |
| `screens/PreferensiAI.html` | `ExplorePreferencesScreen` (existing trip) | Jelajah: cari untuk trip (Bali, harga dari budget, Khas Bali). *Outdated since 23 Oct 2026: "Fokus area (opsional)" is now a prefilled, editable "Destinasi" field (design-decisions-id.md); the board still needs updating.* | 5 |  |  |
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
| `screens/ItineraryLihatDetail.html` | `ItineraryScreen` (AI items) | Itinerary: tombol info membuka detail tempat AI | 5 |  |  |
| `screens/DetailTempatItinerary.html` | `PlaceDetailScreen` (from itinerary) | Detail tempat dari itinerary, tanpa Lewati/Simpan | 5 |  | ItineraryLihatDetail |
| `WebItineraryLihatDetail`, `WebDetailTempatItinerary` (canvas boards) | same, wide window | Web: lihat detail dari itinerary | 5 |  |  |
| `screens/DetailTempatGelap.html` | `PlaceDetailScreen` (dark) | Tema gelap: Detail tempat | 5 |  | Swipe |
| `screens/Voting.html` | `SwipeScreen` (vote mode) | Voting: swipe dari rekomendasi teman, sisa waktu | 5 |  | Itinerary |
| `screens/VotingBanner.html` | `VoteBanner` on `ItineraryScreen` | Itinerary: banner "Rina meminta pilihanmu" | 5 |  |  |
| `screens/VotingMenunggu.html` | `SwipeScreen` (voted all) | Voting: suaramu sudah masuk | 5 |  | Voting |
| `screens/VotingHasil.html` | `VoteResultScreen` (owner) | Voting: hasil, siapa belum voting, masukkan ke itinerary | 5 |  | Itinerary |
| `screens/VotingDitutup.html` | `VoteResultScreen` (member, closed) | Voting: ditutup, pilihan owner | 5 |  | Itinerary |
| `screens/VotingDitutupMenunggu.html` | `VoteResultScreen` (member, closed, not decided) | Voting: ditutup, menunggu owner | 5 |  | Itinerary |
| `screens/DetailTempatVoting.html` | `PlaceDetailScreen` (vote mode) | Detail tempat: mode voting, Lewati/Suka | 5 |  | Voting |
| `screens/DaftarTripVoting.html` | `TripListScreen` (open round) | Trip saya: ada voting (titik magenta) | 5 |  |  |
| `screens/PreferensiAIGrup.html` | `ExplorePreferencesScreen` (multi-member trip) | Jelajah: trip dengan beberapa anggota (hasil jadi voting) | 5 |  |  |
| `WebDetailTempat`, `WebGelapDetailTempat` (canvas boards) | `PlaceDetailScreen` (wide window) | Web: detail tempat, terang dan gelap | 5 |  | WebSwipe |
| `WebVoting`, `WebVotingBanner`, `WebVotingMenunggu`, `WebVotingHasil`, `WebVotingDitutup` (canvas boards) | `SwipeScreen` (vote mode), `VoteBanner`, `VoteResultScreen` (wide window) | Web: voting | 5 |  | WebDetailTrip |

### Notifications

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/Notifikasi.html` | `NotificationsScreen` | Notifikasi: kotak masuk (Baru / Sebelumnya, tandai semua dibaca) | 5 |  |  |
| `screens/NotifikasiVoting.html` | `NotificationsScreen` (voting rows) | Notifikasi: voting (minta pilihan, tinggal 3 jam, saatnya memutuskan, hasil) | 5 |  |  |
| `screens/GelapNotifikasi.html` | `NotificationsScreen` (dark) | Tema gelap: Notifikasi | 5 |  |  |
| `screens/KosongNotifikasi.html` | `NotificationsScreen` (empty) | Kosong: Notifikasi | 5 | `ic_sun_bathing_water` |  |
| `screens/BerandaNotifikasiBaru.html` | `HomeScreen` (unread badge) | Beranda: lonceng dengan jumlah belum dibaca | 5 |  |  |
| `screens/PengaturanNotifikasi.html` | `NotificationSettingsScreen` | Pengaturan notifikasi: tiga saklar | 5 |  |  |
| `screens/PengaturanNotifikasiMati.html` | `NotificationSettingsScreen` (system permission off) | Pengaturan notifikasi: izin ponsel mati | 5 |  |  |
| `screens/IzinNotifikasi.html` | `NotificationPermissionSheet` | Izin notifikasi: penjelasan sebelum dialog sistem | 5 |  | Main |

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
| `screens/VerifikasiEmail.html` | `VerifyEmailScreen` | Verifikasi email: cek email (setelah Daftar) | 3 |  |  |
| `screens/EmailTerverifikasi.html` | `EmailVerifiedScreen` | Verifikasi email: berhasil (dari link) | 3 |  |  |
| `screens/VerifikasiTidakBerlaku.html` | `EmailVerifiedScreen` (invalid link) | Verifikasi email: link tidak berlaku | 3 |  |  |
| `screens/JelajahBelumVerifikasi.html` | `ExploreScreen` (email not verified) | Jelajah: email belum diverifikasi | 3 |  |  |
| `WebVerifikasiEmail`, `WebEmailTerverifikasi`, `WebVerifikasiTidakBerlaku`, `WebJelajahBelumVerifikasi` (canvas boards) | same, web layout | Web: verifikasi email | 3 |  |  |
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
| `screens/CatatPengeluaranPembayar.html` | `ExpenseSheet` + `SplitSection` (several payers, equal) | Catat pengeluaran bersama: dibayar beberapa orang, bagi rata | 4 |  | PengeluaranBersama |
| `screens/CatatPengeluaranPembayarNominal.html` | `ExpenseSheet` + `SplitSection` (several payers, set amounts) | Dibayar beberapa orang: atur nominal, masih kurang | 4 |  | PengeluaranBersama |
| `WebCatatPengeluaranPembayar` (canvas board) | same, side sheet | Web: dibayar beberapa orang | 4 |  | WebPengeluaranBersama |
| `screens/CatatPengeluaranNominal.html` | `AddSharedExpenseScreen` (custom, not balanced) | Atur nominal: belum pas | 4 |  | PengeluaranBersama |
| `screens/CatatPengeluaranNominalPas.html` | `AddSharedExpenseScreen` (custom, balanced) | Atur nominal: pas | 4 |  | PengeluaranBersama |
| `screens/SaldoTrip.html` | `SharedExpensesScreen` (Balances tab) | Saldo dan pelunasan | 4 |  |  |

### Common states

| File | Code name | Board title | Phase | Illustration | Overlay on |
| --- | --- | --- | --- | --- | --- |
| `screens/StateError.html` | `ErrorState` (connection) | State: error koneksi | all | `capybara-sleeping` |  |
| `screens/StateServerError.html` | `ErrorState` (server) | State: error server | all | `capybara-tent` |  |
| `screens/StateComingSoon.html` | `ComingSoonState` | State: segera hadir | all | `capybara-compass-map` |  |

### Admin app (Karung, Phase 6)

Canvas **Karung** (https://claude.ai/artifact/GJbx3VCHbQUVPgkHzmnHbu), not exported to `screens/`: open the canvas. Web pages at 1440 wide, IRIS tokens, sidebar layout. Rules: `../specs/admin-spec.md`.

| Board file | Code name | Board title | Stage |
| --- | --- | --- | --- |
| `Main` | `OverviewScreen` | Ringkasan | B |
| `Pengguna` | `UsersScreen` | Pengguna | A |
| `PenggunaDetail` | `UserDetailScreen` | Pengguna: detail | A |
| `PenggunaAturKredit` | `AdjustCreditsDialog` | Pengguna: atur kredit | A |
| `JelajahAI` | `ExploreAiScreen` | Jelajah AI | B |
| `Sistem` | `SystemScreen` | Sistem & error | B, C |
| `KontenTeks` | `ContentStringsScreen` | Konten: teks dan wording | D |
| `KontenError` | `ContentErrorsScreen` | Konten: pesan error | D |
| `KontenKategori` | `ContentCategoriesScreen` | Konten: kategori | D |
| `KontenMinatKhas` | `ContentInterestsScreen` | Konten: minat khas | D |
| `Sidebar` | `AdminSidebar` | Komponen: sidebar | A |

## Boards added after 14 Oct 2026

Exported from the canvas on 26 Oct 2026, grouped by the canvas section they sit in. The **Code name** column is filled in by the agent that builds a screen, in the same commit; empty = not built yet. Web boards are 1280 wide (`Width` column); `Og*` boards are 1200 × 630 Open Graph images, not screens. Illustrations follow the mobile board of the same screen; the web sign-in boards use an illustration the old export did not have, written as `../illustrations/canvas-2475ebc2.png` (ask the product owner which IRIS illustration it is). Six older files are no longer on the canvas and are kept as they were: `DaftarTripVoting`, `DetailTempatVoting`, `JelajahTanpaTrip`, `NotifikasiVoting`, `PreferensiAIGrup`, `VotingDitutupMenunggu`.

### Rekomendasi AI

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/PreferensiAITokyo.html` |  | Jelajah: cari untuk trip yang ada, contoh Tokyo tanpa budget (konten penuh) | 390 |
| `screens/JelajahTripBaru.html` |  | Jelajah: trip baru (konten penuh) | 390 |
| `screens/JelajahTripBaruMemuat.html` |  | Jelajah: trip baru, minat khas sedang dimuat (konten penuh) | 390 |

### Web · Beranda dan trip

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebBeranda.html` |  | Web: Beranda | 1280 |
| `screens/WebBerandaKosong.html` |  | Web: Beranda kosong (belum ada trip) | 1280 |
| `screens/WebTripSaya.html` |  | Web: Trip saya | 1280 |
| `screens/WebTripSayaKosong.html` |  | Web: Trip saya kosong | 1280 |
| `screens/WebTripSelesaiSaja.html` |  | Web: Trip saya tanpa trip mendatang | 1280 |
| `screens/WebGelapBeranda.html` |  | Web: Tema gelap Beranda | 1280 |

### Web · Buat, edit, dan hapus trip

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebBuatTrip.html` |  | Web: Buat trip (dialog) | 1280 |
| `screens/WebPilihRentangTanggal.html` |  | Web: Pilih tanggal trip (date range picker) | 1280 |
| `screens/WebEditTrip.html` |  | Web: Edit trip (dialog) | 1280 |
| `screens/WebDialogHapusTrip.html` |  | Web: Dialog hapus trip | 1280 |

### Web · Itinerary

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebItineraryKosong.html` |  | Web: Itinerary kosong | 1280 |
| `screens/WebTambahAktivitas.html` |  | Web: Tambah aktivitas (side sheet) | 1280 |
| `screens/WebPilihJam.html` |  | Web: Pilih jam (time picker) | 1280 |
| `screens/WebSeretAktivitas.html` |  | Web: Seret aktivitas (sedang digeser) | 1280 |
| `screens/WebDialogUbahUrutan.html` |  | Web: Dialog konfirmasi ubah urutan | 1280 |
| `screens/WebHasilUrutan.html` |  | Web: Setelah urutan diubah (snackbar) | 1280 |
| `screens/WebBelumDijadwalkanSemua.html` |  | Web: Belum dijadwalkan, semua (dialog) | 1280 |
| `screens/WebJadwalkanTempat.html` |  | Web: Jadwalkan tempat (dialog) | 1280 |

### Web · Packing

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebPacking.html` |  | Web: Packing | 1280 |
| `screens/WebPackingKosong.html` |  | Web: Packing kosong | 1280 |
| `screens/WebTambahBarang.html` |  | Web: Tambah barang (dialog) | 1280 |
| `screens/WebMenuPacking.html` |  | Web: Menu packing | 1280 |
| `screens/WebPilihTemplate.html` |  | Web: Pakai template packing (dialog) | 1280 |
| `screens/WebSimpanTemplate.html` |  | Web: Simpan sebagai template (dialog) | 1280 |

### Web · Pengeluaran

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebPengeluaran.html` |  | Web: Pengeluaran | 1280 |
| `screens/WebPengeluaranKosong.html` |  | Web: Pengeluaran kosong | 1280 |
| `screens/WebCatatPengeluaran.html` |  | Web: Catat pengeluaran (side sheet) | 1280 |
| `screens/WebPilihTanggal.html` |  | Web: Pilih tanggal (date picker) | 1280 |
| `screens/WebGelapPengeluaran.html` |  | Web: Tema gelap Pengeluaran | 1280 |

### Web · Rekomendasi AI

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebJelajah.html` |  | Web: Jelajah, pilih trip | 1280 |
| `screens/WebJelajahLanjut.html` |  | Web: Jelajah, ada sesi belum selesai | 1280 |
| `screens/WebJelajahPreferensi.html` |  | Web: Jelajah, cari untuk trip yang ada (konten penuh) | 1280 |
| `screens/WebJelajahTripBaru.html` |  | Web: Jelajah, trip baru (konten penuh) | 1280 |
| `screens/WebSwipe.html` |  | Web: Swipe rekomendasi | 1280 |
| `screens/WebSwipeKanan.html` |  | Web: Swipe geser kanan (simpan) | 1280 |
| `screens/WebSwipeKiri.html` |  | Web: Swipe geser kiri (lewati) | 1280 |
| `screens/WebSwipeTersimpan.html` |  | Web: Swipe snackbar setelah simpan | 1280 |
| `screens/WebMemuatRekomendasi.html` |  | Web: Swipe memuat | 1280 |
| `screens/WebSwipeHabis.html` |  | Web: Swipe rekomendasi habis | 1280 |
| `screens/WebSwipeError.html` |  | Web: Jelajah AI error | 1280 |
| `screens/WebSwipeTidakAda.html` |  | Web: Jelajah AI tidak ada hasil | 1280 |
| `screens/WebSwipeKuota.html` |  | Web: Jelajah AI kuota habis | 1280 |
| `screens/WebGelapSwipe.html` |  | Web: Tema gelap Swipe | 1280 |

### Web · Akun dan masuk

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebMasuk.html` |  | Web: Masuk (tanpa opsi tamu) | 1280 |
| `screens/WebDaftar.html` |  | Web: Daftar | 1280 |
| `screens/WebLupaSandi.html` |  | Web: Lupa kata sandi | 1280 |
| `screens/WebCekEmail.html` |  | Web: Lupa kata sandi, cek email | 1280 |
| `screens/WebSandiBaru.html` |  | Web: Kata sandi baru | 1280 |
| `screens/WebSesiBerakhir.html` |  | Web: Sesi berakhir | 1280 |
| `screens/WebAkun.html` |  | Web: Akun | 1280 |
| `screens/WebSheetMataUang.html` |  | Web: Akun, pilih mata uang (dialog) | 1280 |
| `screens/WebSheetTema.html` |  | Web: Akun, pilih tema (dialog) | 1280 |

### Web · Anggota dan bagi biaya

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebAnggotaTrip.html` |  | Web: Anggota trip (pemilik) | 1280 |
| `screens/WebMenuAnggota.html` |  | Web: Anggota, menu ubah peran | 1280 |
| `screens/WebDialogKeluarkan.html` |  | Web: Anggota, keluarkan dari trip | 1280 |
| `screens/WebAnggotaEditor.html` |  | Web: Anggota trip (dilihat editor) | 1280 |
| `screens/WebDialogKeluarTrip.html` |  | Web: Anggota, keluar dari trip | 1280 |
| `screens/WebUndangAnggota.html` |  | Web: Undang anggota (dialog) | 1280 |
| `screens/WebGabungTrip.html` |  | Web: Gabung trip (dari link undangan) | 1280 |
| `screens/WebUndanganTidakBerlaku.html` |  | Web: Undangan tidak berlaku | 1280 |
| `screens/WebPengeluaranBersama.html` |  | Web: Pengeluaran bersama, Ringkasan | 1280 |
| `screens/WebCatatPengeluaranBersama.html` |  | Web: Catat pengeluaran bersama (side sheet) | 1280 |
| `screens/WebCatatPengeluaranNominal.html` |  | Web: Atur nominal, belum pas | 1280 |
| `screens/WebCatatPengeluaranNominalPas.html` |  | Web: Atur nominal, pas | 1280 |
| `screens/WebSaldoTrip.html` |  | Web: Saldo dan pelunasan | 1280 |
| `screens/WebCatatPengeluaranPembayar.html` |  | Web · Catat pengeluaran bersama: dibayar beberapa orang | 1280 |

### Web · State umum

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebStateError.html` |  | Web: State error koneksi | 1280 |
| `screens/WebStateServerError.html` |  | Web: State error server | 1280 |
| `screens/WebStateComingSoon.html` |  | Web: State segera hadir | 1280 |

### Web · Detail tempat dan voting (usulan)

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebDetailTempat.html` |  | Web · Detail tempat | 1280 |
| `screens/WebGelapDetailTempat.html` |  | Web · Tema gelap: Detail tempat | 1280 |
| `screens/WebVotingBanner.html` |  | Web · Voting: banner di itinerary | 1280 |
| `screens/WebVoting.html` |  | Web · Voting: swipe pilihan teman | 1280 |
| `screens/WebVotingMenunggu.html` |  | Web · Voting: suaramu sudah masuk | 1280 |
| `screens/WebVotingHasil.html` |  | Web · Voting: hasil untuk owner | 1280 |
| `screens/WebVotingDitutup.html` |  | Web · Voting: ditutup (anggota) | 1280 |
| `screens/WebItineraryLihatDetail.html` |  | Web · Itinerary: lihat detail tempat AI | 1280 |
| `screens/WebDetailTempatItinerary.html` |  | Web · Detail tempat dari itinerary | 1280 |

### Verifikasi email (usulan)

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebVerifikasiEmail.html` |  | Web: Verifikasi email, cek email | 1280 |
| `screens/WebEmailTerverifikasi.html` |  | Web: Email terverifikasi | 1280 |
| `screens/WebVerifikasiTidakBerlaku.html` |  | Web: Link verifikasi tidak berlaku | 1280 |
| `screens/WebJelajahBelumVerifikasi.html` |  | Web: Jelajah, email belum diverifikasi | 1280 |

### Trip grup · P0: anggota tanpa akun, gabung, onboarding, menagih

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/OnboardingGrup1.html` |  | Onboarding grup 1: rencana bareng | 390 |
| `screens/OnboardingGrup2.html` |  | Onboarding grup 2: patungan | 390 |
| `screens/OnboardingGrup3.html` |  | Onboarding grup 3: siapa bawa apa | 390 |
| `screens/AjakTeman.html` |  | Setelah buat trip: ajak teman | 390 |
| `screens/AnggotaTanpaAkun.html` |  | Anggota trip: ada anggota tanpa akun | 390 |
| `screens/TambahAnggotaNama.html` |  | Tambah anggota tanpa akun (bottom sheet) | 390 |
| `screens/WebGabungPratinjau.html` | `GuestPageContent` (invite link, signed out) | Web di HP: pratinjau trip dari link, tanpa login | 390 |
| `screens/GabungPilihNama.html` |  | Gabung trip: klaim nama tanpa akun | 390 |
| `screens/SaldoTagih.html` | `SaldoContent` | Saldo: ingatkan dan tandai lunas | 390 |
| `screens/IngatkanWA.html` | `RemindSheet` | Pengingat bayar lewat WhatsApp (bottom sheet) | 390 |
| `screens/BayarKe.html` | `PayToSheet` | Bayar ke anggota (yang perlu bayar) | 390 |
| `screens/InfoPembayaran.html` | `PaymentMethodsScreen` | Akun: info pembayaran | 390 |

### Trip grup · P1: polling tanggal, barang bersama, kas bersama

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/BuatTripTanggalBelumPasti.html` |  | Buat trip: tanggal belum pasti | 390 |
| `screens/BuatPolling.html` |  | Atur polling tanggal (bottom sheet) | 390 |
| `screens/PollingTanggal.html` |  | Polling tanggal: pilih dan tetapkan | 390 |
| `screens/PackingBersama.html` | `PackingList` Bersama (`SharedPackingRow`, `SharedSummaryCard`) | Packing: barang bersama | 390 |
| `screens/TambahBarangBersama.html` | `AddPackingItemSheet` + `BringerChoice` | Tambah barang bersama (bottom sheet) | 390 |
| `screens/KasBersama.html` | `KittyContent` (Expenses tab, Kas) | Pengeluaran: kas bersama (**removed 27 Oct 2026: the owner dropped the kitty; not built**) | 390 |
| `screens/CatatSetoran.html` | `DepositSheet` | Catat setoran kas (bottom sheet) (**removed 27 Oct 2026: the owner dropped the kitty; not built**) | 390 |

### Trip grup · P2: foto struk, dokumen trip, rekap

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/FotoStruk.html` | `ReceiptFlow` (pick) | Foto struk: kamera | 390 |
| `screens/HasilStruk.html` | `ReceiptFlow` (result, `ReceiptFormState`) | Foto struk: hasil dan bagi per item | 390 |
| `screens/DokumenTrip.html` | `DocumentsScreen` + `AddDocumentSheet` | Dokumen trip | 390 |
| `screens/RekapTrip.html` | `RecapScreen` (`RecapCard`) | Rekap trip untuk dibagikan | 390 |

### Trip grup · titik masuk: polling, foto struk, dokumen, rekap

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/NotifikasiGrup.html` |  | Notifikasi: polling, bayar, dokumen, rekap | 390 |
| `screens/ItineraryPolling.html` |  | Itinerary: tanggal masih dipolling (anggota) | 390 |
| `screens/PollingTanggalAnggota.html` |  | Polling tanggal: tampilan anggota | 390 |
| `screens/WebPollingTamu.html` | `GuestPageContent` | Web di HP: polling dari link WhatsApp, tanpa akun | 390 |
| `screens/TambahPengeluaranPilih.html` | `AddExpenseChooserSheet` | Tambah pengeluaran: pilih cara (tombol +) | 390 |
| `screens/MenuTrip.html` |  | Menu trip: tinggal pengaturan trip | 390 |
| `screens/BerandaRekap.html` | `RecapHomeCard` on `HomeScreen` | Beranda setelah trip selesai: kartu rekap | 390 |
| `screens/CatatPengeluaranKas.html` | `ExpenseSheet` ("Ambil dari kas" on) | Catat pengeluaran: toggle ambil dari kas aktif (**removed 27 Oct 2026: the owner dropped the kitty; not built**) | 390 |

### Tab Grup · pusat polling, tindakan, dan dokumen (+ komentar)

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/TripGrup.html` |  | Tab Grup: pusat trip | 390 |
| `screens/TambahDiGrup.html` |  | Tab Grup: tombol + dibuka | 390 |
| `screens/PilihJenisPolling.html` |  | Buat polling: pilih jenis | 390 |
| `screens/BuatPollingPenginapan.html` |  | Buat polling penginapan: sudah ada opsi | 390 |
| `screens/PollingPenginapanAnggota.html` |  | Polling penginapan: tampilan anggota | 390 |
| `screens/KomentarOpsi.html` |  | Komentar pada opsi polling | 390 |
| `screens/PollingPenginapanHasil.html` | `PollScreen` (decide: estimate) | Polling penginapan: hasil untuk pembuat | 390 |
| `screens/PollingBudgetAnonim.html` |  | Polling budget anonim (P2) | 390 |

### Polling · tambah opsi lewat Jelajah, link, atau manual

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/BuatPollingPenginapanKosong.html` |  | Polling penginapan: belum ada opsi | 390 |
| `screens/JelajahPenginapan.html` |  | Jelajah penginapan dari polling | 390 |
| `screens/JelajahPenginapanHasil.html` |  | Jelajah penginapan: hasil untuk polling | 390 |
| `screens/TambahOpsiManual.html` |  | Tambah opsi: link atau manual | 390 |
| `screens/BuatPollingMakan.html` |  | Polling tempat makan: belum ada opsi | 390 |
| `screens/JelajahMakan.html` |  | Jelajah tempat makan dari polling | 390 |
| `screens/JelajahMakanHasil.html` |  | Jelajah tempat makan: hasil untuk polling | 390 |

### Bagikan ke WhatsApp · preview link dan web untuk tamu

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/BagikanPolling.html` | `SharePollSheet` | Polling terkirim: bagikan ke grup | 390 |
| `screens/PreviewWA.html` |  | Preview link di grup WhatsApp (ilustrasi) | 390 |
| `screens/WebPollingPenginapanTamu.html` | `GuestPageContent` (STAY poll) | Web di HP: polling penginapan tanpa akun | 390 |
| `screens/OgPolling.html` | `og/poll.png` (static, owner decision) | Gambar preview link: polling (1200×630) | 1200 |
| `screens/OgUndangan.html` | `og/invite.png` (static, owner decision) | Gambar preview link: undangan trip (1200×630) | 1200 |

### Polling · edge case: seri, opsi diubah, offline, pembuat keluar, suara ganda

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/PollingSeri.html` |  | Polling: hasil seri, pembuat memilih | 390 |
| `screens/DialogHapusOpsiPolling.html` |  | Dialog: hapus opsi yang sudah dipilih | 390 |
| `screens/PollingPilihanDireset.html` |  | Polling: pilihan direset karena opsi diubah | 390 |
| `screens/PollingOffline.html` |  | Polling: vote saat offline | 390 |
| `screens/PollingTerlambat.html` |  | Dialog: vote offline tiba setelah polling ditutup | 390 |
| `screens/DialogKeluarAdaPolling.html` |  | Dialog: pembuat polling keluar dari trip | 390 |
| `screens/WebPilihNamaPolling.html` |  | Web di HP: pilih nama sebelum vote | 390 |

### Web · tab Grup dan polling

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebTripGrup.html` |  | Web: tab Grup | 1280 |
| `screens/WebPilihJenisPolling.html` |  | Web: buat polling, pilih jenis | 1280 |
| `screens/WebBuatPollingPenginapan.html` |  | Web: buat polling penginapan | 1280 |
| `screens/WebJelajahPenginapan.html` |  | Web: Jelajah penginapan dari polling | 1280 |
| `screens/WebBagikanPolling.html` |  | Web: polling terkirim, bagikan | 1280 |
| `screens/WebPollingPenginapan.html` |  | Web: vote penginapan + komentar | 1280 |
| `screens/WebPollingHasilSeri.html` |  | Web: hasil seri untuk pembuat | 1280 |

### Web · uang, struk, dokumen, rekap, packing, dan anggota grup

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebSaldoTagih.html` | `SaldoContent` (expanded) | Web: saldo, yang perlu bayar ke kamu | 1280 |
| `screens/WebIngatkanWA.html` | `RemindSheet` (dialog) | Web: ingatkan lewat WhatsApp | 1280 |
| `screens/WebBayarKe.html` | `PayToSheet` (dialog) | Web: bayar ke anggota | 1280 |
| `screens/WebKasBersama.html` | `KittyContent` (expanded) | Web: kas bersama (**removed 27 Oct 2026: the owner dropped the kitty; not built**) | 1280 |
| `screens/WebCatatSetoran.html` | `DepositSheet` (dialog) | Web: catat setoran kas (**removed 27 Oct 2026: the owner dropped the kitty; not built**) | 1280 |
| `screens/WebFotoStruk.html` | `ReceiptFlow` (pick, file only) | Web: unggah foto struk | 1280 |
| `screens/WebHasilStruk.html` | `ReceiptFlow` (result, dialog) | Web: cek hasil struk | 1280 |
| `screens/WebDokumenTrip.html` | `DocumentsScreen` (web: downloads) | Web: dokumen trip | 1280 |
| `screens/WebRekapTrip.html` | `RecapScreen` (web: "Unduh gambar") | Web: rekap trip | 1280 |
| `screens/WebPackingBersama.html` |  | Web: packing bersama | 1280 |
| `screens/WebAnggotaTanpaAkun.html` |  | Web: anggota, termasuk tanpa akun | 1280 |

### Web · polling tanggal, tempat makan, budget, dan edge case

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebBuatPollingTanggal.html` |  | Web: buat polling tanggal | 1280 |
| `screens/WebPollingTanggal.html` |  | Web: vote polling tanggal | 1280 |
| `screens/WebJelajahMakan.html` |  | Web: Jelajah tempat makan dari polling | 1280 |
| `screens/WebPollingBudgetAnonim.html` |  | Web: polling budget anonim (P2) | 1280 |
| `screens/WebPollingPilihanDireset.html` |  | Web: pilihan direset karena opsi diubah | 1280 |
| `screens/WebDialogHapusOpsiPolling.html` |  | Web: hapus opsi yang sudah dipilih | 1280 |
| `screens/WebDialogKeluarAdaPolling.html` |  | Web: pembuat polling keluar dari trip | 1280 |

### Destinasi · saran saat mengetik dan lokasi GPS

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/DestinasiKosong.html` |  | Destinasi: belum diketik | 390 |
| `screens/DestinasiSaran.html` |  | Destinasi: saran saat mengetik | 390 |
| `screens/DestinasiGPS.html` |  | Destinasi: dari lokasi saat ini | 390 |
| `screens/DestinasiIzinDitolak.html` |  | Destinasi: izin lokasi ditolak | 390 |
| `screens/PilihLokasiDekat.html` |  | Jelajah: pilih lokasi dekat (GPS) | 390 |

### Trip luar negeri · destinasi, mata uang, dan zona waktu

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/DestinasiSaranLuarNegeri.html` |  | Destinasi: saran luar negeri | 390 |
| `screens/BuatTripLuarNegeri.html` |  | Buat trip: destinasi luar negeri | 390 |
| `screens/PilihMataUangTrip.html` |  | Buat trip: pilih mata uang utama (luar negeri) | 390 |
| `screens/CatatPengeluaranLuarNegeri.html` |  | Catat pengeluaran: mata uang luar negeri | 390 |

### Daftar bawaan dari owner trip · disusun sendiri, dikirim ke semua anggota

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/MulaiDaftarAnggota.html` | `MemberListStartSheet` | Packing: owner mulai daftar bawaan anggota | 390 |
| `screens/SusunDaftarAnggota.html` | `MemberListEditor` | Packing: owner menyusun daftar bawaan anggota | 390 |
| `screens/KirimDaftarAnggota.html` | `MemberListSendSheet` | Packing: kirim daftar bawaan ke anggota | 390 |
| `screens/PackingOwnerGunung.html` | `PersonalPackingTab` (editor, `MemberListCard`) | Packing pribadi owner: progres anggota | 390 |
| `screens/PackingPribadiGunung.html` | `PersonalPackingTab` (member, `FromOwnerCard`) | Packing pribadi anggota: daftar dari owner trip | 390 |

### Polling anonim · toggle saat membuat, hasil tanpa nama pemilih

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/BuatPollingAnonim.html` |  | Buat polling: anonim aktif | 390 |
| `screens/PollingAnonimAnggota.html` |  | Polling anonim: anggota memilih | 390 |
| `screens/PollingAnonimHasil.html` |  | Polling anonim: hasil untuk pembuat | 390 |

### Web · saran destinasi, lokasi saat ini, dan trip luar negeri

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebDestinasiSaran.html` |  | Web: saran destinasi saat mengetik | 1280 |
| `screens/WebDestinasiGPS.html` |  | Web: destinasi dari lokasi saat ini | 1280 |
| `screens/WebDestinasiIzinDitolak.html` |  | Web: izin lokasi diblokir | 1280 |
| `screens/WebBuatTripLuarNegeri.html` |  | Web: buat trip ke luar negeri | 1280 |
| `screens/WebCatatPengeluaranLuarNegeri.html` |  | Web: catat pengeluaran dalam yen | 1280 |

### Web · daftar bawaan dari owner dan polling anonim

| File | Code name | Board title | Width |
| --- | --- | --- | --- |
| `screens/WebMulaiDaftarAnggota.html` |  | Web: mulai daftar bawaan anggota | 1280 |
| `screens/WebSusunDaftarAnggota.html` |  | Web: susun dan kirim daftar bawaan anggota | 1280 |
| `screens/WebPackingOwner.html` |  | Web: packing owner dengan progres anggota | 1280 |
| `screens/WebPackingAnggota.html` |  | Web: packing anggota dari daftar owner | 1280 |
| `screens/WebBuatPollingAnonim.html` |  | Web: buat polling anonim | 1280 |
| `screens/WebPollingAnonim.html` |  | Web: polling anonim, anggota memilih | 1280 |

## Not designed yet

Build these from existing patterns; ask the product owner before inventing new layouts: enter invite code, activity detail/edit (reuse *Tambah aktivitas* form), notifications, edit profile, language sheet (reuse *Sheet tema* pattern), search trips, all transactions list, Explore credits (Credits screen with balance, voucher field, packs and history; the out-of-credits state of `SwipeScreen`), dark theme for Itinerary/Packing/Explore/sheets/dialogs (apply the IRIS dark tokens; see *GelapBeranda*, *GelapPengeluaran*, *GelapSwipe* for the pattern).

**Group foundation (25 Oct 2026).** Stage 1 was built from the PRD text before the group boards were exported (`GroupTab`, `GroupActionSheet`, `AddMemberSheet`, `ClaimMemberContent`, `CommentsSheet`). The boards are exported now (see "Boards added after 14 Oct 2026"), so these screens get a design pass against `TripGrup`, `WebTripGrup`, `AnggotaTanpaAkun`, `TambahAnggotaNama`, `GabungPilihNama`, `KomentarOpsi`.

## Design decisions

See `../specs/design-decisions-id.md` (Indonesian; the agreed design rules) plus `../specs/itinerary-spec.md` and `../specs/packing-spec.md` (English).
