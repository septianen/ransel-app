# TRD: Explore Inspirasi (AI Interview)

21 Nov 2026 · Roux · Status: Draft, nunggu review Boss · PRD: `docs/specs/explore-inspirasi-prd.md`

Bahasa santai-teknis. Ini **desain implementasi**, belum kode. Explore trip-first yang hidup sekarang (`docs/specs/AI-Recommendation-Spec.md`, `POST /recommendations`) **tetap**. Inspirasi = pintu baru: user belum punya tujuan → interview minat → kartu jenis wisata + daerah → bikin trip.

Jangan ketuker sama **Wawancara Buat trip** yang sudah ada (`composeApp/.../feature/interview/`, `shared/.../domain/TripInterview.kt`, kolom trip di V8). Itu wawancara destination/tanggal/kendaraan buat **bikin trip**, bukan interview minat Inspirasi.

Kalau dokumen ini beda sama kode, kode `shared/` + migrasi yang menang buat bentuk data. PRD yang menang buat perilaku.

---

## 1. Arsitektur

### Yang sudah ada (dipakai ulang)

| Bagian | Path |
| --- | --- |
| Explore search | `server/.../ai/RecommendationService.kt`, `routes/RecommendationRoutes.kt` |
| Saran minat destinasi | `server/.../ai/ExploreSuggestionService.kt`, `routes/ExploreRoutes.kt` |
| LLM | `GeminiClient` implements `LlmClient` (`AiPorts.kt`); factory `AiServicesFactory.kt` |
| Prompt + parse JSON | `server/.../ai/Prompt.kt`, `LlmOutput.kt` (`responseMimeType = application/json`) |
| Places + foto | `GooglePlacesClient`, `PlacePhotoService`, `PhotoSigner`, `routes/PlacePhotoRoutes.kt` |
| Credit | `CreditService.requireAffordable` sebelum kerja mahal, `charge` sesudah sukses |
| Setting / tarif | `SettingsService` + `app_settings` + `SettingKey` |
| Budget AI | `UsageMeter` / metered LLM → 503 `AI_PAUSED` kalau `CACHE_ONLY` |
| Email gate | `requireVerifiedEmail` (sama kayak `/recommendations`) |
| Bikin trip | `POST /trips` → `TripService.create` + `CreateTripRequest` |

Server **satu instance** (tidak ada Redis di repo). State yang harus selamat dari restart / retry credit → **Postgres**, bukan `ConcurrentHashMap` (itu cuma lock in-flight di `RecommendationService.searching`).

### Endpoint baru (usulan)

Prefix `ApiRoutes` Phase 5, bearer + `requireVerifiedEmail`. Nama path English. Session id = **UUID dari client** (aturan ID RANSEL).

| Method | Path | Kapan | LLM? | Credit |
| --- | --- | --- | --- | --- |
| `POST` | `/explore/inspirasi` | start: chip/teks hero | ya, langkah 1 | `requireAffordable` saja |
| `POST` | `/explore/inspirasi/{id}/step` | jawab 1 opsi / freetext | ya, pertanyaan berikutnya | tidak |
| `POST` | `/explore/inspirasi/{id}/finish` | "Udah cukup" atau slot lengkap | ya, kartu | `charge` sekali kalau 3–5+kejutan valid |
| `POST` | `/explore/inspirasi/{id}/refine` | ganti chip header / "Tanya lagi" | ya, kartu baru | tidak |
| `GET` | `/explore/inspirasi/{id}` | resume / hasil tersimpan | tidak | tidak |
| `POST` | `/explore/inspirasi/{id}/buddy` | "Kabarin gw" | tidak | tidak |

Flag mati → 404 `NOT_FOUND` (app lama tidak lihat hero). 402 `INSUFFICIENT_CREDITS` di start. 503 `AI_UNAVAILABLE` / `AI_PAUSED` tanpa charge. 422 `VALIDATION_FAILED` (`interests` = `TOO_MANY`, `session` = `FINISHED` / `EXPIRED`, `step` = `TOO_MANY`).

