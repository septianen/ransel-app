# PRD: Explore Inspirasi (AI Interview) — RANSEL

21 Nov 2026 · PM: Benn · Owner: Boss (Septian) · Status: Draft v0, nunggu review Boss

## Ringkasan

Explore sekarang (lihat `AI-Recommendation-Spec.md`) mulai dari **trip yang udah ada**: user milih trip, isi preferensi, lalu swipe tempat. Explore Inspirasi kebalikannya: buat user yang **belum punya tujuan**. AI ngeinterview user soal minat dan hobinya secara dinamis, lalu ngasih rekomendasi **jenis wisata + daerah** yang bisa langsung dijadiin trip.

Contoh: user gak pernah kepikiran ke Pangkalan Bun, tapi karena minatnya satwa dan fotografi, Explore nunjukin "Feeding orang utan · Tanjung Puting, Kalteng", dan dia jadi pengen ke sana.

Versi pertama yang direncanakan adalah **versi berbayar (pakai credit)**.

## Masalah & tujuan

- User trip planner biasanya udah tau mau ke mana. Explore saat ini gak bantu user yang belum punya tujuan.
- Yang bikin orang pengen jalan sering kali **hobinya** (thrifting, birding, freediving), bukan nama tempatnya.
- Tujuan: user tanpa tujuan bisa nemu ide trip yang belum pernah kepikiran, lalu langsung bikin trip dari situ.

## Keputusan yang udah disepakati (20–21 Nov 2026)

1. Explore jadi tempat inspirasi, bukan cuma cari rekomendasi. Layout feed: **Opsi A** (satu kartu foto besar per layar).
2. Versi pertama = **interview AI dinamis** (berbayar). Pertanyaan berikutnya disusun AI dari jawaban sebelumnya.
3. Output berupa **jenis wisata + daerah/area**, bukan nama toko, restoran, atau operator.
4. Credit: dipotong **sekali per sesi**, hanya kalau hasil akhir sukses (mekanisme `CreditService` sudah ada). **Tarif ditunda** sampai kualitas fitur keliatan dari spike.
5. Estimasi biaya **masuk**, berlabel "perkiraan AI".
6. Operator/tempat kursus **ditunda** ke fase berikutnya.
7. Fitur sosial / cari partner **keluar** dari v1. Diganti tombol "Cari buddy" (segera hadir) + tracker permintaan.
8. Cakupan daerah: dalam kota, luar kota (Indonesia), dan luar negeri.

## Alur user

1. **Hero section** di Explore:
   - Field bebas "Lagi pengen ngapain?". Kalau user ngetik detail ("freediving pemula deket Bali"), AI lompat slot yang udah kejawab.
   - Chip minat di bawahnya: Fotografi, Belanja, Kuliner, Olahraga & outdoor, Musik & event, Sejarah & budaya, Satwa & alam, Healing. **Multi-pilih, maks 5** (batas di server).
   - Tombol menampilkan biaya sebelum ditap ("Tanya AI · X kredit" / jatah gratis bila ada). Angka tarif = placeholder.
2. **Interview dinamis** (lihat Slot). Tiap langkah: 1 pertanyaan + 3–5 opsi kartu (ikon + teks) + "Lainnya" (ketik sendiri). Progress = bar tipis (jumlah langkah dinamis). Mulai langkah 3 ada tombol "Udah cukup, lihat inspirasi".
3. **Hasil** (lihat Output).
4. **Bikin trip dari sini** → draft trip baru dengan daerah + durasi terisi, masuk ke alur trip biasa.

## Slot interview

Urutan slot tetap, isi pertanyaannya dinamis dan selalu diturunkan dari jawaban sebelumnya. AI boleh melompati slot yang sudah terjawab.

| # | Slot | Isi | Catatan |
| --- | --- | --- | --- |
| 1 | Minat utama | Chip / teks bebas, maks 5 | — |
| 2 | Nyaring kombinasi | AI bikin 3–5 kombinasi dari minat yang dipilih, tiap opsi diberi label asal minatnya (mis. "📷+🍜 Street photography"), user pilih **1**. Plus "Fokus satu aja" dan "Lainnya". | Dilewati kalau user cuma pilih 1 minat. Opsi yang cuma pakai 1 minat dibuang server. |
| 3 | Jarak | Dalam kota / Luar kota (Indonesia) / Luar negeri / Bebas | Kota asal dari izin lokasi kasar (sekali, disimpan di profil); kalau ditolak, AI nanya sekali. |
| 4 | Detail spesifik | Turunan dari kombinasi: jenis, level, gaya | Tempat "pemicu" trip biasanya ketemu di sini. |
| 5 | Konteks | Durasi, kata-katanya ikut jarak (dalam kota: setengah hari/seharian; luar negeri: durasi + paspor/visa) | — |
| 6 | Opsional | Hanya kalau jawaban masih ambigu | Maks total ~6 langkah. |

### Aturan wajib per minat (disimpan di server, bisa ditambah tanpa rilis app)

