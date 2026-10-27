package app.ransel.data.voting

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.RoundSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The voting rounds of the trips the user looked at (docs/specs/voting-spec.md). Server data only, like members:
 * the trip list dot and the Group tab read it, the vote screen refreshes it after every change.
 */
class VotingStore {
    private val _rounds = MutableStateFlow<Map<String, List<RoundSummaryDto>>>(emptyMap())

    /** Rounds by trip id, newest first. A trip that was never loaded is missing. */
    val rounds: StateFlow<Map<String, List<RoundSummaryDto>>> = _rounds.asStateFlow()

    suspend fun refresh(api: RanselApi, tripId: String) {
        when (val result = api.rounds(tripId)) {
            is ApiResult.Success -> _rounds.update { it + (tripId to result.value) }
            // Not a member any more (or the trip is gone): nothing to show.
            is ApiResult.Failure -> if (result.error.code == app.ransel.shared.api.ErrorCode.TRIP_NOT_FOUND) _rounds.update { it - tripId }
        }
    }

    fun clear() {
        _rounds.value = emptyMap()
    }
}

/** A round the user should act on: voting is open and they still have cards without a vote. */
fun RoundSummaryDto.needsMyVote(now: Instant = Clock.System.now()): Boolean =
    roundState(now, closesAt, decidedAt) == RoundState.OPEN && myVoteCount < placeCount

/**
 * The rounds the user has to act on, newest first: vote (open, cards left) or, for the OWNER, decide (voting closed,
 * not decided yet). The Group tab banner shows the first one; all rounds are in its "Voting" list.
 */
fun List<RoundSummaryDto>.actionRounds(isOwner: Boolean, now: Instant = Clock.System.now()): List<RoundSummaryDto> =
    filter { round ->
        round.needsMyVote(now) || (isOwner && roundState(now, round.closesAt, round.decidedAt) == RoundState.CLOSED)
    }
