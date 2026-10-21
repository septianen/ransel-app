# PRD — RANSEL

Oct 13, 2026 · @SEN

## Summary

RANSEL is a trip planner that keeps the itinerary, the packing list, and expenses for one trip in one place, with AI place recommendations picked by swiping. It is built with Compose Multiplatform for Android, iOS, and Web, with a Ktor backend, and designed with the IRIS design system.

**Problem:** people plan a holiday across many tools at once: the schedule in notes, the packing list in another app, expenses in a spreadsheet, and place ideas from random searches. When travelling with friends, who paid for what gets lost.

**Solution:** one trip as the hub for everything, usable offline, shareable with friends, and helped by AI to find places that match the traveller's taste.

**Platforms:** Android, iOS (Compose Multiplatform), and Web (wide layout ≥ 1240 dp with a navigation rail). The UI is available in Indonesian and English. Android and iOS work offline and without an account; the web app is online-only and requires signing in, since it is loaded from the server anyway.

## Goals and metrics

Main goal: a user can take one trip from zero to ready-to-go without switching apps.

| Goal | Metric | Initial target |
| --- | --- | --- |
| Trips are actually planned | % of trips with ≥ 1 activity on ≥ 50% of their days | set after beta |
| Packing is used until departure | % of upcoming trips with packing progress ≥ 80% the day before | set after beta |
| Expenses are recorded | average expense entries per ongoing trip | set after beta |
| Explore AI is useful | places saved per swipe session; % of saved places that end up scheduled | set after beta |
| Collaboration (Phase 4) | % of trips with ≥ 2 members; % of balances marked as settled | set after beta |

Target numbers have not been discussed yet, so the target column is left open on purpose.

**Non-goals:**

- Booking tickets or hotels, or payments inside the app.
- Turn-by-turn map navigation.
- Money transfers between members; the app only records who owes whom and whether it is settled.
- Social features or a public feed.

## Personas and scenarios

| Persona | Needs | Main features |
| --- | --- | --- |
| Solo planner (e.g. Septian, 4 days in Bali) | Daily schedule, packing list, budget control | Trip, Itinerary, Packing, Expenses |
| Inspiration seeker | Does not know yet where to go at the destination | Explore AI (swipe), Unscheduled |
| Group of friends (4 people) | Shared plan, splitting costs, knowing who owes whom | Members, split bill, Balances |
| Traveller without signal | Can still view and change the plan | Offline-first, automatic sync |

Main scenarios:

1. The user creates the trip "Bali 4 hari" (20–23 Nov 2026, budget Rp5,000,000), adds activities per day, then reorders them.
2. The user looks for ideas with Explore AI, saves a few places, and schedules them on a specific day.
3. The user prepares packing from a template and ticks items off before leaving.
4. During the trip the user records expenses; on a shared trip, expenses are split and balances are settled at the end.
5. The user uses the app without signal; changes are sent when back online.

## Scope per phase

Six phases in order; Phase 1 is already useful for a real trip without any server, and multi-member and AI features are deliberately last.

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 — Offline app (solo) | Trips, itinerary, packing, expenses on Android and iOS; local Room database; no server | One real trip can be planned start to finish on a phone |
| 2 — Basic backend | Ktor + PostgreSQL CRUD for the same data (dummy user); web app joins, online-only | Every endpoint works and data is stored in Postgres |
| 3 — Auth and sync | Sign up / sign in, forgot password, offline-first sync between phone and web | A trip created offline on a phone shows up on the web once online |
| 4 — Multi-member | Invites, roles, paid by, split bill, balances, settlements, packing templates | Two accounts manage one trip and the split result is correct |
| 5 — Explore AI | Preference form, swipe cards, Unscheduled, Explore credits | Recommendations are real places and can be scheduled into the itinerary |
| 6 — Admin (Karung) | Internal admin web app: users, credit adjustments, monitoring, settings, remote content (admin side) | An admin can find a user, adjust their credits and see it in their credit history |

The UI for Phases 1–5 is designed in the "RANSEL — IRIS" canvas and the admin app in the "Karung" canvas; what remains is listed under Open questions.

## Functional requirements

### Home and trips

- Home shows the nearest trip (cover, countdown), a **Trip prep** card (itinerary days filled, packing items ready, number of unscheduled places), and a prompt to Explore AI. There is no remaining-budget card and no shortcut tiles.
- My trips groups trips into Ongoing, Upcoming, and Finished. The Create trip button is the FAB.
- Create/edit trip: name, destination, date range (DateRangePicker), budget, currency, cover. Deleting a trip goes through a dialog that lists how many activities, items, and expense entries will be deleted with it.
- Empty states: Home without trips, My trips empty, and My trips without upcoming trips.

