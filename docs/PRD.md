# PRD — RANSEL

Oct 27, 2026 · @SEN

## Summary

RANSEL is a trip planner that keeps the itinerary, the packing list, and expenses for one trip in one place, with AI place recommendations picked by swiping. Trips can be planned alone or with a group: the group votes on dates, stays and places, packs together, splits costs and keeps its documents in the trip. It is built with Compose Multiplatform for Android, iOS, and Web, with a Ktor backend, and designed with the IRIS design system.

**Problem:** people plan a holiday across many tools at once: the schedule in notes, the packing list in another app, expenses in a spreadsheet, place ideas from random searches, and group decisions in a long chat thread. When travelling with friends, who paid for what and who brings what gets lost.

**Solution:** one trip as the hub for everything, usable offline, shareable with friends (also with friends who have no account), and helped by AI to find places that match the traveller's taste.

**Platforms:** Android, iOS (Compose Multiplatform), and Web (wide layout ≥ 1240 dp with a navigation rail). The UI is available in Indonesian and English, on every platform chosen in Account → Bahasa (default: follow the system or browser). Android and iOS work offline and without an account; the web app is online-only and requires signing in, since it is loaded from the server anyway (the guest page for poll and invite links is the exception: it needs no account). The web app can be installed to the home screen or desktop (PWA); installed or not, it still needs a connection for data.

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

**Non-goals:**

- Booking tickets or hotels, or payments inside the app.
- Turn-by-turn map navigation.
- Money transfers between members; the app only records who owes whom, shows the receiver's payment info, and whether it is settled.
- A shared money pot ("kas"/kitty): removed on 27 Oct 2026 (owner); groups settle through Saldo.
- Social features or a public feed.

## Personas and scenarios

| Persona | Needs | Main features |
| --- | --- | --- |
| Solo planner (e.g. Septian, 4 days in Bali) | Daily schedule, packing list, budget control | Trip, Itinerary, Packing, Biaya |
| Inspiration seeker | Does not know yet where to go at the destination | Explore AI (swipe), Unscheduled |
| Group of friends (4 people) | Decide together, pack together, split costs, know who owes whom | Ringkasan, polls, place voting, Pribadi/Bersama packing, Saldo, documents |
| Friend without an account | Vote and see their packing list from a WhatsApp link | Guest page on the web |
| Traveller without signal | Can still view and change the plan | Offline-first, automatic sync |

Main scenarios:

1. The user creates the trip "Bali 4 hari" (20–23 Nov 2026, budget Rp5,000,000), adds activities per day, then reorders them.
2. The user looks for ideas with Explore AI, saves a few places, and schedules them on a specific day; in a group trip the search becomes a voting round.
3. The group decides the dates and the stay with polls shared to WhatsApp; friends without an account vote on the guest page.
4. The owner sends a members' packing list; everyone ticks their own bag, and the group shares who brings the stove.
5. During the trip the group records expenses (also from a receipt photo); at the end Saldo shows the fewest transfers and the receiver's bank details.
6. The user uses the app without signal; changes are sent when back online.
7. After the trip the group gets a recap card to share and can start the next trip from it.

## Scope per phase

Six phases, then the group trip stages. Phase 1 is already useful for a real trip without any server.

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 — Offline app (solo) | Trips, itinerary, packing, expenses on Android and iOS; local Room database; no server | One real trip can be planned start to finish on a phone |
| 2 — Basic backend | Ktor + PostgreSQL CRUD for the same data (dummy user); web app joins, online-only | Every endpoint works and data is stored in Postgres |
| 3 — Auth and sync | Sign up / sign in, forgot password, email verification, offline-first sync between phone and web | A trip created offline on a phone shows up on the web once online |
| 4 — Multi-member | Invites, roles, paid by, split bill, balances, settlements, packing templates | Two accounts manage one trip and the split result is correct |
| 5 — Explore AI | Preference form, swipe cards, Unscheduled, Explore credits, place voting on group trips | Recommendations are real places and can be scheduled into the itinerary |
| 6 — Admin (Karung) | Internal admin web app: users, credit adjustments, monitoring, settings, remote content | An admin can find a user, adjust their credits and see it in their credit history |
| Group trips (stages 1–9, Oct 2026) | Members by name, comments, polls, group money, destinations, overseas trips, group packing, sharing and the guest page, documents, recap, trip templates | A group of four, one without an account, plans, packs and settles one trip in the app |