- Aktivitas butuh skill (diving, freediving, climbing, dsb.): **wajib** nanya level/sertifikat dan "Udah punya buddy?".
- Belanja: wajib nanya budget.
- Luar negeri: wajib nyinggung paspor/visa (status perkiraan).

### Contoh turunan

| Slot 1 | Slot 2/4 | Arah hasil |
| --- | --- | --- |
| Fotografi | Landscape / Street / Wildlife / Bird | Daerah birding, kota street photo |
| Belanja | Thrifting / Tas branded / Kerajinan lokal → baju band, denim vintage | Area pasar thrift (mis. Blok M kalau dalam kota Jakarta) |
| Fotografi + Belanja | Thrifting kamera | Glodok (dalam kota), Tokyo (luar negeri) |
| Olahraga | Freediving → pemula, belum punya buddy | Spot ramah pemula, mis. Nusa Penida, Menjangan |

## Output

- **Header profil minat**: ringkasan satu baris ("Freediving · Pemula · ≤3 hari · dekat Jawa–Bali"), tiap bagian berupa chip yang bisa diubah → AI ngitung ulang tanpa ulang interview, tanpa potong credit lagi.
- **3–5 kartu spot** (layout Opsi A): foto, judul "jenis wisata · daerah", 1 baris "kenapa cocok buat lo", chip Akses / Musim terbaik / Level.
- **1 kartu kejutan**: minat sama, daerah yang gak kepikiran.
- **Tap kartu → sheet "Yang perlu lo tau"**: syarat (sertifikat dsb.), kisaran biaya (label "perkiraan AI, cek lagi sebelum berangkat"), tombol **Bikin trip dari sini**, Simpan.
- **"Bukan ini / Gak sreg? Tanya lagi"**: AI nanya 1 pertanyaan lagi dan ganti kartu, masih sesi yang sama.
- **"Cari buddy"** (muncul kalau user jawab belum punya buddy): tombol outlined + badge "Segera hadir" → sheet "Kabarin gw".
- Foto spot dari layanan peta yang udah dipakai Explore (cari pakai nama spot + daerah); fallback ilustrasi/ikon.

## Keluar dari v1

- Nama operator / tempat kursus / toko / restoran.
- Fitur sosial: posting, cari partner, komentar, like (v2, tergantung data "Cari buddy").
- Itinerary per hari.
- Tarif final credit.
- Feed gratis dari database kurasi (direncanakan, bukan bagian dokumen ini).

## Tracking

- Tiap tap "Kabarin gw" dicatat per user + minatnya (bukti permintaan fitur sosial v2).
- Log tiap sesi interview: pertanyaan, opsi, pilihan user, hasil, berhasil/gagal. Dipakai buat review kualitas AI dan nentuin tarif.
- Event: sesi mulai, sesi selesai, "Udah cukup" ditap, kartu ditap, "Bikin trip dari sini" ditap.

## Selesai kalau (acceptance)

- [ ] User tanpa trip bisa buka Explore, isi minat (chip atau teks), jalanin interview, dan dapet 3–5 kartu jenis wisata + daerah.
- [ ] Pilih Fotografi + Belanja → slot 2 ngasih opsi kombinasi berlabel, salah satunya gabungan dua minat itu; pilih 1 minat → slot 2 dilewati.
- [ ] Pilih 6 chip → chip ke-6 gak bisa dipilih, muncul pesan batas.
- [ ] Freediving pemula → AI pasti nanya level/sertifikat dan buddy sebelum hasil.
- [ ] Pilih "Dalam kota" dari Jakarta → hasil berupa area di Jakarta; gak ada pertanyaan durasi nginep.
- [ ] Ketik "street photography di Bangkok" → AI gak nanya ulang minat dan jarak.
- [ ] Credit cuma kepotong sekali per sesi dan hanya kalau hasil muncul; AI gagal → gak kepotong. Ubah chip di header → gak kepotong lagi.
- [ ] Tap "Bikin trip dari sini" → draft trip terbentuk dengan daerah + durasi terisi.
- [ ] Tap "Kabarin gw" → tercatat di server dengan minat user.
- [ ] Kisaran biaya & visa selalu berlabel "perkiraan AI".

## Pertanyaan terbuka

1. Tarif per sesi & apakah jatah gratis harian berlaku buat interview — nunggu hasil spike.
2. Hubungan dengan Explore yang sekarang (trip-first + swipe): Explore Inspirasi jadi layar pertama tab Explore, dan trip-first jadi shortcut dari dalam trip? Perlu keputusan Boss.
3. "Simpan" hasil disimpen ke mana (daftar inspirasi tersimpan di profil?).

## Desain

- Figma: [Explore Inspirasi — Moodboard v0](https://www.figma.com/design/d9M4z0W173FPkD2JuSRXkm?node-id=492-2) (Opsi A dipilih).
- Board UI interview + hasil: _menyusul dari Rockstar_.

## Teknis

TRD menyusul dari Roux (endpoint interview, format output tetap, fallback, aturan wajib di server, logging, credit per sesi).
