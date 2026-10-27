# PRD — RANSEL

Oct 27, 2026 · @SEN

## Summary

RANSEL is a group trip planner: friends plan one trip together, from the itinerary and who brings what to splitting costs and settling up, and it keeps working without signal. AI place recommendations picked by swiping help the group decide where to go. Positioning agreed on 24 Oct 2026, based on the Riset Pasar & Strategi RANSEL doc. A trip can also be planned alone. It is built with Compose Multiplatform for Android, iOS, and Web, with a Ktor backend, and designed with the IRIS design system.

**Problem:** people plan a holiday across many tools at once: the schedule in notes, the packing list in another app, expenses in a spreadsheet, place ideas from random searches, and group decisions in a long chat thread. When travelling with friends, who paid for what and who brings what gets lost.

**Solution:** one trip as the hub for everything, usable offline, shareable with friends (also friends without an account), and helped by AI to find places that match the traveller's taste.

**Platforms:** Android, iOS (Compose Multiplatform), and Web (wide layout ≥ 1240 dp with a navigation rail). The UI is available in Indonesian and English, chosen in Account → Bahasa on every platform (default: the system or browser language). Android and iOS work offline and without an account; the web app is online-only and requires signing in, since it is loaded from the server anyway. The guest page for poll and invite links is the exception: it needs no account. The web app can be installed to the home screen or desktop (PWA); installed or not, it still needs a connection for data.

## Goals and metrics

Main goal: a user can take one trip from zero to ready-to-go without switching apps, alone or with a group.

| Goal | Metric | Initial target |
| --- | --- | --- |
| Trips are actually planned | % of trips with ≥ 1 activity on ≥ 50% of their days | set after beta |
| Packing is used until departure | % of upcoming trips with packing progress ≥ 80% the day before | set after beta |
| Expenses are recorded | average expense entries per ongoing trip | set after beta |
| Explore AI is useful | places saved per swipe session; % of saved places that end up scheduled | set after beta |
| Collaboration | % of trips with ≥ 2 members; % of polls decided; % of balances settled | set after beta |

Target numbers have not been discussed yet, so the target column is left open on purpose.

**North star metric:** group trips active until the end, meaning trips with at least 2 members that record an expense after the start date.

**Non-goals:**

- Booking tickets or hotels, or payments inside the app.
- Turn-by-turn map navigation.
- Money transfers between members; the app only records who owes whom, shows the receiver's payment info, and whether it is settled.
- A shared money pot (kas, kitty): built in stage 4, removed on 27 Oct 2026 by the owner; groups settle through Saldo.
- A group chat; members comment on poll options instead.
- Social features or a public feed.

## Personas and scenarios

| Persona | Needs | Main features |
| --- | --- | --- |
| Solo planner (e.g. Septian, 4 days in Bali) | Daily schedule, packing list, budget control | Trip, Itinerary, Packing, Biaya |
| Inspiration seeker | Does not know yet where to go at the destination | Explore AI (swipe), Unscheduled |
| Group of friends (4 people) | Decide together, pack together, split costs, know who owes whom | Ringkasan, polls, place voting, Pribadi and Bersama packing, Saldo, documents |
| Friend without an account | Vote and see their packing list from a WhatsApp link | Guest page on the web |
| Traveller without signal | Can still view and change the plan | Offline-first, automatic sync |

Main scenarios:

1. The user creates the trip "Bali 4 hari" (20–23 Nov 2026, budget Rp5,000,000), adds activities per day, then reorders them.
2. The user looks for ideas with Explore AI, saves a few places, and schedules them on a specific day; on a group trip the search becomes a voting round.
3. The group decides the dates and the stay with polls shared to WhatsApp; friends without an account vote on the guest page.
4. The owner sends a members' packing list; everyone ticks their own bag, and the group shares who brings the stove.
5. During the trip the group records expenses (also from a receipt photo); at the end Saldo shows the fewest transfers and the receiver's bank details.
6. The user uses the app without signal; changes are sent when back online.
7. After the trip the group gets a recap card to share and can start the next trip from it.

## Scope per phase