Rate limit: bucket terpisah, pola sama `SuggestionsRateLimit` (`ExploreRoutes.kt`). Angka lewat setting (default usulan 20 sesi/jam/user). Satu sesi in-flight per user (copy `oneSearchAtATime`).

### Stateless vs session store

**Pilih session store di DB.** Stateless murni (client kirim seluruh transcript tiap kali) enak buat LLM, tapi credit idempotency, log kualitas, refine, dan resume butuh baris server.

- Client kirim `sessionId` di start (UUID).
- Server simpan `inspirasi_sessions`: owner, status (`OPEN` / `CHARGED` / `FAILED` / `EXPIRED`), slot terisi, langkah, transcript JSON, hasil JSON, `charged_at`.
- Tiap step: server **source of truth**. Client tidak “nambah langkah sendiri”.
- TTL usulan 24 jam `OPEN` tanpa finish; sesi `CHARGED` disimpan lebih lama buat log (ikut retention log, 180 hari — sama `SearchLog`).

`ConcurrentHashMap` cuma lock “sedang generate”, bukan isi sesi.

### Max steps

PRD: ~6. Server: `MAX_STEPS = 6`. `canFinish = true` mulai **langkah 3** (setelah minimal minat + jarak, atau slot wajib per minat sudah keisi). Step ke-7 → 422. Slot 6 hanya kalau masih ambigu **dan** aturan wajib belum lengkap.

---

## 2. DTO (`shared`)

File baru usulan: `shared/.../dto/InspirasiDtos.kt`. Enum di `Enums.kt`. Route di `ApiRoutes.kt`. Commit **sendiri** + baris `CONTRACT_CHANGES.md` (additive).

Chip hero **bukan** `Interest` Explore yang ada (`NATURE, FOOD, CULTURE, SHOPPING, ADVENTURE, NIGHTLIFE, PHOTO, RELAX` di `Enums.kt`). Label PRD beda (Musik & event, Satwa & alam, Healing, Olahraga & outdoor). Enum baru, serialisasi **nama konstanta**:

`PHOTOGRAPHY, SHOPPING, FOOD, OUTDOOR, MUSIC, HERITAGE, WILDLIFE, HEALING`

Teks bebas hero = `prompt: String?`, bukan enum.

### Pertanyaan (step response)

```
InspirasiStepDto
  sessionId: String
  stepIndex: Int            // 0-based, yang baru ditampilkan
  slot: InspirasiSlot       // INTERESTS, COMBO, DISTANCE, DETAIL, CONTEXT, OPTIONAL
  question: String
  options: List<InspirasiOptionDto>   // 3–5; server buang yang ilegal
  allowFreeText: Boolean    // true → UI "Lainnya"
  canFinish: Boolean
  done: Boolean             // true → body hasil, bukan pertanyaan
  skippedSlots: List<InspirasiSlot>
  credits: CreditsDto?      // terisi setelah charge sukses
  result: InspirasiResultDto?         // hanya jika done
```

```
InspirasiOptionDto
  id: String                // stabil dalam 1 langkah, bukan label
  label: String
  icon: String              // key allowlist, lihat bawah
  sourceInterests: List<InspirasiInterest>  // slot 2: asal kombinasi; kosong di slot lain
```

**Ikon:** LLM / tree cuma boleh kirim **nama property** yang ada di `iris/.../ui/symbols/Symbols.kt` (subset Material Symbols Rounded yang sudah di-bundle). Allowlist server = set nama itu (`photoCamera`, `shoppingBag`, `restaurant`, `hiking`, `forest`, `theaterComedy`, `templeHindu`, `spa` **tidak ada** di `Symbols` sekarang — fallback `explore` / `autoAwesome`). Jangan terima ligature mentah atau nama di luar font: app tidak bisa gambar. Kalau desain butuh ikon baru, frontend nambah subset dulu.

