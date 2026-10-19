# AI Recommendation Spec — RANSEL

Oct 16, 2026 · @SEN

## Summary

Explore AI (Phase 5) suggests real places for one trip; the user swipes right to save a place into that trip's Unscheduled box. This spec is the single source for the Explore flow, the preference form and the recommendation request. Where PRD, TRD or App Spec say otherwise about Explore, this spec wins.

Decisions agreed on 16 Oct 2026:

- Explore always starts by choosing a trip: an existing ongoing or upcoming trip, or a new trip created when the search starts.
- Destination and dates always come from the trip. An optional focus area narrows one search.
- Budget becomes a **price level** (`$` / `$$` / `$$$`) relative to local prices, preselected from the trip budget when there is one.
- Interests are split into **core interests** (fixed, valid everywhere) and **destination interests** (3–5 chips suggested by AI per destination and month, cached).
- Explore requires an account; the daily quota stays at 10 searches per user, reset 00:00 WIB. Loading destination interests never uses quota.

## User flow

The Explore tab opens the trip picker; the form comes second and always knows its trip.

&#91;embedded content: Explore flow · 2 entries, 2 shortcuts, 1 search\]

Shortcuts land directly on step 2a or 2b; both forms end in the same search.

1. **Step 1, trip picker** (`ExploreTripPickerScreen`). Lists the user's ongoing and upcoming trips where they are OWNER or EDITOR, nearest first, each with a status tag and one line of context (for example "5 belum dijadwalkan"). Finished trips and view-only trips are hidden. "Trip baru" is a floating action button at the bottom right, always visible however long the list is. The header shows the remaining quota ("7 dari 10 pencarian tersisa hari ini").
2. **Step 2a, existing trip** (`ExplorePreferencesScreen`, existing). An "Untuk trip" card with a Ganti button back to step 1, then the form (see Form inputs). Button: "Cari rekomendasi".
3. **Step 2b, new trip** (`ExplorePreferencesScreen`, new). Destination, dates (DateRangePicker), trip name prefilled "Trip ke \<destination>", then the same preferences. Button: "Buat trip dan cari". Tapping it creates the trip through the normal trip repository (default currency, no budget), makes sure it reached the server, then searches. The trip stays even if no place is saved.
4. **Loading, swipe and result states** are unchanged: loading (`CaveCartLoading`), swipe cards, all seen, error, no results, quota reached.

Shortcuts that skip step 1:

- "Cari inspirasi dengan AI" inside a trip (Itinerary empty state, Unscheduled sheet, Home callout for the nearest trip) opens step 2a for that trip.
- A user with no eligible trip goes straight to step 2b (Home empty, My trips empty, My trips with only finished trips).
- "Ubah preferensi" from swipe opens step 2a for the same trip, with the last preferences filled in.

