# Itinerary Voting Spec — RANSEL

Oct 17, 2026 · @SEN · Status: **proposal, waiting for the product owner's OK before any code**

## Summary

On a trip with more than one member, an AI search becomes a **suggestion round**: the recommendations go to every member, each member swipes them within 24 hours (a swipe is a vote), and the trip OWNER sees the result and decides which places enter the itinerary. A solo trip (one member) keeps today's behaviour: swipe right saves the place directly.

Decisions agreed on 17 Oct 2026:

- Every member votes, including VIEWER. Only the OWNER decides what enters the itinerary.
- A vote is the same gesture as today's swipe: right = like ("Suka"), left = skip ("Lewati"). Undo is allowed while voting is open.
- One AI search costs the requester one quota unit (10 per day); votes cost nothing for anyone.
- Voting stays open for **24 hours** from the moment the round is created. The OWNER may decide at any time, also before the 24 hours end or after they end.
- Notifications (push or email) are **later**. For now members see an in-app banner and a dot.
- Data lives in new server tables and a server-only API (like members and invites). It needs an additive contract change in `shared/`.

This spec changes the product behaviour "Explore always saves into a trip" for multi-member trips, so the PRD ("Explore AI") must be updated when this is approved.

## Terms

- **Round** (`SuggestionRound`): one AI search result set for one trip, with a creator, a 24 hour voting window, and its places.
- **Vote**: one member's LIKE or SKIP on one place of one round. One vote per member per place; changing it replaces it.
- **Decide**: the OWNER's action that puts chosen places into the itinerary and closes the round for good.

## User flow

1. **Search.** An OWNER or EDITOR searches as today (`ExplorePreferencesScreen`). If the trip has two or more members, the button note says "Hasil dikirim ke <n> anggota trip, owner yang memutuskan." and the search creates a round.
2. **Requester votes first.** After loading, the requester lands in `SwipeScreen` in **vote mode**: same cards and gestures; swiping records votes instead of creating itinerary items. The header shows "Voting untuk <trip>", the sender and the time left.
3. **Members get a banner.** Trip screens show a banner "Rina meminta pilihanmu: 12 tempat di Bali · sisa 23 jam" and the trip list gets a magenta dot. Tapping it opens the same swipe screen in vote mode. Opening a card's detail uses `PlaceDetailScreen` (see the place detail design), whose bottom buttons read Lewati / Suka.
4. **Finished voting.** A member who has voted on every card sees "Suaramu sudah masuk" with a summary of their own likes. Counts of other people's votes stay hidden until voting closes (avoids herd voting).
5. **Owner decides** (`VoteResultScreen`). The OWNER opens the round from the banner at any time and sees the places sorted by likes, with who voted what and who has not voted yet. They tick places and tap "Masukkan <n> ke itinerary"; the places appear in "Belum dijadwalkan" exactly like saved AI places today. The round is then closed.
6. **After the window.** After 24 hours members can no longer vote; they see "Voting ditutup" and, once decided, which places the owner chose. If the OWNER never decides, the round stays visible for 7 days and then expires; nothing enters the itinerary by itself.

Not handled in this version: a tie is decided by the OWNER by hand; there is no automatic majority rule.

## Rules

| Topic | Rule |
| --- | --- |
| Who can start a round | Same as search today: OWNER or EDITOR, trip not ended. |
| Who can vote | Every current member, any role. The requester is a voter too. |
| Who can decide | OWNER only. |
| Solo trip | One member: no round; swipe saves directly as today. |
| Open rounds | At most 3 open (not decided, not expired) rounds per trip; a 4th search returns 409 `ROUND_LIMIT`. |
| Extra batches | `newSearch = false` batches for the same session append their cards to the same round while it is open, and do not use quota. |
| Exclusions | A place that appears in any round of the trip is excluded from later searches for that trip (same effect as the existing liked/skipped exclusion). |
| Member leaves or is removed | Their votes stop counting (the tally only counts current members). |
| Member joins mid-round | They can vote on the open round and count in "who has not voted". |
| Votes visible to | The OWNER sees names and votes live. Members see only their own votes until the window closes, then the counts (not the names). |
| Quota | 1 per search for the requester. Votes, viewing and deciding use none. |
| Detail pages | The detail text (about, tips, hours) is part of each card, so every voter sees the same complete page with no extra request. The round stores a snapshot of each card (including that text) so voting does not depend on the requester's device. |

## Data model (new migration after the place detail cache one; V1–V5 stay untouched)

`suggestion_rounds`: `id uuid pk`, `trip_id`, `created_by`, `created_at`, `closes_at` (created + 24 h), `decided_at null`, `decided_by null`, `expires_at` (created + 7 d).

`suggestion_round_places`: `round_id`, `place_id`, `category`, `sort_order`, `snapshot jsonb` (the `RecommendationItem`: name, reason, price level, duration, lat/lng, address, photoUrl, and the detail text: about, highlights, opening hours, best time, estimated cost), PK `(round_id, place_id)`.

`suggestion_votes`: `round_id`, `place_id`, `user_id`, `vote` (`LIKE` or `SKIP`, CHECK), `created_at`, `updated_at`, PK `(round_id, place_id, user_id)`. Writes go through the server only.