### Request

```
StartInspirasiRequest
  id: String                // UUID client
  interests: List<InspirasiInterest>  // 0–5
  prompt: String?           // "Lagi pengen ngapain?", max usulan 280
  homeArea: InspirasiHomeAreaDto?     // hasil reverse kasar, opsional

InspirasiStepRequest
  optionId: String?         // xor freeText
  freeText: String?

RefineInspirasiRequest
  profileChips: List<InspirasiChipDto>  // ganti header
  extraQuestion: String?                // "Tanya lagi"
```

`done` di step response: setelah finish/refine sukses. Start/step biasa: `done = false`.

---

## 3. Skema output akhir

Bukan `RecommendationItem` (itu **tempat Google** + `placeId` wajib). Kartu Inspirasi = **jenis + daerah**; `placeId` foto **opsional** (hasil lookup).

```
InspirasiResultDto
  profile: List<InspirasiChipDto>     // satu baris chip, max 5
  cards: List<InspirasiCardDto>       // 3–5, surprise = false
  surprise: InspirasiCardDto          // minat sama, daerah lain
  estimatedDisclaimer: String         // copy server, bilingual via Accept-Language
  showFindBuddy: Boolean              // jawaban buddy = belum
```

```
InspirasiChipDto
  slot: InspirasiSlot                 // atau key bebas "level" / "duration"
  id: String
  label: String                       // "Freediving", "Pemula"
```

```
InspirasiCardDto
  id: String
  type: String                        // "Feeding orang utan"
  area: String                        // "Tanjung Puting, Kalteng"
  why: String                         // 1 baris
  access: String                      // chip
  bestSeason: String
  level: String?
  requirements: List<String>          // sheet
  costRange: InspirasiCostRangeDto?
  estimated: InspirasiEstimatedFlags
  surprise: Boolean
  photoUrl: String?                   // signed path, pola RecommendationItem
  photoAttribution: String?
  placeId: String?                    // internal foto; jangan jadi "nama tempat"
  durationDays: Int?                  // buat mapping trip
  durationLabel: String?              // "seharian" / "≤3 hari"
```

```
InspirasiCostRangeDto
  minMinor: Long
  maxMinor: Long
  currency: String                    // biasanya IDR; luar negeri bisa USD lalu convert FxRates
  per: InspirasiCostPer              // PERSON / TRIP / DAY — TBD spike; default PERSON

InspirasiEstimatedFlags
  cost: Boolean                       // selalu true kalau costRange ada
  visa: Boolean                       // true kalau luar negeri / paspor disinggung
  season: Boolean                     // true
```

UI wajib nampilin label "perkiraan AI" kalau flag true. Copy jangan di-hardcode tarif di app.

Validasi server sebelum `done`: 3–5 kartu + 1 surprise; tiap kartu `type`+`area` non-blank; bukan nama operator/toko/resto (prompt + filter kata; **bukan** Places Text Search sebagai syarat hidup kartu — beda sama Explore search yang drop kandidat kalau Places miss). Foto miss → `photoUrl = null`, kartu tetap.

---

## 4. Prompt / LLM

**Provider yang dipakai sekarang:** Gemini Developer API, `GeminiClient` (`x-goog-api-key`, `generateContent`, `responseMimeType = application/json`). Model per call dari `SettingKey.LLM_MODEL` / `AppConfig.llmModel`; fallback model env `llmFallbackModel` kalau 429/5xx. Tanpa key: `NotConfiguredLlm` → 503 `AI_UNAVAILABLE`.

**Jangan** nambah method ke `LlmClient` tanpa perlu: bisa `generate` internal baru di `GeminiClient` (usulan `interviewStep` / `inspirasiCards`) **atau** perpanjang interface. Explore `suggest(LlmQuery)` **tidak cocok** (itu kandidat tempat + destinasi trip).