Each trip can have its own unfinished swipe session (a new search for a trip replaces that trip's old one). It shows as a "Lanjutkan" strip inside that trip's row in step 1, so several can be open at once, and the Explore tab has a magenta dot while any is unfinished. A guest sees the sign-in screen instead of step 1.

## Form inputs

Every input is relative to the trip's destination, so the same form works for Bali, Tokyo or the Himalaya.

| Input | Step | Values | Default | Sent as |
| --- | --- | --- | --- | --- |
| Destination, dates, trip name | 2b only | text, date range, text | name = "Trip ke \<destination>" | stored on the trip, not in the request |
| Focus area | 2a only | free text, optional | empty = whole destination | `focusArea` |
| Price level | 2a, 2b | `$` Murah, `$$` Sedang, `$$$` Mewah | from trip budget, else `$$` | `priceLevel`: LOW, MEDIUM, HIGH |
| Core interests | 2a, 2b | Alam, Kuliner, Budaya & sejarah, Belanja, Petualangan, Hiburan malam, Spot foto, Santai | none | `interests` (enum) |
| Destination interests | 2a, 2b | 3–5 AI chips in a "Khas \<destination>" box | none | `extraInterests` (strings) |
| Travel style | 2a, 2b | Santai (2–3 places a day), Padat (4–6) | Santai | `style` |
| Notes | 2a, 2b | free text, optional | empty | `notes` |

At least one interest, core or destination, is required before the button enables.

**Price level.** It describes the price of the places, not the total trip cost, and it is relative to local prices: a cheap ramen stall in Tokyo is `$` in Tokyo. Each option carries a short example (Murah: "Jajanan lokal, tiket murah atau gratis"; Sedang: "Restoran lokal, atraksi populer"; Mewah: "Fine dining, pengalaman premium"). It maps directly to Google Places `priceLevel` (`$` = 0–1, `$$` = 2, `$$$` = 3–4).

**Preselect from the trip budget.** When the trip has a budget, the app shows the level the server suggests (see API contract) with the note "Dipilih dari budget trip \<name>: sekitar \<amount> per orang per hari". The amount = budget / trip days / member count. Without a budget, `$$` is selected with the note "Trip ini belum punya budget, jadi Sedang dipilih sebagai awal. Isi budget di Edit trip supaya terpilih otomatis." The user can always change it.

**Core vs destination interests.** Core interests are an enum, the same in every destination, so prompts, cache keys and statistics stay stable. Beach is not core: it appears only as a destination interest where it fits. Destination interests are plain strings shown with the AI style (purple box, sparkle icon) so users can tell where they come from.

## Destination interests

Destination interests load on their own while the user fills the form; the form never waits for them and the search button never waits for them.

**When the request is sent**

- Existing trip: as soon as the trip is picked in step 1, so the chips are usually ready when the form opens. The server also warms the cache in the background whenever a trip is created or its destination or dates change.
- New trip: after the user stops typing the destination for 600 ms, or leaves the field. No request per keystroke.
- New trip, dates set or moved to another month: the chips reload for that month. Chips already ticked that still exist in the new list stay ticked; the others are dropped.

**Cache.** Key = normalized destination + travel month (the month of the trip start date). Bali in November is generated once for every user. The month is part of the key so seasonal chips (Sakura for Tokyo in late March) only show in season. Entries expire after 30 days.

**Timing and states**

| Situation | What the user sees |
| --- | --- |
| Cache hit | Chips appear at once |
| Cache miss | Skeleton chips + "Mencari minat khas \<destination>…" for about 2–4 s (one short LLM call) |
| No answer after 8 s, or an error | The "Khas …" box is hidden; core interests still work; no error message |
| Destination not recognized (typo) or empty list | The box is hidden |
| Offline | The box is hidden (Explore needs a connection anyway) |

**Quota.** Loading destination interests never counts against the 10 daily searches. It has its own soft limit on the server (60 requests per user per hour) to stop abuse.

## API contract

One new read endpoint feeds the form; the search request loses destination and dates and gains price level, focus area and destination interests. All classes live in `shared` (`dto/RecommendationDtos.kt`, `model/Enums.kt`, `api/ApiRoutes.kt`).

| Method | Path | Body / query | Response | Quota |
| --- | --- | --- | --- | --- |
| GET | `/explore/suggestions` | `tripId` (step 2a) or `destination` + `month` as `YYYY-MM` (step 2b) | `ExploreSuggestionsResponse` | none; soft limit 60 per user per hour |
| POST | `/recommendations` | `RecommendationRequest` | `RecommendationResponse` (unchanged) | 1 search, except `newSearch = false` |
| POST, DELETE | `/recommendations/feedback` | unchanged | 204 | none |

**ExploreSuggestionsResponse**

- `interests: List<String>`: 3–5 destination interests in the user's language (Indonesian or English from `Accept-Language`), each at most 40 characters. Empty list = hide the box.
- `suggestedPriceLevel: PriceLevel?`: only with `tripId` and a trip budget.
- `budgetPerPersonPerDayMinor: Long?` + `currency: String?`: the amount shown in the preselect note, in the trip's base currency.

**RecommendationRequest**

- `tripId: String`
- `focusArea: String? = null` (at most 60 characters)
- `priceLevel: PriceLevel` (LOW, MEDIUM, HIGH), replaces `budget: BudgetLevel`
- `interests: List<Interest>`: NATURE, FOOD, CULTURE, SHOPPING, ADVENTURE, NIGHTLIFE, PHOTO, RELAX. BEACH is removed; RELAX is new.
- `extraInterests: List<String> = emptyList()`: the ticked destination interests, at most 5, each at most 40 characters, copied from the suggestions response.
- `style: TravelStyle`, `notes: String?`, `excludePlaceIds`, `newSearch`: unchanged.

**Errors**

| Status | Code | When |
| --- | --- | --- |
| 404 | `TRIP_NOT_FOUND` | trip missing or the user is not a member |
| 403 | `FORBIDDEN` | the user is a VIEWER |
| 422 | `VALIDATION_FAILED` | `details.tripId = TRIP_ENDED`, `details.interests = EMPTY` (no core and no extra interest), `details.extraInterests = TOO_MANY` |
| 429 | `AI_QUOTA_EXCEEDED` | 10 searches used today (search only) |
| 429 | `RATE_LIMITED` (new) | suggestions soft limit hit |
| 503 | `AI_UNAVAILABLE` | LLM failed twice; for suggestions the app just hides the box |

## Server pipeline

The server owns every AI and Places call; the app never sees a key.

**Suggestions** (`GET /explore/suggestions`)

1. Resolve destination and month: from the trip (`tripId`, role at least VIEWER) or from the query.
2. Look up the cache (`explore_suggestions_cache`, key = normalized destination + month + language). Hit: return it.
3. Miss: one short LLM call asking for JSON with 3–5 interests that are typical for that destination in that month and are not already covered by the core list, plus a price guide: the typical spend per person per day in USD at the edge between `$` and `$$` and between `$$` and `$$$`. Validate the JSON; on failure retry once, then return an empty list.
4. With a trip budget: per day per person = budget / trip days / member count, converted to USD with the server's daily FX table (ECB reference rates, refreshed once a day); below the lower edge = LOW, above the upper edge = HIGH, else MEDIUM.
5. Store for 30 days. Warm the cache in the background when a trip is created or its destination or start month changes.

**Search** (`POST /recommendations`)

1. Load the trip, check role (EDITOR or higher) and that it has not ended (today in WIB after `end_date` → 422).
2. Check the daily quota (unchanged).
3. Cache key = normalized destination + focus area + month + sorted core interests + sorted extra interests + price level + style.
4. Prompt inputs: destination, focus area when given ("only places in or near Ubud"), trip dates and season, price level as words relative to local prices, core and extra interests, style, notes, liked and skipped places.
5. Validate every place with Places Text Search (IDs Only, free), biased to the destination or focus area; drop places not found.
6. Price filter: when Places returns a `priceLevel`, drop places more than one step away from the chosen level; places without a price stay.
7. Return 10–15 cards; prefetch rules and feedback are unchanged.

**Cost.** A suggestions miss is one short LLM call (well under $0.001 at current DeepSeek Flash prices) and is shared by every user going to the same place in the same month. The FX table is one free request a day. Search cost does not change.

## Screens

All boards are in the [RANSEL — IRIS canvas](https://claude.ai/artifact/46hiHks1W4WkYjVdhqmtJp), rows "Rekomendasi AI" (mobile, 390 wide) and "Web · Rekomendasi AI" (1280 wide).

| Board | Platform | Code name | Shows |
| --- | --- | --- | --- |
| PilihTripJelajah | mobile | `ExploreTripPickerScreen` | step 1 |
| JelajahLanjut | mobile | `ExploreTripPickerScreen` (resume card) | step 1 with an unfinished session |
| PreferensiAI | mobile | `ExplorePreferencesScreen` (existing trip) | Bali, price level preselected from the budget, Khas Bali |
| PreferensiAITokyo | mobile | `ExplorePreferencesScreen` (existing trip) | Tokyo without a budget, seasonal Khas Tokyo (Sakura) |
| JelajahTripBaru | mobile | `ExplorePreferencesScreen` (new trip) | step 2b, Khas Lombok loaded |
| JelajahTripBaruMemuat | mobile | `ExplorePreferencesScreen` (new trip) | destination interests loading |
| JelajahTamu | mobile | `ExploreTripPickerScreen` (guest) | sign-in prompt |
| WebJelajah, WebJelajahLanjut | web | `ExploreTripPickerScreen` | step 1, without and with a resume card |
| WebJelajahPreferensi | web | `ExplorePreferencesScreen` (existing trip) | step 2a |
| WebJelajahTripBaru | web | `ExplorePreferencesScreen` (new trip) | step 2b |

Loading after the search uses `CaveCartLoading` (the capybara in a mine cart); the swipe and result boards are unchanged.

## Tasks

Phase 5 work for both agents; `shared` changes land first in their own commit.

**Contract (`shared`, one commit, logged in `CONTRACT_CHANGES.md`)**

- [ ] `PriceLevel { LOW, MEDIUM, HIGH }` replaces `BudgetLevel`; `Interest` drops BEACH and adds RELAX.
- [ ] `RecommendationRequest`: `priceLevel`, `focusArea`, `extraInterests`; no destination or dates.
- [ ] `ExploreSuggestionsResponse` + route `EXPLORE_SUGGESTIONS = "/explore/suggestions"`; `ErrorCode.RATE_LIMITED`.

**Backend**

- [ ] Suggestions endpoint with cache table `explore_suggestions_cache` (destination, month, language, interests JSONB, price guide, expires\_at) in V4, LLM call, retry once, empty list on failure.
- [ ] Daily FX table from ECB reference rates; price level from trip budget.
- [ ] Cache warm-up when a trip is created or its destination or start month changes.
- [ ] Search: role and trip-ended checks, new cache key, prompt with focus area and extra interests, Places price filter.
- [ ] Soft limit 60 suggestions per user per hour → 429 `RATE_LIMITED`.

**Frontend**

- [ ] Trip picker, existing-trip form, new-trip form and shortcuts as in User flow.
- [ ] Price level with preselect note; core interests; "Khas \<destination>" box with skeleton, 8 s timeout and hide-on-failure.
- [ ] Suggestions request on trip pick (2a) and after 600 ms of no typing or on blur (2b); reload when the start month changes; keep ticked chips that still exist.
- [ ] Search button enabled with at least one interest; never waits for suggestions.

**Acceptance checks**

- [ ] Tokyo with `$`: every card is cheap by Tokyo standards, none above Places price level 2.
- [ ] A Himalaya trip shows no beach chip; Bali, Lombok and other coastal places do.
- [ ] Tokyo in late March offers a cherry blossom chip; Tokyo in November does not.
- [ ] The same destination and month for a second user returns suggestions from the cache with no LLM call.
- [ ] Suggestions never change the daily search count.
- [ ] A finished trip or a VIEWER cannot search (422 and 403).