The UI is designed in the "RANSEL — IRIS" canvas and the admin app in the "Karung" canvas; what remains is listed under Open questions.

## Functional requirements

### Home and trips

- Home shows the nearest trip (cover, countdown), a **Persiapan trip** card (itinerary days filled, packing items ready, number of unscheduled places), and a prompt to Explore AI. Tapping the nearest trip opens it on **Ringkasan** for a group trip and on **Itinerary** for a solo trip. For two weeks after a trip ends, a "Rekap siap" card opens its recap. There is no remaining-budget card and no shortcut tiles.
- My trips groups trips into Ongoing, Upcoming, and Finished. The Create trip button is the FAB.
- Create/edit trip: name, destination (destination picker), date range (DateRangePicker; days before today cannot be picked, except that a trip under way keeps the start it has), budget, currency, cover, and on Create an optional **trip template**. Deleting a trip goes through a dialog that lists how many activities, items, and expense entries will be deleted with it.
- A trip has four tabs: **Ringkasan**, **Itinerary**, **Packing**, **Biaya**. Tab labels always stay on one line (they shrink to at least 12 sp before an ellipsis). The header shows back, the trip title and dates (plus the difference with WIB abroad or in WITA/WIT), **Rekap trip** after the end date, and **Ubah trip** for the owner. Members open from Ringkasan, not from the header (owner, 27 Oct 2026).
- Empty states: Home without trips, My trips empty, and My trips without upcoming trips.

### Ringkasan (the trip hub)