Pola retry **sudah ada** di `RecommendationService.askLlm`: 2 percobaan, parse gagal / transport → coba lagi, lalu 503. Inspirasi copy itu. Parse lenient: `LlmOutput` (fence markdown, `ignoreUnknownKeys`). Schema JSON **kita** yang dipaksa di prompt + validasi Kotlin, bukan JSON Schema Google (belum dipakai).

**1 retry LLM, lalu fallback tree statis** (beda dari search yang 503). Tree: JSON di `server/src/main/resources/inspirasi/fallback-tree.json` (pertanyaan per slot + opsi). Kartu fallback: 3–5 + 1 surprise dari daftar kurasi per minat+jarak, **bukan** LLM. Kalau tree juga kosong → 503, **tidak charge**.

Prompt system (usulan, file `Prompt.kt` atau `InspirasiPrompt.kt`):

- Output JSON doang.
- Kartu = jenis wisata + daerah, **dilarang** nama toko / resto / operator / kursus.
- Turunkan pertanyaan dari jawaban sebelumnya; **jangan** tanya ulang slot yang `filled`.
- Sisipkan **aturan wajib** dari store (bawah).
- Bahasa = `Accept-Language` (`id` default, pola `ExploreRoutes`).
- Estimasi biaya & visa selalu “perkiraan”.

Aturan per minat **server-side, editable tanpa rilis app:**

Usulan tabel `inspirasi_interest_rules` (`interest` text / glob, `required_slots` text[], `prompt_addendum` text, `enabled`). Seed: skill (diving, freediving, climbing, …) → DETAIL level/sertifikat + buddy; `SHOPPING` → budget; jarak `ABROAD` → paspor/visa. Karung v1 **belum punya** editor JSON bebas — opsi: seed + SQL/override `app_settings` key `INSPIRASI_RULES_JSON` (nilai text, dicek `validateSetting`). UI Karung = TBD (bukan v1 wajib).

`AiBudget` CACHE_ONLY berlaku: panggilan LLM Inspirasi ikut meter, 503 `AI_PAUSED`, tidak charge.

---

## 5. Slot logic

Urutan tetap: INTERESTS → COMBO → DISTANCE → DETAIL → CONTEXT → OPTIONAL. AI isi **teks** pertanyaan; server yang boleh skip.

| Aturan | Siapa |
| --- | --- |
| Max 5 chip | Client disable ke-6 + pesan; server 422 `TOO_MANY` kalau >5. Domain helper di `shared/domain` (satu sumber). |
| COMBO skip kalau minat = 1 (chip atau 1 tema dari prompt) | Server, sebelum LLM. |
| Opsi COMBO yang cuma 1 minat → dibuang | Server filter `sourceInterests.size >= 2`, kecuali opsi sistem `FOCUS_ONE` / `OTHER`. |
| Skip slot yang sudah terjawab (prompt "street photography di Bangkok" → minat + jarak) | LLM usul `skip`; **server** cek: ada minat + ada sinyal kota/negara di prompt. Jangan percaya LLM doang. |
| Kota asal kasar | Lihat lokasi. |
| Slot 6 hanya ambigu / aturan wajib belum | Server, pakai rules table. |
| Dalam kota → opsi durasi setengah hari/seharian, **bukan** nginep | Prompt + validasi opsi CONTEXT. |

Kombinasi: LLM boleh, tapi post-filter wajib. Test PRD Fotografi+Belanja: minimal satu opsi `sourceInterests` mengandung keduanya.

---

## 6. Credit

Mekanisme yang **sudah hidup** (`CreditService.kt`, `planCreditSpend` di `shared/.../domain/Credits.kt`):

- Cek dulu `requireAffordable`, kerja, baru `charge`.
- Search kosong **tidak** dipotong (`RecommendationService` ~baris 240–248, owner 4 Nov 2026). Inspirasi sama: hasil invalid / LLM gagal / tree gagal → 0.
- Free daily dulu, baru wallet; reset 00:00 WIB.
- Ledger `credit_ledger`; unique `idempotency_key`.