Five product phases in order; Phase 1 is already useful for a real trip without any server. Phase 6 (the admin app) and the group trip stages came after.

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 — Offline app (solo) | Trips, itinerary, packing, expenses on Android and iOS; local Room database; no server | One real trip can be planned start to finish on a phone |
| 2 — Basic backend | Ktor + PostgreSQL CRUD for the same data; web app joins, online-only | Every endpoint works and data is stored in Postgres |
| 3 — Auth and sync | Sign up / sign in, forgot password, email verification, offline-first sync between phone and web | A trip created offline on a phone shows up on the web once online |
| 4 — Multi-member | Invites, roles, paid by, split bill, balances, settlements, packing templates | Two accounts manage one trip and the split result is correct |
| 5 — Explore AI | Preference form, swipe cards, Unscheduled, Explore credits, place voting on group trips | Recommendations are real places and can be scheduled into the itinerary |
| 6 — Admin (Karung) | Internal admin web app: users, credit adjustments, monitoring, settings, remote content | An admin can find a user, adjust their credits and see it in their credit history |
| Group trips (stages 1–9) | Members by name, comments, polls, group money, destinations, overseas trips, group packing, sharing and the guest page, documents, recap, trip templates | A group of four, one without an account, plans, packs and settles one trip in the app |

The UI is designed in the "RANSEL — IRIS" canvas and the admin app in the "Karung" canvas; what remains is listed under Open questions.

**Change on 24 Oct 2026:** the core of Phase 4 (members, split bill, balances) and the P0 group features move into the first beta together with Phases 1–3. **Beta scope (25 Oct 2026):** beta ships after every group feature in Group trips is done, rather than with a reduced core loop.

## Functional requirements

### Home and trips

- Home shows the nearest trip (cover, countdown), a **Persiapan trip** card (itinerary days filled, packing items ready, number of unscheduled places), and a prompt to Explore AI. Tapping the nearest trip opens **Ringkasan** for a group trip and **Itinerary** for a solo trip. For two weeks after a trip ends, a "Rekap siap" card opens its recap. There is no remaining-budget card and no shortcut tiles.
- My trips groups trips into Ongoing, Upcoming, and Finished. The Create trip button is the FAB.
- Create/edit trip: name, destination (destination picker), date range (DateRangePicker; no day before today, except that a trip under way keeps its saved start), budget, currency, cover, and on Create an optional **trip template**. Deleting a trip goes through a dialog that lists how many activities, items, and expense entries will be deleted with it.
- A trip has four tabs: **Ringkasan**, **Itinerary**, **Packing**, **Biaya**. Tab labels always stay on one line (they shrink to at least 12 sp before an ellipsis). The header shows back, the title and dates (plus the difference with WIB abroad or in WITA/WIT), **Rekap trip** after the end date, and **Ubah trip** for the owner. Members open from Ringkasan, not from the header (27 Oct 2026).
- Empty states: Home without trips, My trips empty, and My trips without upcoming trips.

### Ringkasan (the trip hub)

The first tab of every trip, so new group features do not pile up in Itinerary (agreed 24 Oct 2026 as "Grup"; renamed Ringkasan on 27 Oct 2026, because solo trips have it too). A big green **HeroCard** shows the countdown ("12 hari lagi", "Hari ke-2 dari 4", "Trip selesai") with the trip name and dates.

