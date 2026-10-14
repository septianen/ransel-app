# To-do Desain — RANSEL (IRIS)

Status desain di kanvas "RANSEL — IRIS". Semua layar memakai design system IRIS, ukuran ponsel 390 × 844, kecuali disebut lain. Kanvas dikelompokkan per fitur (bukan per fase); urutan bagian di bawah mengikuti baris kanvas.

Terakhir diperbarui: 13 Okt 2026.

## Belum didesain

Fase 1–5 sudah lengkap. Yang tersisa adalah layar pendukung yang muncul dari keputusan terakhir atau tombol yang belum punya tujuan:

- [ ] **Alihkan kepemilikan** — pemilik memilih anggota yang jadi pemilik baru sebelum bisa keluar dari trip.
- [ ] **Masukkan kode undangan** — sheet/field kode dari tombol "Masukkan kode lain" di Undangan tidak berlaku (juga bisa dari Trip saya).
- [ ] **Detail / edit aktivitas** — tap item itinerary; kemungkinan cukup memakai form Tambah aktivitas dengan tombol Hapus.
- [ ] **Notifikasi** — tujuan tombol lonceng di Beranda.
- [ ] **Ubah profil** dan **sheet Bahasa** — dari halaman Akun.
- [ ] **Cari trip** dan **Lihat semua transaksi** — tujuan tombol cari di Trip saya dan link di Pengeluaran.
- [x] ~~Tempat disimpan tanpa trip~~ — dibuang. Jelajah selalu terikat ke satu trip; diganti layar "Jelajah: belum ada trip".
- [ ] **Tema gelap layar lain** — pola konversi sudah ada; terapkan ke Itinerary, Packing, Jelajah/Preferensi, sheet, dan dialog (bisa dikerjakan saat implementasi).

## Sudah didesain

### Beranda dan daftar trip
- [x] Beranda: kartu trip, kartu **Persiapan trip** (Itinerary hari terisi, Packing barang siap, Belum dijadwalkan), callout Jelajah AI
- [x] Kosong: Beranda (`capybara-phone`)
- [x] Trip saya: Sedang berjalan, Mendatang, Selesai
- [x] Kosong: Trip saya (`capybara-reading-map-compass`) dan versi tanpa trip mendatang (kartu putus-putus di atas daftar Selesai)
- [x] Tema gelap: Beranda

### Buat, edit, dan hapus trip
- [x] Buat trip, Edit trip (dengan Hapus trip)
- [x] Pilih tanggal trip (DateRangePicker M3, layar penuh)
- [x] Dialog hapus trip

### Itinerary
- [x] Itinerary dengan kotak **Belum dijadwalkan** (badge jumlah, 2 tempat teratas, "Lihat semua", tombol Jadwalkan)
- [x] Kosong: Itinerary (`capybara-world-map`)
- [x] Tambah aktivitas, Pilih jam (TimePicker M3, 24 jam)
- [x] Seret dan lepas: sedang digeser (border saja, tanpa miring), dialog ubah urutan, snackbar Urungkan
- [x] Belum dijadwalkan: semua (bottom sheet, filter Semua / Dari AI / Manual)
- [x] Jadwalkan tempat (bottom sheet: pilih hari 2×2, jam otomatis di akhir hari)
- [x] Detail trip versi web (1280)

### Packing
- [x] Packing, Kosong: Packing (`capybara-bedroll-lantern`)
- [x] Tambah barang (bottom sheet)
- [x] Menu ⋮ Packing → Pakai template (sheet Pilih template) dan Simpan sebagai template (sheet)

### Pengeluaran
- [x] Pengeluaran (solo), Kosong: Pengeluaran (dengan header + tab trip)
- [x] Catat pengeluaran (bottom sheet), Pilih tanggal (DatePickerDialog M3)
- [x] Tema gelap: Pengeluaran