**Yang belum ada (harus ditambah di implementasi, bukan sekarang):**

- `CreditAction` hanya `SEARCH, NEXT_BATCH, PLACE_DETAIL, RECEIPT` (`Enums.kt`). CHECK SQL `credit_ledger_action_check` di `V1__init.sql` daftar yang sama.
- `CreditService.cost` / `CreditCostsDto` belum punya field interview.
- `charge()` **tidak** terima idempotency key (cuma `addCredits` / `adjust` yang pakai). Double-tap finish bisa dobel potong kalau tidak diubah.

Usulan:

1. `CreditAction.INSPIRASI` (jangan `INTERVIEW` — bentrok konsep wawancara trip).
2. `SettingKey.COST_INSPIRASI` + env `CREDIT_COST_INSPIRASI` (default **placeholder**, boleh 0 selama spike). Karung override, pola `COST_RECEIPT`.
3. `CreditCostsDto.inspirasi: Int = 0` (additive, app lama aman).
4. `charge(..., idempotencyKey=)` ; start/finish pakai `inspirasi:<sessionId>`. Replay finish → snapshot, tidak potong lagi.
5. `requireAffordable` di **start** (tombol sudah nunjukin harga dari `GET /credits`). Finish cek lagi; kalau saldo habis di tengah → 402, sesi `OPEN` tetap, belum `CHARGED`.
6. Refine / ganti chip / step / buddy → tidak `charge`.
7. **Jatah gratis harian:** PRD open Q1. Default teknis: `planCreditSpend` berlaku (free dulu) supaya tidak cabang baru; tarif 0 = selalu “gratis”. Ganti tanpa rilis app lewat Karung.

Tombol hero baca `credits.costs.inspirasi` + `available` (sudah di `ExploreTripPickerScreen` / `CreditsDto`).

---

## 7. Foto

Reuse pipeline Explore:

1. `PlacesClient.findPlaceId(name = type, destination = area)` (`GooglePlacesClient`, Text Search IDs Only).
2. `PhotoSigner.signedPath(placeId)` → `RecommendationItem.photoUrl` style (`/places/photo/{id}?exp=&sig=`).
3. GET publik `PlacePhotoRoutes` + `PlacePhotoService` (LRU, 404 kalau kosong/quota).

Kalau find/photo gagal: `photoUrl = null`. **App** fallback ilustrasi/ikon (IRIS), jangan 4xx-kan kartu. Jangan Place Details Enterprise (`rating` / `priceLevel` memang sengaja tidak dipanggil).

Kartu kejutan: query nama jenis yang sama + daerah beda, lookup terpisah.

---

## 8. Tracking

### Tabel buddy (PRD)

`inspirasi_buddy_requests`: `id`, `user_id` FK `users`, `session_id` FK, `interests text[]`, `created_at`. Unique `(user_id, session_id)` supaya tap dobel bukan dua bukti. **Bukan** fitur sosial. Tidak ada notifikasi.

### Log sesi

Jangan memaksa `ai_search_log` (kolom `destination` NOT NULL, `kind` CHECK `EXPLORE|POLL`, `place_ids` — bentuk search). Bisa **nambah** `AiSearchKind.INSPIRASI` + relax CHECK **atau** tabel sendiri. Usulan: **tabel sendiri** `inspirasi_session_log` / JSON di `inspirasi_sessions.transcript` + `result` + `status` + `latency_ms` + `credits_charged` + `llm_cost_micro_usd`. `UsageMeter` tetap nulis `provider_usage` (Karung biaya). Review kualitas = baca transcript (pertanyaan, opsi, pilihan, hasil, sukses/gagal). Jangan log prompt system penuh / API key.

Retention: 180 hari, job mirip `SearchLog.purge`.

### Event app

`composeApp/.../data/analytics/AnalyticsEvent.kt` — nama snake_case, **tanpa** teks bebas / nama daerah (aturan analytics). Usulan:

