package app.ransel.shared.dto

import app.ransel.shared.model.Category
import app.ransel.shared.model.Vote
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Itinerary voting (docs/specs/voting-spec.md). Server data only, like members and invites: no Room tables.
 *
 * On a trip with two or more members, POST /recommendations creates a suggestion round and returns its id in
 * `RecommendationResponse.roundId`. Every member votes LIKE or SKIP per card within 24 hours (votes cost nothing);
 * the OWNER decides which places enter the itinerary. Deciding: the OWNER's client first creates the itinerary items
 * (dayDate = null, source = AI, placeId) through its normal path, exactly like swipe-right today, then calls
 * POST .../decide, which closes the round.
 *
 * Visibility: the OWNER sees names and votes live ([RoundDto.tally] with likedBy, [RoundDto.voters]); members see only
 * their own votes until `closesAt`, then counts without names. Expired rounds (7 days, never decided) are not listed
 * and answer 404 ROUND_NOT_FOUND.
 */

/** GET /trips/{id}/rounds: open and recent rounds, newest first, for the banner and the trip-list dot. Any member. */
@Serializable
data class RoundSummaryDto(
    val id: String,
    val tripId: String,
    val createdByUserId: String,
    val createdByName: String,
    val createdAt: Timestamp,
    /** Voting ends here (created + 24 h). */
    val closesAt: Timestamp,
    val decidedAt: Timestamp? = null,
    val placeCount: Int,
    /** Cards the caller has voted on; `myVoteCount < placeCount` while open = the caller still has to vote. */
    val myVoteCount: Int,
    /** Places the OWNER put into the itinerary; null until decided. */
    val chosenCount: Int? = null,
)

/** GET /trips/{id}/rounds/{roundId}. Any member; what is filled depends on the role and the time (see file KDoc). */
@Serializable
data class RoundDto(
    val id: String,
    val tripId: String,
    val createdByUserId: String,
    val createdByName: String,
    val createdAt: Timestamp,
    val closesAt: Timestamp,
    val decidedAt: Timestamp? = null,
    /** After this the round is gone if nobody decided (created + 7 days). */
    val expiresAt: Timestamp,
    /** In card order (the order of the search result). */
    val places: List<RoundPlaceDto>,
    /** The caller's own votes by placeId. */
    val myVotes: Map<String, Vote> = emptyMap(),
    /** Current members of the trip (the denominator of "3 dari 4 suka"). */
    val memberCount: Int,
    /** Current members who voted on every card. Null when [tally] is hidden. */
    val finishedCount: Int? = null,
    /** OWNER: always. Other members: only after [closesAt], and without names. Only current members' votes count. */
    val tally: List<TallyDto>? = null,
    /** OWNER only: every current member and how far they got. */
    val voters: List<VoterDto>? = null,
    /** Filled once decided. */
    val chosenPlaceIds: List<String> = emptyList(),
)

/** One card of a round: the snapshot of the search result, so every voter sees the same text with no extra request. */
@Serializable
data class RoundPlaceDto(
    val category: Category,
    val item: RecommendationItem,
)

@Serializable
data class TallyDto(
    val placeId: String,
    val likes: Int,
    val skips: Int,
    /** userIds of the members who liked it. OWNER only; empty for everyone else. */
    val likedBy: List<String> = emptyList(),
)

@Serializable
data class VoterDto(
    val userId: String,
    val displayName: String,
    /** Cards this member has voted on. */
    val votedCount: Int,
)

/**
 * PUT /trips/{id}/rounds/{roundId}/votes/{placeId} → 204. Idempotent; a new vote replaces the old one.
 * DELETE on the same path → 204 = undo. Both: 409 VOTING_CLOSED after `closesAt` or once decided,
 * 404 ROUND_NOT_FOUND for an unknown/expired round or a placeId that is not in the round.
 */
@Serializable
data class VoteRequest(
    val vote: Vote,
)

/**
 * POST /trips/{id}/rounds/{roundId}/decide → 204. OWNER only (403 FORBIDDEN otherwise), any time before expiry.
 * [placeIds] may be empty (close without choosing). 409 ROUND_ALREADY_DECIDED; 422 VALIDATION_FAILED with
 * details.placeIds = NOT_IN_ROUND.
 */
@Serializable
data class DecideRequest(
    val placeIds: List<String>,
)