### Rekomendasi AI (Jelajah)
- [x] Jelajah tanpa sesi: hero, Destinasi (text field), Tanggal (field terpisah → DateRangePicker), budget, minat, gaya jalan, catatan
- [x] Jelajah dengan sesi belum selesai: kartu "Lanjutkan" di atas form
- [x] Jelajah belum masuk (tamu): hero ajakan masuk, 3 manfaat, tombol Masuk dan Daftar
- [x] Jelajah belum ada trip: hero "Buat trip dulu, yuk", 3 langkah, tombol Buat trip
- [x] Swipe: memuat, kartu, geser kanan (SIMPAN), geser kiri (LEWATI), snackbar setelah simpan, habis (`capybara-pinning-map`)
- [x] Error (`capybara-umbrella-rain`), tidak ada hasil (`capybara-magnifier-map`), kuota habis (`capybara-firefly`)
- [x] Tema gelap: Swipe

### Onboarding, akun, dan sinkronisasi
- [x] Onboarding 3 layar, Masuk (tanpa ilustrasi), Daftar
- [x] Lupa kata sandi → Cek email → Kata sandi baru
- [x] Sesi berakhir (`capybara-tired-phone`)
- [x] Akun, sheet Mata uang, sheet Tema
- [x] Offline di Itinerary, snackbar Kembali online

### Anggota dan bagi biaya
- [x] Anggota trip (sisi pemilik), menu ubah peran, dialog keluarkan anggota
- [x] Anggota trip (sisi editor), dialog keluar dari trip
- [x] Undang anggota (bottom sheet), Gabung trip (`capybara-with-duck`), Undangan tidak berlaku (`capybara-scanning-qr`)
- [x] Pengeluaran bersama: tab Ringkasan (kartu "Posisimu")
- [x] Catat pengeluaran bersama: Bagi rata, Atur nominal (belum pas / pas)
- [x] Saldo dan pelunasan (tab Saldo, "Supaya lunas", Tandai lunas)

### State umum
- [x] Error koneksi (`capybara-sleeping`), error server (`capybara-tent`), segera hadir (`capybara-compass-map`)

## Keputusan desain

### Umum
- Tile kategori memakai warna aksen **bold** dengan **ikon putih**.
- Kategori itinerary (ID / EN): Wisata / Explore (biru), Kuliner / Eat (oranye), Perjalanan / Travel (teal), Menginap / Stay (ungu), Belanja / Shop (kuning), Lainnya / Other (cokelat). Enum: `ATTRACTION`, `FOOD`, `TRANSPORT`, `LODGING`, `SHOPPING`, `OTHER`. Lihat `itinerary-spec.md`. Warna kategori packing: lihat `packing-spec.md`.
- Kategori pengeluaran memakai **enum yang sama** dengan itinerary, tapi labelnya kata benda: Aktivitas, Makan, Transport, Penginapan, Belanja, Lainnya. Warnanya sama dengan itinerary (Transport teal, Aktivitas biru, Makan oranye, Penginapan ungu).
- Semua layar di dalam trip (Itinerary, Packing, Pengeluaran, termasuk empty state) memakai tombol header `group_add` yang membuka Anggota trip, bukan tombol bagikan.
- Kartu Sisa budget (Pengeluaran, web) memakai `accent-magenta` #D92D7B. Badge dan elemen AI memakai `secondary` (#D92D7B); kotak alasan AI memakai `tertiary-container` / `tertiary`.
- Pemilih tanggal dan jam memakai komponen bawaan Material 3 (jalan di Compose Multiplatform), diwarnai token IRIS. Pembatasan ke rentang trip lewat `selectableDates` opsional.
- Form panjang di Compose diletakkan di `Column` + `verticalScroll` supaya tombol tidak ikut mengecil.

### Beranda dan trip
- Beranda tidak menampilkan kartu Sisa budget dan section "Rencanakan". Gantinya kartu **Persiapan trip** (3 kolom, tiap kolom membuka layarnya). Pengeluaran dibuka lewat kartu trip, Jelajah lewat callout dan navigasi bawah.
- Trip saya: Sedang berjalan (kartu `primary` bold dengan progres budget), Mendatang (kartu putih + badge hitung mundur), Selesai (baris ringkas + total terpakai). "Buat trip" di FAB; FAB disembunyikan di empty state.
- Dialog hapus trip: `AlertDialog` M3, ikon di lingkaran `error-container`, ringkasan isi yang ikut terhapus, tombol "Batal" dan "Ya, hapus" (`error`). Pola yang sama dipakai untuk keluarkan anggota dan keluar dari trip.