- **Solo trip** (only the user, as the server says): the countdown, **Dokumen trip**, and for the owner an **Ajak teman** card that opens the invite sheet. No voting, money or members.
- **Group trip**, top to bottom:
    - **Perlu tindakanmu**: one card per place-voting round waiting for the user (a tap opens its swipe cards, or the owner's result once voting has closed), polls to vote on or to confirm, payments to confirm, and transfers the user still owes.
    - **Voting berjalan**: polls and voting rounds still open that only wait for others.
    - **Riwayat voting** `n ›`: one row that opens the finished polls and decided rounds.
    - **Dokumen trip** `n ›`.
    - **Anggota** with the count; tapping opens the members screen.
- The + button (owner and editors) opens a sheet: Buat polling (group trips only), Ajak anggota (owner), Unggah dokumen.
- A signed-out user sees the countdown and an invitation to sign in.
- Section titles on every screen use one style (`SectionHeader`, IRIS headline-small).

### Itinerary

- Activities per day with start–end time, category, location, and notes.
- Categories (ID / EN): Wisata / Explore (blue), Kuliner / Eat (orange), Perjalanan / Travel (teal), Menginap / Stay (purple), Belanja / Shop (yellow), Lainnya / Other (brown).
- Activities are reordered with a **long press** anywhere on the row, then drag; there is no drag handle, so scrolling past a row never moves it (27 Oct 2026). Times are recalculated automatically (see Business rules).
- An **Unscheduled** box above the day list holds places from Explore AI or added manually, each with a Schedule button, plus a full list (filter All / From AI / Manual).
- Schedule a place: pick a day; the time is filled in at the end of that day and can be changed with the TimePicker.
- Voting lives only on Ringkasan; Itinerary, Packing and Biaya show no voting banner (27 Oct 2026).

### Packing

- One component for every list (solo, Pribadi, Bersama): items by category with an icon, a "3/5" count and "Lengkap", collapsible; each row has a checkbox, name, quantity and a ⋮ button with **Ubah** and **Hapus** (Hapus asks first). A long press also opens Ubah.
- One progress card for the solo list and Pribadi: "Siap dibawa · x dari y barang", a bar, the departure countdown, "Wajib x/y" when some lines are required, and the ⋮ menu: **Pakai template**, **Simpan sebagai template**, **Hapus semua centang**. An empty list offers Pakai template.
- Save as template: name, option to keep quantities, option to share with trip members; checked state is not saved.
- Group trips: see Group packing and the members' list.

### Biaya (expenses)

- Record an expense: amount, currency, category (the same six as the itinerary and in the same colors, with noun labels that fit money: Makan / Food, Transport, Penginapan / Lodging, Aktivitas / Activities, Belanja / Shopping, Lainnya / Other), note, date (may fall outside the trip dates), and payment method. The + button asks **Foto struk** or **Catat manual**. The expense form and "Cek hasil struk" open as their own screen with Save pinned at the bottom (a dialog on wide web), not a bottom sheet that closes while scrolling; leaving with unsaved input asks "Buang perubahan?" (owner, 27 Oct 2026).
- Ringkasan view: a green **HeroCard** with the remaining budget (or the total spent without a budget; it stays green over budget and reads "Melebihi budget"), unpaid estimates, a donut by category, transactions by day.
- Shared trips add Paid by (one or several payers), Split equally / Set amounts, and the green **Ringkasan | Saldo** tabs (the same `SegmentedTabs` as Packing's Pribadi | Bersama). There is no Kas view (removed 27 Oct 2026).
- See Getting paid and Receipt photo under Group trips.

### Explore AI

- The Explore tab holds the preference form for a trip: destination (editable per search; replaces the earlier focus area), price level relative to local prices (Hemat / Sedang / Bebas), core interests plus destination interests suggested by the AI, travel style (Santai / Padat), and notes; a new trip asks destination, dates and a name first ("Buat trip dan cari"). Full rules: `docs/specs/AI-Recommendation-Spec.md`.
- Results are swipe cards: swipe right to save, left to skip, with Undo. Saved places go to Unscheduled in the related trip. On a trip with two or more members a search becomes a **voting round** (`docs/specs/voting-spec.md`): every member swipes within 24 hours and the owner decides which places enter the itinerary.
- An unfinished session can be resumed from a "Continue" card.
- States: loading, all cards seen, error, no results, out of credits, sign-in required (guest), and verify email first.

### Account and sync

- Three onboarding screens, Sign up, Sign in, and "Use without an account for now".
- Forgot password: email → check your email → new password.
- Email verification after sign-up (22 Oct 2026): the new account can use the app right away, but Explore and the signup credits wait until the link in the email (valid 24 hours) is opened. The app shows "Satu langkah lagi" after sign-up and a verify gate in the Explore tab (resend link, "Sudah verifikasi").
- Account: profile, sync status, default currency, theme (system/light/dark), language (on the web too: the page reloads in the new language), payment info (bank accounts and e-wallets), credits, packing and activity reminders, sign out.
- Session expired: ask to sign in again, or continue offline.

### Notifications

- Signed-in users get an in-app inbox (the bell on Home) and push notifications (Android first; iOS comes with the Xcode project). Guests have neither.
- Events: a friend joins your trip (also through a guest link), you are removed or your role changes, someone claims a name, a shared expense that includes you is recorded, a payment to or from you is marked or claimed, a voting round or poll asks for your vote or has a result, a mention in a comment, the owner's packing list arrives or changes, a document is added, and the recap is ready.
- Reminders from the server: the evening before departure (18:00 in the trip's zone) if packing items are not ticked yet, 30 minutes before a scheduled activity, a day before a poll closes, and the owner's "Ingatkan" for the packing list (once per member per 12 hours).
- Three switches in Account: trip activity, expenses and settlements, reminders. A switch that is off means no inbox entry and no push for that kind. The person who caused an event is never notified about it.
- The inbox stores what happened, not the sentence, so it is shown in the app language; the push text follows the language the phone reported.

### Members and cost splitting (Phase 4)

- The owner invites by code or link with the Editor or Viewer role; invites can be revoked.
- The invitee sees a Join trip screen, or an invalid-invite screen.
- The owner can change roles and remove members; members can leave a trip. Members open from Ringkasan → Anggota.

### Common states

- Connection error, server error, coming soon, and empty use one pattern with a capybara illustration and the error code from the API.
- Back (Android back, the browser's back button) closes an open sheet first and keeps the screen.

### Admin (Karung, Phase 6)

Full rules: `docs/specs/admin-spec.md`.

- A web app for admins only, at `karung.mietek.tech`. Admins are the accounts listed in the server's `ADMIN_EMAILS`; they sign in with their normal RANSEL account.
- Users: search and filter users, see a profile with trip metadata (never trip content), credit history (receipt photos labelled "Foto struk"), devices and recent activity.
- Adjust credits: add or subtract wallet credits for one user with a required reason; the balance never goes below 0 and the free daily allowance is not touched. The user sees the row as "Penyesuaian admin" in their credit history.
- Other actions: sign a user out of every device, deactivate or reactivate an account, send a password reset email (once an email provider exists). Every admin action is written to an audit log.
- Monitoring: active users, new trips, Explore searches, credits used, AI cost against the monthly budget, errors per code, server health.
- Settings: credit numbers (signup grant, daily free credits, cost per action) and AI budget settings can be changed without a deploy; the server config values stay the defaults.
- Remote content: app strings, error messages, category looks and destination interests are edited and published in Karung; the apps start reading them in a later step.

## Group trips

These close the gaps found when RANSEL is used by a group where not everyone has an account or the app (agreed 24 Oct 2026). All screens are in the RANSEL — IRIS canvas, rows "Trip grup · P0", "P1" and "P2", plus web versions.

| Priority | Feature | Screens | Status |
| --- | --- | --- | --- |
| P0 | Members without an account | AnggotaTanpaAkun, TambahAnggotaNama, GabungPilihNama | Built |
| P0 | Join from the invite link without signing in | WebGabungPratinjau | Built (guest page) |
| P0 | Group-first onboarding and an invite step after creating a trip | OnboardingGrup1–3, AjakTeman | Not built yet (Ringkasan has "Ajak teman" on solo trips) |
| P0 | Payment info, reminders and "I've paid" | SaldoTagih, IngatkanWA, BayarKe, InfoPembayaran | Built |
| P1 | Date poll | BuatPolling, PollingTanggal | Built; a trip without dates ("Tanggal belum pasti", BuatTripTanggalBelumPasti) is not built yet |
| P1 | Shared packing items (who brings what) | PackingBersama, TambahBarangBersama | Built |
| P1 | Group kitty (iuran) | KasBersama, CatatSetoran | Removed 27 Oct 2026 |
| P2 | Receipt photo with split per item | FotoStruk, HasilStruk | Built |
| P2 | Trip documents, available offline | DokumenTrip | Built |
| P2 | Shareable trip recap | RekapTrip | Built |

### P0 · Members without an account

- The owner can add a member by name, with an optional WhatsApp number. The member gets a "Tanpa akun" tag, is always a Viewer, and can be picked in Paid by and Split with, but cannot edit the trip.
- When someone joins through the invite link and the trip has members without an account, the app asks "Kamu yang mana?". Picking a name moves that member's expenses, splits, settlements and packing assignments to the new account. The owner is notified and can undo a wrong claim.
- "Kirim link" on a member without an account opens WhatsApp with the invite link.

### P0 · Join without signing in (the guest page)

- `ransel.mietek.tech/j/<code>` (invite) and `ransel.mietek.tech/p/<code>` (poll) open a web page on the phone browser that needs no account: trip name, dates, members, open polls, and "Gabung dengan akun" first for an invite. Exact amounts per person, payment info and the itinerary details stay hidden.
- The visitor picks their name from the members without an account (taken ones greyed) or writes one ("Nama saya tidak ada" adds a member without an account; "Itu kamu?" when the name exists). They can then vote and tick the packing lines they received. One browser per name; the owner can release a name ("Lepas dari browser"). Abuse protection stays simple: an IP rate limit, no OTP or captcha (owner, 26 Oct 2026).
- Not built: a guest's own share of the expenses on the page; "Pasang aplikasi" is "Buat akun untuk ikut merencanakan" until there are store links.

### P0 · Group-first onboarding (not built yet)

- Onboarding tells the group story: plan together, split costs, who brings what (OnboardingGrup1–3 replace Onboarding1–3).
- After Buat trip, the AjakTeman screen offers Share to WhatsApp, copy link, or add friends by name. "Nanti saja" goes to the itinerary.

### P0 · Getting paid

- Account → Info pembayaran stores bank accounts and e-wallets (name, number, account holder). They are shown only to members of a trip who owe that person money, never on the guest page.
- On the Saldo tab, each open transfer to you has a 🔔 bell ("Ingatkan"), which opens WhatsApp with a prefilled message (amount, trip, payment info if switched on). RANSEL does not send messages itself.
- The person who owes sees "Bayar ke <name>" with the amount, payment info and Copy buttons, then taps "Saya sudah bayar". The receiver confirms, which records the settlement. The receiver can still mark a transfer as settled directly.
- My position card is red when I owe, green when money comes back, neutral when even; amounts always show whole on one line.
- RANSEL never moves money; this stays within the non-goals.

### P1 · Date poll

- The owner or an editor creates a date poll with 2–5 date ranges (none before today) and a closing time; members tick every range they can make, multiple choice by default.
- Members without an account vote from the guest link. The creator sees who has not voted and can remind them through WhatsApp.
- The creator or the owner confirms a range, which sets the trip dates (activities on removed days become unscheduled).
- Not built yet: creating a trip with "Tanggal belum pasti" (no days until the poll is decided); a trip always has dates today.

### P1 · Shared packing items

- Packing has two green tabs: Pribadi (each member's own list) and Bersama (one list for the group).
- A Bersama item has a quantity and an optional person who brings it. An unassigned item shows a "Saya bawa" button (no extra "Belum ada yang bawa" text since 27 Oct 2026). Only the person who brings an item ticks it as packed; the bringer or an editor gives it back.
- Its summary card ("x dari y sudah ada yang bawa") has a ⋮ menu for the owner and editors with Pakai template and Simpan sebagai template; no "untick all", since the ticks belong to the bringers.

### P1 · Group kitty (removed)

Built in stage 4 (a third Biaya view "Kas", deposits, "Ambil dari kas"), then removed on 27 Oct 2026: the owner found it more confusing than useful next to Saldo. It never reached production.

### P2 · Receipt photo

- Foto struk reads the receipt with AI (place, date, items, total, tax; costs 1 credit, "Foto struk" in the credit history; nothing when unreadable) and opens "Cek hasil struk"; the user checks it before saving.
- The date read stays, also outside the trip (a ticket can be bought earlier), but is editable; the year shows when it is not the trip's year.
- "Cek hasil struk" has the same "Dibayar oleh" and "Dibagi ke" as the manual form (several payers, who takes part, Bagi rata or Atur nominal; only the payer ticked = a personal expense), plus "Per item" when the receipt has lines; Bagi rata is the default (owner, 27 Oct 2026).
- "Per item" split lets each line go to different members. Tax and service added on top are spread in proportion to what each person had, said under the tax line, and "Bagian tiap orang" lists every share with its part of the tax. Tax already inside the prices ("Incl. Pb1 10%": the items alone make the total) is only a note. Save is enabled only when the items add up to the total.

### P2 · Trip documents

- Members attach PDFs and images (tickets, booking confirmations, vouchers; 20 MB per file, 200 MB per trip) or links to the trip, by group (Perjalanan, Menginap, Aktivitas, Lainnya), optionally tied to an activity chosen on a full-screen **Pilih aktivitas** (search, by day, unscheduled last, "Tanpa aktivitas"). Files are kept on the device once opened, for offline use. Viewers open them, editors add them, the uploader or the owner deletes them.

### P2 · Trip recap

- After the end date, the trip header has "Rekap trip", Home shows a "Rekap siap" card for two weeks, and a notification arrives the next morning: days, activities, places, total spend, and "Semua patungan lunas" when all balances are settled. It can be shared as an image or saved as a trip template, and "Buat trip dengan grup ini" starts the next trip through the invite sheet.

### Polls (agreed 24–25 Oct 2026)

**Polling with templates.** WhatsApp already has polls, so a RANSEL poll is only worth it when the winner goes straight into the plan. Each template defines what happens to the winner.

| Template | Priority | What happens to the winner |
| --- | --- | --- |
| Tanggal | P1 | Becomes the trip dates |
| Tempat dan aktivitas | P1 | Goes to Belum dijadwalkan (an AI search is the swipe voting round) |
| Penginapan | P1 | Price × nights becomes an expense estimate; the booking link is saved to Dokumen |
| Tempat makan | P1 | Becomes an activity on the poll's day and time |
| Budget per orang | P2 | Anonymous; recorded on the poll |
| Destinasi | P2 | Becomes the trip destination |
| Transportasi | P2 | Noted on the trip |
| Pertanyaan bebas | P2 | No automatic action |

Rules:

- One choice by default; the creator can allow several.
- Polls close after a set time: **1, 2 or 3 days** (7 days removed on 27 Oct 2026), and members are reminded a day before. A poll does not close by itself when everyone has voted; the creator or the owner can close it early with "Tutup voting sekarang", which opens the confirm dialog at once when there is one leader.
- Only the creator or the trip owner confirms the winner, picks among tied options, closes the poll without confirming, reopens or deletes it.
- Members without an account vote through the WhatsApp link.
- In an anonymous poll nobody, the creator included, can see who chose what. Only totals are shown.
- Finished polls (confirmed) appear only under Riwayat voting on Ringkasan.

**Adding options (agreed 25 Oct 2026).** Poll options can be added in three ways, depending on the template:

- **Cari di Jelajah.** Offered first when a stay or food poll has no options yet. Jelajah opens with the category fixed by the template, so there are no interest chips. Dates, nights, headcount and category come from the trip. The user only fills in a location and a specific budget range (per night for a stay, per person for food), plus optional needs. They tick results, and the ticked results become options.
- **Paste a link** (optional). The server fetches the page and reads Open Graph and schema.org metadata for the name and photo. Price is not read, so the user types it.
- **Fill in manually:** name and price.

Templates: Tanggal uses the date picker. Penginapan and Tempat makan use all three ways. Tempat makan defaults to the area of that day's itinerary and suits quick polls during the trip.

Prices from Jelajah are labelled as estimates. Google Places only gives a price level, not nightly rates, so exact stay prices would need a hotel or affiliate partner API. That is an open question and a possible revenue source.

**Comments instead of chat.** RANSEL does not build a group chat, because groups already chat on WhatsApp. Members comment on a poll option (owner decision 25 Oct 2026: poll options only, not itinerary places or expenses). @mention sends a notification. Each option shows a comment count.

**Anonymous polls (agreed 25 Oct 2026).** Every poll form has an "Anonim" toggle (off by default; always on and locked for budget polls). When it is on, nobody can see who chose which option, including the poll creator and the trip owner. Everyone still sees vote counts and who has already voted, so reminders keep working. The toggle can be changed until the first vote, then it is locked so a poll can never be turned from anonymous to public. Comments on an anonymous poll are not anonymous.

**Poll edge cases (agreed 25 Oct 2026).**

| Case | Rule |
| --- | --- |
| Tie | The creator picks the winner among the tied options and is notified. If they have not picked within 24 hours, the poll reopens for 1 day. The creator can also reopen it by hand. |
| Creator leaves the trip | Their active polls pass to the trip owner. Votes already cast still count. The leave dialog says so. |
| Option added after votes | Allowed. Existing votes stay. |
| Voted option edited (price, name) or deleted | Votes on that option are reset, and those voters are notified to vote again. Deleting an option that has votes needs a confirmation dialog. |
| Voting offline | The vote is queued on the device and sent when back online. Counts shown are the last known counts. |
| Queued vote arrives after the poll closed | The server rejects it. The app explains why and shows the winner. |
| Double voting by guests | One vote per trip member. Names that belong to an account are locked in the browser and need sign-in. If a guest name votes again from another device, the new vote replaces the old one. |
| Members who never vote | Not counted. They get a reminder a day before the poll closes. |

**WhatsApp link previews.** Every shared link (trip invite, poll) serves Open Graph tags. Agreed 25 Oct 2026 as a 1200×630 image rendered per link (question, up to 3 options with price, vote count, closing time); changed on 26 Oct 2026 by the owner to **one static image per link type** with the text per poll or trip. After a poll is created, a share sheet offers "Bagikan ke WhatsApp" with a prefilled message sent from the user's own WhatsApp.

### Place voting

An Explore search on a group trip becomes a round (`docs/specs/voting-spec.md`): every member swipes within 24 hours, the owner decides. Rounds appear as cards in "Perlu tindakanmu" (one per round), rows in "Voting berjalan" and "Riwayat voting", drawn like poll rows (magenta tile with the AI mark, status tag). The owner can close voting early.

### Entry points for group trip screens

| Screen | How the user gets there |
| --- | --- |
| Foto struk | The + button on Biaya opens a chooser with two options: Foto struk and Catat manual. |
| Dokumen trip | "Dokumen trip" on Ringkasan, "Unggah dokumen" in its + sheet, and a notification when a member uploads a document. |
| Rekap trip | The "Rekap trip" button in the trip header (once the trip has ended), the Home "Rekap siap" card, and a "rekap siap" notification. |
| A poll, member with an account | A notification ("Septian minta kamu pilih tanggal") and "Perlu tindakanmu" on Ringkasan. Voting is not shown on Itinerary. |
| A poll, member without an account | The WhatsApp link opens the poll in the browser; they pick their name and vote without signing up. |
| A poll, owner or editor | The + button on Ringkasan (Buat polling); every poll is listed on Ringkasan. |
| Progres anggota (owner) | The one-line summary of the members' list on the owner's Pribadi tab. |

### Destination suggestions and current location

When someone fills in a destination (Buat trip, Jelajah, a destination poll option), they choose from suggestions instead of typing free text, so every trip gets a real place with a map point.

- **Before typing:** "Gunakan lokasi saat ini", recently used destinations (last 10, per device), and destinations popular for group trips.
- **While typing:** suggestions start at 2 characters and show the city or area, its province, and a type label (kota, kabupaten, tempat wisata). The matching part is bold. Indonesian places rank first, but overseas cities and countries also appear (for example Tokyo, Jepang). Airports are not suggested (owner, 26 Oct 2026). For an overseas trip the country sets the local currency and time zone.
- **Current location (GPS):** permission is only requested when the person taps "Gunakan lokasi saat ini". We turn the location into an area name (for example Jakarta Selatan) and offer a few levels to choose from. Coordinates are used once and are not stored.
- **Permission denied:** explain that location is off, offer a button to open settings, and keep search and recents usable.
- **In Jelajah during a trip:** the "Dekat" field suggests the current location (when the person is near the destination), the last itinerary activity of the day, or another place.
- **Offline, signed out, or the search service is down:** fall back to recents and a built-in list of Indonesian provinces, cities and regencies.
- A Google attribution appears below the suggestions.

### Overseas trips

- A destination can be a country or a city outside Indonesia. Indonesian places rank first in suggestions.
- **Main currency** stays IDR by default, because members settle up with each other in rupiah.
- **Local currency** is set automatically from the destination country (for example JPY for Japan; "Otomatis dari destinasi", editable). New expenses default to it, and the app shows the rupiah amount using that day's reference rate. Members can enter their own rate (for example from the money changer they used).
- The original foreign amount is stored, so totals can be recalculated if a rate is corrected.
- **Time zone** follows the destination. Itinerary times are shown in local time, and the header shows the difference from WIB.
- Jelajah searches around the destination. Results abroad may be thinner, and the empty state suggests pasting a link instead.

### Group packing and the members' list

The trip owner builds a packing list once and sends it to every member. This helps members who have never done this kind of trip, such as climbing a mountain, know what to bring. RANSEL does not supply the content; the owner writes it. Only the owner writes, sends and follows it (27 Oct 2026; editors could before).

- **Start:** on the owner's Pribadi tab, "Daftar bawaan anggota". The owner can copy their own Pribadi list, use one of their saved templates, or start from empty.
- **Editor:** a list name, categories, items with quantity (− and + steppers), and a **Wajib** mark for must-haves.
- **Send:** choose members (all are selected by default; a member without an account sees it on the guest page). "Simpan sebagai template saya" saves it for future trips.
- **Everyone gets a copy, the owner too** (27 Oct 2026): the lines land in each Pribadi list and look and behave like the member's own (tick, edit, delete); only the "Wajib" label shows. A line someone already keeps (same category and name) is linked, not added twice. Members can add their own items, which only they see.
- **Owner view:** on Pribadi, one line ("2 dari 5 anggota siap" with who is not ready, or "Draf daftar anggota · 12 barang · belum dikirim") opens **Progres anggota**: the list with Ubah and Kirim, every member (not ready first; ready = all required lines ticked, or all lines when none is required) with "Wajib x/y · Semua a/b" and a 🔔 per member, and "Ingatkan yang belum siap".
- **Later edits** by the owner reach members: new items are added everywhere; changed or removed items change only in the copies a member has not edited; a copy a member deleted never comes back. Check marks and own items are kept.
- Members joining later receive the list automatically. Check marks are never part of a template.

## Business rules

**Itinerary drag and drop.** Each activity keeps its duration; the affected range is packed together starting from the earliest start time in that range. Gaps are not stored or preserved. Example: 10:00–11:25, 11:25–12:00, 12:00–14:00; moving item 1 to position 3 gives 10:00–10:35, 10:35–12:35, 12:35–14:00. A confirmation dialog appears before saving (it can be turned off with "Don't ask again"), followed by an Undo snackbar.

**Schedule a place.** The place goes to the end of the chosen day, starting when the last activity ends; an empty day starts at 09:00. The duration comes from the AI estimate. The user can change the time.

**Trip dates.** A new trip, a new trip from Explore, and date poll options cannot start before today. Editing a trip under way may keep its saved start.

**Explore session.** The Explore tab opens the form for a trip; "Cari inspirasi dengan AI" inside a trip preselects that trip, and a user with no ongoing or upcoming trip they can edit goes straight to the new-trip form. An unfinished swipe session shows as a Continue card and a dot on the tab icon. A session ends when all cards have been seen or the user starts a new search. Searches cost credits (they replaced the fixed 10 searches a day): a new user gets 30 credits once (after verifying their email), plus 3 free credits a day that refill at 00:00 WIB and do not accumulate; a new search costs 3 credits, the next batch of the same search 1, and a receipt photo 1 (numbers are server config; an admin can change them in Karung). The free credits are used first. When credits run out the app offers to add credits with a voucher code; buying credits comes with a payment provider later. Explore requires an account: a guest who opens the Explore tab sees a sign-in screen (Masuk / Daftar) instead of the form, and signing in moves the trips already on the device to the account. Every other feature still works without an account. Recommendations are always saved into a trip (its Unscheduled box). A new trip from Explore is created when the user taps "Buat trip dan cari", in their default currency and without a budget, and it stays even if no place is saved.

**Split bill.** By default the amount is split equally between the ticked members. With Set amounts, the shares must add up to the amount; the difference is shown as "short" or "over" and Save stays disabled until it matches. A bill paid by several people lists each payer's amount. Per item from a receipt: see Receipt photo.

**Balances.** Each person's balance = total paid − total share + confirmed payments made − confirmed payments received. Estimates do not count. "Supaya lunas" shows the fewest transfers. Tandai lunas records the payment at once; "Saya sudah bayar" waits for the receiver's confirmation.

**Packing.** In Bersama only the bringer ticks an item. In Pribadi every line is the member's, also those from the owner's list. Only the owner writes, sends and follows the members' list.

**Roles.**

| Action | Owner | Editor | Viewer |
| --- | --- | --- | --- |
| View the trip | yes | yes | yes |
| Change itinerary, Bersama packing, expenses | yes | yes | no |
| Own Pribadi packing list | yes | yes | yes |
| Create polls, upload documents | yes | yes | no |
| Vote, take part in cost splitting | yes | yes | yes |
| Members' packing list (write, send, progress, remind) | yes | no | no |
| Invite, change roles, remove members, add people by name | yes | no | no |
| Delete the trip | yes | no | no |
| Leave the trip | after transferring ownership | yes | yes |

**Invites.** A code `NAME-XXXX` and a link `ransel.mietek.tech/j/<code>`, with a usage limit and an expiry date. An invite fails when it has expired, was revoked, or is full. Removing a member does not delete the expense records that involve them.

**Currency.** The default currency only applies to new trips; existing trips keep their own currency. An overseas trip's expenses default to its local currency.

## Non-functional and UX requirements

- **Offline-first.** On Android and iOS, all core features (trips, itinerary, packing including Pribadi, expenses) work without a connection and without an account. Offline changes are marked "waiting to sync" and sent automatically when online, with a snackbar "Back online · n changes sent". Explore AI, members, polls, Saldo, the members' list and documents not yet downloaded need a connection.
- **Language.** Indonesian and English via Compose Resources (`values/` and `values-in/`); category labels have official pairs (Wisata / Explore, Kuliner / Eat, and so on). The language can be chosen in Account on every platform.
- **Theme.** Light, dark, and follow system, using the agreed IRIS dark color mapping.
- **Design system.** Every screen uses IRIS tokens and components; shared app components: HeroCard (big green top card), SectionHeader (section titles), SegmentedTabs (green Pribadi | Bersama, Ringkasan | Saldo), StatusTag. The capybara illustrations come from the "Ilustrasi" asset group in IRIS (transparent PNG, shipped in the app as 2× WebP).
- **Built-in components.** Date picker, date range picker, and time picker use the built-in Material 3 components that run on Compose Multiplatform.
- **Accessibility.** Text contrast at least 4.5:1, touch targets at least 48 dp, decorative illustrations without alt text, labels on icon buttons (e.g. the 🔔 Ingatkan bell).
- **Responsive.** A 390 dp phone is the reference; web ≥ 1240 dp uses a navigation rail and a side panel.
- **Account security.** Password reset links are valid for 30 minutes; after a password change, sessions on other devices end.

## Open questions

- [ ] Target numbers for the success metrics.
- [ ] Ownership transfer flow before the owner leaves a trip.
- [ ] Screens not designed yet: enter invite code, activity detail/edit, edit profile, search trips, all transactions.
- [ ] Dark theme for Itinerary, Packing, Explore, sheets, and dialogs (the pattern exists, the screens do not).
- [ ] Email provider for password reset.

Added on 24 Oct 2026 with the group trip features:

- [x] Web (1280) versions of the group trip screens.
- [x] Receipt reading: Gemini, about Rp50 of tokens per photo, 1 credit per photo.
- [x] Storage limit per trip for documents (200 MB, 20 MB per file), kept while the trip exists.
- [x] Kitty in a trip with several currencies: moot, the kitty was removed.
- [ ] Whether a member without an account can be removed after expenses were recorded for them.
- [ ] Group-first onboarding, the invite step after Buat trip, and trips without dates (P0/P1 not built yet).