### Itinerary

- Activities per day with start–end time, category, location, and notes.
- Categories (ID / EN): Wisata / Explore (blue), Kuliner / Eat (orange), Perjalanan / Travel (teal), Menginap / Stay (purple), Belanja / Shop (yellow), Lainnya / Other (brown).
- Activities can be reordered by drag and drop; times are recalculated automatically (see Business rules).
- An **Unscheduled** box above the day list holds places from Explore AI or added manually, each with a Schedule button, plus a full list (filter All / From AI / Manual).
- Schedule a place: pick a day; the time is filled in at the end of that day and can be changed with the TimePicker.

### Packing

- Items per category with quantity and a ready checkbox; progress such as "5 of 9 items".
- ⋮ menu: Use template, Save as template, Uncheck all.
- Save as template: name, option to keep quantities, option to share with trip members; checked state is not saved.

### Expenses

- Record an expense: amount, currency, category (the same six as the itinerary and in the same colors, with noun labels that fit money: Makan / Food, Transport, Penginapan / Lodging, Aktivitas / Activities, Belanja / Shopping, Lainnya / Other), note, date (may fall outside the trip dates), and payment method.
- Summary: remaining budget, donut by category, transactions by day.
- Shared trips add: Paid by, Split equally / Set amounts, a "Your position" card, and a Balances tab with "To settle up" and Mark as settled.

### Explore AI

- The Explore tab holds the preference form: destination (text field), dates (a separate field), budget per person per day (Hemat / Sedang / Bebas, i.e. Budget / Moderate / Flexible), interests (multi-select: nature, food, beach, culture, shopping, adventure, nightlife, photo spots), travel style (Santai / Padat, i.e. Relaxed / Packed), and notes.
- Results are swipe cards: swipe right to save, left to skip, with Undo. Saved places go to Unscheduled in the related trip.
- An unfinished session can be resumed from a "Continue" card on the Explore screen.
- States: loading, all cards seen, error, no results, out of credits, and sign-in required (guest).

### Account and sync

- Three onboarding screens, Sign up, Sign in, and "Use without an account for now".
- Forgot password: email → check your email → new password.
- Account: profile, sync status, default currency, theme (system/light/dark), language, packing and activity reminders, sign out.
- Session expired: ask to sign in again, or continue offline.

### Notifications

- Signed-in users get an in-app inbox (the bell on Home) and push notifications (Android first; iOS comes with the Xcode project). Guests have neither.
- Events: a friend joins your trip (owner and editors are told), you are removed or your role changes, a shared expense that includes you is recorded, a payment to or from you is marked, and (when voting exists) a request to vote and its result.
- Reminders from the server: the evening before departure (18:00 WIB) if packing items are not ticked yet, and 30 minutes before a scheduled activity. Every trip uses WIB for now.
- Three switches in Account: trip activity, expenses and settlements, reminders. A switch that is off means no inbox entry and no push for that kind. The person who caused an event is never notified about it.
- The inbox stores what happened, not the sentence, so it is shown in the app language; the push text follows the language the phone reported.

### Members and cost splitting (Phase 4)

- The owner invites by code or link with the Editor or Viewer role; invites can be revoked.
- The invitee sees a Join trip screen, or an invalid-invite screen.
- The owner can change roles and remove members; members can leave a trip.
- Every trip screen (Itinerary, Packing, Expenses) has a members button in the header.

### Admin (Karung, Phase 6)

Full rules: `docs/specs/admin-spec.md`.

- A web app for admins only, at `karung.mietek.tech`. Admins are the accounts listed in the server's `ADMIN_EMAILS`; they sign in with their normal RANSEL account.
- Users: search and filter users, see a profile with trip metadata (never trip content), credit history, devices and recent activity.
- Adjust credits: add or subtract wallet credits for one user with a required reason; the balance never goes below 0 and the free daily allowance is not touched. The user sees the row as "Penyesuaian admin" in their credit history.
- Other actions: sign a user out of every device, deactivate or reactivate an account, send a password reset email (once an email provider exists). Every admin action is written to an audit log.
- Monitoring: active users, new trips, Explore searches, credits used, AI cost against the monthly budget, errors per code, server health.
- Settings: credit numbers (signup grant, daily free credits, cost per action) and AI budget settings can be changed without a deploy; the server config values stay the defaults.
- Remote content: app strings, error messages, category looks and destination interests are edited and published in Karung; the apps start reading them in a later step.