| Event | Params |
| --- | --- |
| `inspirasi_start` | `interest_count`, `has_prompt` |
| `inspirasi_finish` | `steps`, `card_count`, `early` (Udah cukup) |
| `inspirasi_early_finish` | (kalau mau dipisah; PRD minta event tap "Udah cukup") |
| `inspirasi_card_open` | `surprise` bool |
| `inspirasi_create_trip` | — |
| `inspirasi_buddy` | — |

Jangan isi label minat di Firebase (PII-ish + >100 char). Screen view otomatis dari route.

---

## 9. Bikin trip dari sini

**Tidak** ada endpoint draft khusus. App panggil yang sudah ada:

`POST /api/v1/trips` (`ApiRoutes.TRIPS`) body `CreateTripRequest` (`TripDtos.kt`), id UUID client. Server `TripService.create`. Mobile: `TripRepository.create` (Room dulu, sync); web: `RanselApi.createTrip`. Setelah create: `AnalyticsEvent.TripCreated` (sudah ada).

Mapping dari kartu:

| `CreateTripRequest` | Sumber |
| --- | --- |
| `id` | `newId()` client |
| `title` | `"Trip ke {area}"` atau `"{type} · {area}"` (cap `validateTrip`) |
| `destination` | `card.area` |
| `startDate` / `endDate` | dari `durationDays`: dalam kota 1 hari atau **undated** (`null,null` sudah legal V26); luar kota/negeri `start = hari ini WIB` (atau Sabtu depan — TBD) + `end = start + days - 1` |
| `baseCurrency` | `MeDto.defaultCurrency` / `localeForPlace` (`Overseas.kt`) |
| `destinationPlace` | opsional: `GET /places/autocomplete` + `GET /places/{id}` (`GooglePlaceSearch`) — kalau miss, nama teks saja (trip interview sudah izinkan) |
| `countryCode`, `localCurrency`, `timeZone` | `localeForPlace` |
| `budgetMinor` | jangan isi dari `costRange` (itu perkiraan AI, bukan budget user) |
| origin / kendaraan | null (wawancara trip terpisah; user bisa isi nanti) |

Itinerary per hari **keluar v1** — trip kosong + destinasi + tanggal. Lanjut alur trip biasa (Ringkasan / interview travel mode).

"Simpan" kartu **bukan** bikin trip (PRD open Q3).

---

## 10. DB, config, flag, tes, risiko

### Migrasi

Berikutnya = **V11** (sekarang `V10__travel_modes.sql`). Jangan edit V1–V10.

Usulan V11:

- `inspirasi_sessions` (id UUID PK client, user_id, status, step_index, slots jsonb, transcript jsonb, result jsonb, charged_at, created_at, updated_at, expires_at)
- `inspirasi_interest_rules`
- `inspirasi_buddy_requests`
- `users.home_area_name`, `home_lat`, `home_lng`, `home_place_id`, `home_country_code` (kota kasar, **bukan** GPS raw — pola `TripOriginDto`)
- `ALTER credit_ledger_action_check` tambah `'INSPIRASI'`
- `ALTER app_settings_key_check` tambah `'COST_INSPIRASI'`, `'INSPIRASI_ENABLED'` (dan `'INSPIRASI_RULES_JSON'` kalau dipakai)

`ai_search_log` tidak wajib diubah di v1.

### Config

| Key | Env | Default usulan |
| --- | --- | --- |
| `COST_INSPIRASI` | `CREDIT_COST_INSPIRASI` | `0` sampai spike (PRD: tarif ditunda) |
| `INSPIRASI_ENABLED` | `EXPLORE_INSPIRASI_ENABLED` | `false` |
| rules JSON | — | seed SQL |

`SettingsService.current` / `validateSetting` / Karung `SettingsCard` ikut. App baca flag: field additive di `CreditsDto` (`inspirasiEnabled`) **atau** `GET /credits` saja + hide hero kalau cost/flag. `GET /content` sekarang kategori/error, **bukan** feature flag.