- The first tab of every trip (renamed from "Grup" on 27 Oct 2026). A big green **HeroCard** shows the countdown ("12 hari lagi", "Hari ke-2 dari 4", "Trip selesai") with the trip name and dates.
- **Solo trip** (only the user, as the server says): the countdown, **Dokumen trip**, and for the owner an **Ajak teman** card that opens the invite sheet. No voting, money or members.
- **Group trip**, top to bottom:
  - **Perlu tindakanmu**: one card per place-voting round waiting for the user (a tap opens its swipe cards, or the owner's result when voting has closed), polls to vote on or to confirm, payments to confirm, and transfers the user still owes.
  - **Voting berjalan**: polls and voting rounds still open that only wait for others.
  - **Riwayat voting** `n ›`: one row that opens the finished polls and decided rounds.
  - **Dokumen trip** `n ›`.
  - **Anggota** with the count; tapping opens the members screen.
- The + button (owner and editors): Buat polling (group trips only), Ajak anggota (owner), Unggah dokumen.
- Section titles on every screen use one style (`SectionHeader`, IRIS headline-small).
- A signed-out user sees the countdown and an invitation to sign in.

### Itinerary

- Activities per day with start–end time, category, location, and notes.
- Categories (ID / EN): Wisata / Explore (blue), Kuliner / Eat (orange), Perjalanan / Travel (teal), Menginap / Stay (purple), Belanja / Shop (yellow), Lainnya / Other (brown).
- Activities are reordered with a **long press** anywhere on the row, then drag; there is no drag handle, so scrolling past a row never moves it (owner, 27 Oct 2026). Times are recalculated automatically (see Business rules).
- An **Unscheduled** box above the day list holds places from Explore AI or added manually, each with a Schedule button, plus a full list (filter All / From AI / Manual).
- Schedule a place: pick a day; the time is filled in at the end of that day and can be changed with the TimePicker.
- Voting lives only on Ringkasan; Itinerary, Packing and Biaya show no voting banner.

### Packing

- One component for every list (solo, Pribadi, Bersama): items by category with an icon, a "3/5" count and "Lengkap", collapsible; each row has a checkbox, name, quantity and a ⋮ button with **Ubah** and **Hapus** (Hapus asks first). A long press also opens Ubah.
- One progress card for the solo list and Pribadi: "Siap dibawa · x dari y barang", a bar, the departure countdown, "Wajib x/y" when some lines are required, and the ⋮ menu: **Pakai template**, **Simpan sebagai template**, **Hapus semua centang**. An empty list offers Pakai template.
- Save as template: name, option to keep quantities, option to share with trip members; checked state is not saved.
- Group trips: see Group packing.

### Biaya (expenses)

- Record an expense: amount, currency, category (the same six as the itinerary and in the same colors, with noun labels that fit money: Makan / Food, Transport, Penginapan / Lodging, Aktivitas / Activities, Belanja / Shopping, Lainnya / Other), note, date (may fall outside the trip dates), and payment method. The + button asks **Foto struk** or **Catat manual**.
- Ringkasan view: a green **HeroCard** with the remaining budget (or the total spent without a budget; it stays green over budget and says "Melebihi budget"), unpaid estimates, a donut by category, transactions by day.
- Shared trips add Paid by (one or several payers), Split equally / Set amounts, and the green **Ringkasan | Saldo** tabs (the same `SegmentedTabs` as Packing's Pribadi | Bersama).
- See Group money for Saldo, receipts and estimates.

### Explore AI

- The Explore tab holds the preference form for a trip: destination (editable per search), price level relative to local prices (Hemat / Sedang / Bebas), core interests plus destination interests suggested by the AI, travel style (Santai / Padat), and notes. Full rules: `docs/specs/AI-Recommendation-Spec.md`.
- Results are swipe cards: swipe right to save, left to skip, with Undo. Saved places go to Unscheduled in the related trip. On a trip with two or more members a search becomes a **voting round** (`docs/specs/voting-spec.md`): every member swipes within 24 hours and the owner decides which places enter the itinerary.
- An unfinished session can be resumed from a "Continue" card on the Explore screen.
- States: loading, all cards seen, error, no results, out of credits, sign-in required (guest), and verify email first.

### Account and sync

- Three onboarding screens, Sign up, Sign in, and "Use without an account for now".
- Forgot password: email → check your email → new password.
- Email verification after sign-up (owner decision, 22 Oct 2026): the new account can use the app right away, but Explore and the signup credits wait until the link in the email (valid 24 hours) is opened. The app shows "Satu langkah lagi" after sign-up and a verify gate in the Explore tab (resend link, "Sudah verifikasi").
- Account: profile, sync status, default currency, theme (system/light/dark), language (also on the web: the page reloads in the new language), payment info (bank accounts and e-wallets), credits, packing and activity reminders, sign out.
- Session expired: ask to sign in again, or continue offline.

### Notifications

- Signed-in users get an in-app inbox (the bell on Home) and push notifications (Android first; iOS comes with the Xcode project). Guests have neither.
- Events: a friend joins your trip (also through a guest link), you are removed or your role changes, someone claims a name, a shared expense that includes you is recorded, a payment to or from you is marked or claimed, a voting round or poll asks for your vote or has a result, a mention in a comment, the owner's packing list arrives or changes, a document is added, and the recap is ready.
- Reminders from the server: the evening before departure (18:00 in the trip's zone) if packing items are not ticked yet, 30 minutes before a scheduled activity, before a poll closes, and the owner's "Ingatkan" for the packing list (once per member per 12 hours).
- Three switches in Account: trip activity, expenses and settlements, reminders. A switch that is off means no inbox entry and no push for that kind. The person who caused an event is never notified about it.
- The inbox stores what happened, not the sentence, so it is shown in the app language; the push text follows the language the phone reported.

### Members (Phase 4 and group stage 1)

- The owner invites by code or link with the Editor or Viewer role; invites can be revoked. The owner can also add a person **by name** (no account; always Viewer) and send them the link on WhatsApp.
- The invitee sees a Join trip screen (or an invalid-invite screen); on a trip with people added by name the new member picks "Kamu yang mana?" to take over their name and everything that pointed at it. The owner can undo a wrong claim.
- The owner can change roles and remove members; members can leave a trip. Members are opened from Ringkasan → Anggota.
- Comments live on poll options (owner decision 25 Oct 2026), with @mentions.

### Group trips

**Polls** (stage 2–3). Kinds: dates, destination, stay, food, place, budget, transport, custom. The owner and editors create a poll from a template question: closing in **1, 2 or 3 days**, several choices, anonymous, "Terapkan pemenang". Options per kind (dates, place, price, link with "Ambil info", or **Jelajah** to add AI places for stay and food polls). Members tap to vote and can change their vote while open. The creator or owner can close early: "Tutup voting sekarang" opens the confirm dialog at once when there is one leader (a tie is picked on the poll screen); confirming runs the winner's action (dates set the trip dates, destination the destination, place adds an unscheduled place, food an activity, stay an estimate plus the booking link as a document). A poll is never decided automatically; everyone having voted does not close it.

**Place voting** (Phase 5). An Explore search on a group trip becomes a round; see `docs/specs/voting-spec.md`. Rows look like poll rows (magenta tile with the AI mark, status tag).

**Sharing and the guest page** (stage 8). "Bagikan" sends a poll's guest link from the user's own WhatsApp; `/p/<code>` (poll) and `/j/<code>` (invite) open a web page that needs no account: the guest picks their name or writes one ("Nama saya tidak ada" makes a member without an account), votes, and ticks the packing lines they received. One browser per name; the owner can release a name. Link previews show one static image per link type.

**Group money** (stage 4). Saldo: my position (red card when I owe, green when money comes back, neutral when even), my payment info, transfers to me (🔔 Ingatkan with a WhatsApp message, Tandai lunas), from me (Bayar → the receiver's accounts and "Saya sudah bayar"), and claims waiting for me to confirm. A claim counts once the receiver (or the owner, for a receiver without an account) confirms. Amounts always show whole on one line. A STAY poll winner adds an estimate to Biaya until it is paid.

**Receipt photos** (stage 4). Foto struk reads the receipt with AI (costs 1 credit, "Foto struk" in the credit history; nothing when unreadable) and opens "Cek hasil struk": place, date (editable; the year shows when it is not the trip's), category, payer, and **Bagi rata** or **Per item**. Per item: each line gets its members; tax and service added on top are spread in proportion to each person's items (said under the tax line) and "Bagian tiap orang" lists every share with its part of the tax; tax already in the prices ("Incl. Pb1 10%", the items alone make the total) is only a note. Save waits until the lines add up to the total.

**Destinations** (stage 5). One destination picker for the trip form, the Explore form and destination poll options: suggestions while typing, recents, "Populer untuk trip grup", "Gunakan lokasi saat ini", and a built-in list of Indonesian regions offline or for guests.

**Overseas trips** (stage 6). A destination abroad sets the trip's country, local currency and time zone (editable). New expenses start in the local currency with the day's reference rate (or the user's own rate) and show the amount in the main currency; days and reminders follow the trip's zone.

**Group packing** (stage 7). Packing gets green **Pribadi | Bersama** tabs.
- **Bersama** is the trip-wide list: who brings what. "Saya bawa" on an unclaimed item (no extra text needed), the bringer's avatar and name otherwise; only the bringer ticks it; the bringer or an editor gives it back. Its summary card has a ⋮ menu (owner and editors) with Pakai template and Simpan sebagai template; no "untick all", since ticks belong to the bringers.
- **Pribadi** is each member's own bag: one list by category. Lines that came from the owner's list look and behave like the member's own (tick, edit, delete); only "Wajib" shows (owner, 27 Oct 2026).
- **Members' list** (owner only): the owner writes one list (from their own bag, a template or blank; "Wajib?" and quantity per line) and sends it to everyone or chosen members. Every recipient **and the owner** get a copy in Pribadi; a line someone already keeps (same category and name) is linked, not added twice. Later saves add new lines everywhere and change or remove only the copies a member has not edited; a copy a member deleted never comes back. On the owner's Pribadi tab the list is one line ("2 dari 5 anggota siap", or the unsent draft) that opens **Progres anggota**: the list with Ubah and Kirim, every member (not ready first; ready = all required lines ticked, or all lines when none is required) with a bell each, and "Ingatkan yang belum siap".

**Trip documents** (stage 9). Tickets, bookings and vouchers per trip, by group (Perjalanan, Menginap, Aktivitas, Lainnya), as a PDF or image (20 MB per file, 200 MB per trip) or a link, optionally tied to an activity chosen on a full-screen **Pilih aktivitas** (search, by day, unscheduled last, "Tanpa aktivitas"). Files open from the phone once downloaded, also offline. Editors upload; the uploader or the owner deletes.

**Recap and trip templates** (stage 9). The day after the trip ends the group gets a recap (days, activities, places, spend, whether everything is settled), shareable as a picture; it can be saved as a trip template (itinerary by day number plus the shared packing list) and "Buat trip dengan grup ini" starts the next trip through the invite sheet.

### Admin (Karung, Phase 6)

Full rules: `docs/specs/admin-spec.md`.

- A web app for admins only, at `karung.mietek.tech`. Admins are the accounts listed in the server's `ADMIN_EMAILS`; they sign in with their normal RANSEL account.
- Users: search and filter users, see a profile with trip metadata (never trip content), credit history (receipt photos labelled "Foto struk"), devices and recent activity.
- Adjust credits: add or subtract wallet credits for one user with a required reason; the balance never goes below 0 and the free daily allowance is not touched. The user sees the row as "Penyesuaian admin" in their credit history.
- Other actions: sign a user out of every device, deactivate or reactivate an account, send a password reset email (once an email provider exists). Every admin action is written to an audit log.
- Monitoring: active users, new trips, Explore searches, credits used, AI cost against the monthly budget, errors per code, server health.
- Settings: credit numbers (signup grant, daily free credits, cost per action) and AI budget settings can be changed without a deploy; the server config values stay the defaults.
- Remote content: app strings, error messages, category looks and destination interests are edited and published in Karung; the apps start reading them in a later step.

### Common states

- Connection error, server error, coming soon, and empty use one pattern with a capybara illustration and the error code from the API.
- Back (Android back, the browser's back button) closes an open sheet first and keeps the screen.

## Business rules

**Itinerary drag and drop.** Each activity keeps its duration; the affected range is packed together starting from the earliest start time in that range. Gaps are not stored or preserved. Example: 10:00–11:25, 11:25–12:00, 12:00–14:00; moving item 1 to position 3 gives 10:00–10:35, 10:35–12:35, 12:35–14:00. A confirmation dialog appears before saving (it can be turned off with "Don't ask again"), followed by an Undo snackbar.

**Schedule a place.** The place goes to the end of the chosen day, starting when the last activity ends; an empty day starts at 09:00. The duration comes from the AI estimate. The user can change the time.

**Trip dates.** A new trip, a new trip from Explore, and date poll options cannot start before today. Editing a trip under way may keep its saved start.

**Explore session.** The Explore tab always opens the form. An unfinished swipe session shows as a Continue card and a dot on the tab icon. A session ends when all cards have been seen or the user starts a new search. Searches cost credits: a new user gets 30 credits once (after verifying their email), plus 3 free credits a day that refill at 00:00 WIB and do not accumulate; a new search costs 3 credits, the next batch of the same search 1, and a receipt photo 1 (numbers are server config; an admin can change them in Karung). The free credits are used first. When credits run out the app offers to add credits with a voucher code; buying credits comes with a payment provider later. Explore requires an account: a guest who opens the Explore tab sees a sign-in screen (Masuk / Daftar) instead of the form, and signing in moves the trips already on the device to the account. Every other feature still works without an account. Recommendations are always saved into a trip; a signed-in user with no trip sees a "Buat trip dulu" (create a trip first) screen in the Explore tab.

**Split bill.** By default the amount is split equally between the ticked members. With Set amounts, the shares must add up to the amount; the difference is shown as "short" or "over" and Save stays disabled until it matches. A bill paid by several people lists each payer's amount. Per item from a receipt: see Receipt photos.

**Balances.** Each person's balance = total paid − total share + confirmed payments made − confirmed payments received. Estimates do not count. "Supaya lunas" shows the fewest transfers. Tandai lunas records the payment at once; "Saya sudah bayar" waits for the receiver's confirmation.

**Polls.** Only the creator or the owner closes, confirms, reopens or deletes a poll. Voting ends when its time runs out or when it is closed early; the result is then confirmed (or a tie picked) before its action runs. Anonymous polls never show who chose what.

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