No Room tables: rounds and votes need the network, like members and invites. The itinerary items created by "Masukkan ke itinerary" are created by the OWNER's client with `source = AI`, `dayDate = null`, `placeId` and travel through normal sync, which is how swipe-right works today. The server records the decision in a separate call so it can close the round.

## API contract (additive, `shared/dto/VotingDtos.kt`, `api/ApiRoutes.kt`, `ErrorCode`)

| Method | Path | Body | Response | Notes |
| --- | --- | --- | --- | --- |
| POST | `/recommendations` | `RecommendationRequest` | `RecommendationResponse` with new optional `roundId` | Set only when the trip has two or more members. Request gains optional `roundId` for extra batches. |
| GET | `/trips/{id}/rounds` | none | `List<RoundSummaryDto>` | Open and recent rounds for the banner; any member. |
| GET | `/trips/{id}/rounds/{roundId}` | none | `RoundDto` | Cards, my votes, `closesAt`, `decidedAt`; OWNER also gets `tally` with voters; members get counts only after `closesAt`. |
| PUT | `/trips/{id}/rounds/{roundId}/votes/{placeId}` | `{ "vote": "LIKE" or "SKIP" }` | 204 | Idempotent. 409 `VOTING_CLOSED` after `closesAt`. |
| DELETE | `/trips/{id}/rounds/{roundId}/votes/{placeId}` | none | 204 | Undo. Same closing rule. |
| POST | `/trips/{id}/rounds/{roundId}/decide` | `{ "placeIds": [..] }` | 204 | OWNER only. Closes the round. 409 `ROUND_ALREADY_DECIDED`. |

New `ErrorCode` values: `ROUND_NOT_FOUND`, `VOTING_CLOSED`, `ROUND_ALREADY_DECIDED`, `ROUND_LIMIT`. New enum `Vote { LIKE, SKIP }` serialized by name.

Contract rules: land in its own commit with a line in `shared/CONTRACT_CHANGES.md`, keep `ApiRoutes`, the DTO KDoc and TRD "API" in step. All changes are additive, so no breaking-change approval is needed beyond this spec.

## Screens (designs in `docs/design/screens/`)

| File | Code name | Shows |
| --- | --- | --- |
| `Voting.html` | `SwipeScreen` (vote mode) | Header with sender and time left, "Kamu menyukai 2", Lewati / Suka buttons |
| `VotingBanner.html` | `VoteBanner` on `ItineraryScreen` | Banner "Rina meminta pilihanmu" above the Unscheduled box |
| `VotingMenunggu.html` | `SwipeScreen` (voted all) | "Suaramu sudah masuk", own summary, counts hidden until closed |
| `VotingHasil.html` | `VoteResultScreen` (owner) | Places by likes with voters, who has not voted, select and "Masukkan ke itinerary" |
| `VotingDitutup.html` | `VoteResultScreen` (member, closed) | Voting closed, counts, what the owner chose |

Web boards (canvas only): `WebDetailTempat`, `WebGelapDetailTempat`, `WebVoting`, `WebVotingBanner`, `WebVotingMenunggu`, `WebVotingHasil`, `WebVotingDitutup`.

Explore preferences screen gets one note line for multi-member trips (see `PreferensiAI.html` area): "Hasil dikirim ke <n> anggota trip, owner yang memutuskan."

## Tasks

**Contract (`shared`, own commit)**

- [ ] `Vote` enum, `VotingDtos.kt` (`RoundSummaryDto`, `RoundDto`, `RoundPlaceDto`, `VoteRequest`, `DecideRequest`, `TallyDto`), routes, error codes, `RecommendationResponse.roundId`, `RecommendationRequest.roundId`.
- [ ] Unit tests for any shared helper (for example `votesNeeded`/time left) in `shared/src/commonTest`.

**Backend**

- [ ] Migration and Exposed tables; `RoundService` (create from a search, list, get with role-aware tally, vote, undo, decide, expiry job).
- [ ] `RecommendationService`: create or append to a round when the trip has two or more members; exclude round places from new searches.
- [ ] Tests: solo trip unchanged, vote after 24 h → 409, non-owner decide → 403, ex-member votes do not count, members never see names, round limit.

**Frontend**

- [ ] Vote mode in `SwipeViewModel`/`SwipeScreen` (same gestures, calls `PUT votes`, no itinerary item).
- [ ] `VoteBanner`, dot on the trip list, `VoteResultScreen`, waiting and closed states, strings (Indonesian first) and previews.
- [ ] Owner decide action creates the itinerary items through the existing repository, then calls `decide`.

**Docs**

- [ ] On approval: PRD "Explore AI" paragraph, TRD "API" rows and data model, `docs/design/README.md` rows, task lists.

## Open items for the product owner

- Names versus counts for members after closing (this spec shows counts only; change if you want names).
- Whether VIEWER should also see the owner's final choice (this spec: yes, everyone sees it).
- Notifications (push or email) are intentionally out of scope; they depend on the email/push provider decision.
- The 3 open rounds and 7 day expiry numbers are proposals.