### State dan ilustrasi
- Pola state: ilustrasi kapibara 220dp, judul 22/28 extra bold, deskripsi `body-large` `on-surface-variant`, tombol primary lebar penuh + tombol teks opsional, kode error kecil dari `error.code` API bila relevan.
- Empty state di dalam layar tetap menampilkan header, tab, dan chip hari; ilustrasi 180–200dp; FAB disembunyikan supaya tidak ada dua tombol tambah.
- Ilustrasi hanya untuk state satu layar penuh atau empty state, tidak sebagai dekorasi di layar yang sudah berisi konten (mis. kartu "Belum ada trip mendatang" memakai ikon, bukan ilustrasi).
- Sumber ilustrasi: IRIS, grup aset "Ilustrasi" (PNG transparan 1024 px). Belum dipakai: `capybara-cooking`, `capybara-piggy-bank`, dan beberapa pose lain.
- Copy state bernada santai bertema perjalanan ("Waduh, sinyalnya lagi tidur", "Dompetmu masih utuh", "Rekomendasinya lagi kehujanan", "Petanya masih kosong").

### Tema gelap
- Latar `surface` #131313, kartu dan bar #1D1D1D, chip netral #262626; teks #E8E8E8 / #C1C1C1; tombol primary #5ED051 dengan teks #012D00; container memakai nilai gelapnya. Aksen bold tetap dengan ikon putih; tombol bulat magenta dan badge memakai `secondary` gelap #FF9BBC / #4F0428; callout `tertiary-container` #5A226B dengan teks #EE99FF.

### Itinerary
- Seret dan lepas: durasi tetap, jadwal dirapatkan mulai jam paling awal di rentang yang digeser; dialog konfirmasi (bisa dimatikan) lalu snackbar Urungkan. Kartu yang digeser cukup diberi border 2px `primary`. Detail di `itinerary-spec.md`.
- Tempat dari Jelajah masuk **Belum dijadwalkan**, bukan langsung ke hari tertentu. Menjadwalkan lewat tombol + sheet (bukan seret antar-hari): ditaruh di akhir hari terpilih, mulai dari jam selesai aktivitas terakhir (hari kosong mulai 09.00), durasi dari estimasi AI; jam bisa diubah lewat time picker.
- Web (≥ 1240): navigation rail 96dp, itinerary di kolom tengah, panel kanan 360dp (Sisa budget, ringkasan Packing, Belum dijadwalkan).

### Jelajah AI
- Tab Jelajah selalu membuka layar Jelajah (hero + form, dengan navigasi bawah). Sesi swipe yang belum selesai tampil sebagai kartu "Lanjutkan" di atas, dan ikon Jelajah di navigasi bawah diberi titik magenta (bukan angka).
- Swipe dibuka layar penuh di atas tab; tombol tutup kembali ke Jelajah. Sesi selesai saat semua kartu dilihat atau user memulai pencarian baru.
- Destinasi berupa text field biasa (bukan dropdown); Tanggal field terpisah yang membuka DateRangePicker. Keduanya terisi dari trip, dengan keterangan trip tujuan di bawahnya.
- Kartu swipe digeser horizontal tanpa dimiringkan, dengan outline + label (SIMPAN magenta, LEWATI abu).
- Jelajah **wajib masuk**. Tamu yang membuka tab Jelajah melihat layar ajakan masuk (bukan form); setelah masuk, trip di perangkat dipindah ke akun. Fitur lain tetap bisa tanpa akun.
- Kuota 10 pencarian per user per hari, terisi lagi 00.00.
- Jelajah selalu terikat ke satu trip (tidak ada "tempat disimpan tanpa trip"). User yang sudah masuk tapi belum punya trip melihat layar "Buat trip dulu".
- Web: tidak ada opsi "Pakai tanpa akun dulu" di layar Masuk dan tidak ada banner offline; kalau koneksi putus tampil state error koneksi.