### Common states

- Connection error, server error, coming soon, and empty use one pattern with a capybara illustration and the error code from the API.

## Business rules

**Itinerary drag and drop.** Each activity keeps its duration; the affected range is packed together starting from the earliest start time in that range. Gaps are not stored or preserved. Example: 10:00–11:25, 11:25–12:00, 12:00–14:00; moving item 1 to position 3 gives 10:00–10:35, 10:35–12:35, 12:35–14:00. A confirmation dialog appears before saving (it can be turned off with "Don't ask again"), followed by an Undo snackbar.

**Schedule a place.** The place goes to the end of the chosen day, starting when the last activity ends; an empty day starts at 09:00. The duration comes from the AI estimate. The user can change the time.

**Explore session.** The Explore tab always opens the form. An unfinished swipe session shows as a Continue card and a dot on the tab icon. A session ends when all cards have been seen or the user starts a new search. Searches cost credits: a new user gets 30 credits once, plus 3 free credits a day that refill at 00:00 WIB and do not accumulate; a new search costs 3 credits and the next batch of the same search 1 (numbers are server config; an admin can change them in Karung). The free credits are used first. When credits run out the app offers to add credits with a voucher code; buying credits comes with a payment provider later. Explore requires an account: a guest who opens the Explore tab sees a sign-in screen (Masuk / Daftar) instead of the form, and signing in moves the trips already on the device to the account. Every other feature still works without an account. Recommendations are always saved into a trip; a signed-in user with no trip sees a "Buat trip dulu" (create a trip first) screen in the Explore tab.

**Split bill.** By default the amount is split equally between the ticked members. With Set amounts, the shares must add up to the amount; the difference is shown as "short" or "over" and Save stays disabled until it matches.

**Balances.** Each person's balance = total paid − total share. "To settle up" shows the fewest transfers. Mark as settled records the payment and updates the balances.

**Roles.**

| Action | Owner | Editor | Viewer |
| --- | --- | --- | --- |
| View the trip | yes | yes | yes |
| Change itinerary, packing, expenses | yes | yes | no |
| Take part in cost splitting | yes | yes | yes |
| Invite, change roles, remove members | yes | no | no |
| Delete the trip | yes | no | no |
| Leave the trip | after transferring ownership | yes | yes |

**Invites.** A code `NAME-XXXX` and a link `ransel.mietek.tech/j/<code>`, with a usage limit and an expiry date. An invite fails when it has expired, was revoked, or is full. Removing a member does not delete the expense records that involve them.

**Currency.** The default currency only applies to new trips; existing trips keep their own currency.

## Non-functional and UX requirements

- **Offline-first.** On Android and iOS, all core features (trips, itinerary, packing, expenses) work without a connection and without an account. Offline changes are marked "waiting to sync" and sent automatically when online, with a snackbar "Back online · n changes sent". Explore AI and invites need a connection.
- **Language.** Indonesian and English via Compose Resources (`values/` and `values-in/`); category labels have official pairs (Wisata / Explore, Kuliner / Eat, and so on).
- **Theme.** Light, dark, and follow system, using the agreed IRIS dark color mapping.
- **Design system.** Every screen uses IRIS tokens and components; the capybara illustrations come from the "Ilustrasi" asset group in IRIS (transparent PNG, shipped in the app as 2× WebP).
- **Built-in components.** Date picker, date range picker, and time picker use the built-in Material 3 components that run on Compose Multiplatform.
- **Accessibility.** Text contrast at least 4.5:1, touch targets at least 48 dp, decorative illustrations without alt text, labels on icon buttons.
- **Responsive.** A 390 dp phone is the reference; web ≥ 1240 dp uses a navigation rail and a side panel.
- **Account security.** Password reset links are valid for 30 minutes; after a password change, sessions on other devices end.

## Open questions

- [ ] Target numbers for the success metrics.
- [ ] Ownership transfer flow before the owner leaves a trip.
- [ ] Screens not designed yet: enter invite code, activity detail/edit, edit profile, language sheet, search trips, all transactions.
- [ ] Dark theme for Itinerary, Packing, Explore, sheets, and dialogs (the pattern exists, the screens do not).