### Lokasi kasar

Sekarang: `GET /places/reverse` **sengaja tidak nyimpan** koordinat (`ApiRoutes` + TRD Places). Inspirasi **perlu** simpan kota di profil (PRD). Alur: izin kasar → reverse → pilih `NearbyAreaDto` level kota → `PATCH /me` field baru (additive). Tolak izin → slot DISTANCE nanya kota (freetext / autocomplete). Jangan simpan lat/lng GPS.

### Rollout

1. Flag server `false` di production.
2. Shared DTO additive dulu.
3. Spike kualitas (tarif 0).
4. Flag on staging, hero cuma kalau `inspirasiEnabled && emailVerified`.
5. Trip-first Explore **tetap** di picker / dari dalam trip sampai Boss jawab PRD Q2.

### Test plan ↔ acceptance PRD

| Acceptance PRD | Tes |
| --- | --- |
| User tanpa trip → 3–5 kartu jenis+daerah | `InspirasiRoutesTest` start→steps→finish; assert ukuran + `surprise`; Places fake |
| Fotografi+Belanja → combo berlabel; 1 minat → skip combo | unit filter opsi; fixture LLM |
| Chip ke-6 ditolak | `shared` domain + 422 API |
| Freediving pemula → level + buddy sebelum hasil | rules table + tolak finish |
| Dalam kota Jakarta → area Jakarta, tidak ada durasi nginep | assert CONTEXT options + `area` contains Jakarta |
| "street photography di Bangkok" skip minat+jarak | parser prompt + `skippedSlots` |
| Credit sekali / gagal 0 / refine 0 | ledger `idempotency_key`; finish 2×; LLM throw |
| Bikin trip daerah+durasi | mapping unit; opsional `TripRoutesTest` |
| Kabarin gw | row buddy_requests |
| Label perkiraan | DTO flags + copy (UI test frontend) |

Frontend: ViewModel + chip max 5; credit button; jangan hardcode tarif. Fallback foto: `photoUrl` null.

### Risiko

- LLM ngarang operator/resto → filter + fallback tree.
- Combo slot dilewatkan LLM → server skip/filter.
- Foto IDs Only nyasar tempat terkenal → acceptable; kartu tetap jenis+daerah.
- `charge` tanpa idempotency key (hari ini) → wajib diubah sebelum ship.
- CHECK SQL enum ketinggalan → insert ledger 500.
- Tab Explore dobel konsep (PRD Q2) → jangan rip picker sebelum keputusan.
- Biaya Places per kartu (find+photo) × 6 → meter + cache photo LRU yang sudah ada.
- Session store vs server restart: DB, bukan memory.

### Pertanyaan terbuka

Dari PRD:

1. Tarif + apakah daily free berlaku.
2. Inspirasi = home Explore vs shortcut trip-first.
3. "Simpan" ke mana.

Tambahan teknis:

4. Nama `CreditAction.INSPIRASI` vs `INTERVIEW`.
5. Default `durationDays` → tanggal trip (hari ini vs Sabtu vs undated).
6. `InspirasiCostPer` (orang / hari / trip).
7. Editor Karung untuk rules, atau SQL/JSON dulu.
8. Ikon yang belum ada di `Symbols` (`scubaDiving`, `spa`, …) — nambah font atau fallback.
9. Apakah `GET /me` pulang `homeArea` ke semua client (additive) atau cuma di start Inspirasi.
10. Apakah finish boleh dari langkah 3 meski rules skill belum (PRD: "Udah cukup" vs "wajib nanya") — usulan: **tidak**, rules menang.

---

## Urutan implementasi (bukan di PR ini)

Docs only sekarang. Nanti: (1) `shared` additive + `CONTRACT_CHANGES.md`, (2) V11 + `CreditService` + Gemini methods + routes, (3) app hero + interview + hasil, (4) Karung setting + monitoring log. Flag default off.