### Akun dan sinkronisasi
- Masuk tanpa ilustrasi, dengan opsi "Pakai tanpa akun dulu" (offline-first). Daftar dan Kata sandi baru menampilkan syarat kata sandi yang tercentang langsung.
- Lupa kata sandi: link berlaku 30 menit, kirim ulang dengan hitung mundur, setelah kata sandi baru disimpan semua perangkat lain keluar.
- Sesi berakhir: layar penuh dengan opsi "Nanti saja, pakai offline"; perubahan yang menunggu dikirim setelah masuk lagi.
- Mata uang default hanya berlaku untuk trip baru. Tema: Ikuti sistem / Terang / Gelap dengan kartu pratinjau.
- Offline: banner `inverse-surface` di bawah tab dan penanda `cloud_upload` pada item yang belum terkirim; saat online lagi muncul snackbar "Kembali online · n perubahan terkirim".

### Anggota dan bagi biaya
- Avatar anggota: inisial di lingkaran warna tetap per orang (hijau, ungu, cokelat, magenta). Peran: Pemilik / Editor / Pembaca (OWNER / EDITOR / VIEWER). Hanya pemilik yang bisa mengundang, mengubah peran, dan mengeluarkan anggota.
- Undangan: kode `NAMA-XXXX`, link `ransel.app/j/<kode>`, bisa dicabut. Undangan gagal memakai satu layar dengan judul per alasan: kedaluwarsa (INVITE_EXPIRED), dicabut (INVITE_REVOKED), kuota penuh (INVITE_FULL).
- Keluarkan / keluar dari trip: catatan pengeluaran tetap tersimpan; dialog keluar memperingatkan saldo yang belum lunas. Pemilik harus mengalihkan kepemilikan dulu sebelum keluar.
- Split bill: default bagi rata ke anggota yang dicentang; Atur nominal menampilkan selisih "kurang/lebih" dan Simpan nonaktif sampai pas.
- Pengeluaran bersama: tab Ringkasan (kartu "Posisimu", avatar pembayar di ikon transaksi, "bagianmu Rp…") dan tab Saldo (positif `primary`, negatif `error`, "Supaya lunas" dengan transfer minimum).
- Template packing lewat menu ⋮ Packing. Simpan sebagai template: status centang tidak ikut, jumlah barang opsional, bisa dibagikan ke anggota trip.

## Catatan untuk backend

- Semua kebutuhan tabel dari desain sudah masuk ke `db/migration` V1–V4 (belum ada database yang dibuat, jadi tidak perlu V5): kolom `category`, `duration_min` di itinerary; `payment_method` di expenses; `owner_id` nullable + `shared_trip_id` di packing_templates; `default_currency` di users; tabel `password_reset_tokens`, `settlements`, `ai_usage`. `trip_invites.used_count` sudah ada sejak awal di V3.
- Endpoint `PUT /trips/{id}/days/{date}/order` untuk urutan hasil seret; `POST /trips/{id}/settlements` untuk "Tandai lunas".
- Kode error lain: AI_UNAVAILABLE, AI_QUOTA_EXCEEDED, SPLIT_TOTAL_MISMATCH, OWNER_CANNOT_LEAVE.
- Kode error undangan: INVITE_EXPIRED, INVITE_REVOKED, INVITE_FULL.
- Endpoint alihkan kepemilikan trip (OWNER → anggota lain) sebelum pemilik boleh keluar.
- Kuota pencarian AI: **10 per user per hari**, reset 00.00 (`AI_QUOTA_EXCEEDED` / 429). Layar kuota habis sudah memakai "10 dari 10".
- Database lokal: Room 3 (`androidx.room3`) hanya di Android dan iOS. Web online-only (tanpa database lokal, wajib masuk), mulai di Fase 2.

## Data contoh

Trip "Bali 4 hari", 20–23 Nov 2026, budget Rp5.000.000, terpakai Rp3.250.000; packing 5 dari 9 barang siap; anggota Septian (pemilik), Dinda dan Raka (editor), Maya (pembaca); 5 tempat belum dijadwalkan.
