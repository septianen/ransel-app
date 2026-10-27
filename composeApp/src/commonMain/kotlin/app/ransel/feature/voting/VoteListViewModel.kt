package app.ransel.feature.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.data.voting.VotingStore
import app.ransel.data.voting.actionRounds
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.roundState
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.shared.model.Role
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** The rounds of one trip in three groups; every group is newest first. */
data class VoteListUiState(
    /** Rounds the user has to act on: vote, or (OWNER) decide. */
    val action: List<RoundSummaryDto> = emptyList(),
    /** Open rounds the user already voted on, and closed rounds waiting for the owner. */
    val waiting: List<RoundSummaryDto> = emptyList(),
    val done: List<RoundSummaryDto> = emptyList(),
    val isOwner: Boolean = false,
    val now: Instant = Clock.System.now(),
) {
    val isEmpty: Boolean get() = action.isEmpty() && waiting.isEmpty() && done.isEmpty()
}

/** All voting rounds of a trip (docs/specs/voting-spec.md), opened from the Group tab. */
class VoteListViewModel(
    private val tripId: String,
    private val voting: VotingStore,
    private val api: RanselApi,
    trips: TripRepository,
) : ViewModel() {
    val state: StateFlow<VoteListUiState> = combine(voting.rounds, trips.observeTrip(tripId)) { all, trip ->
        val now = Clock.System.now()
        val isOwner = trip?.myRole == Role.OWNER
        val rounds = all[tripId].orEmpty()
        val action = rounds.actionRounds(isOwner, now)
        val rest = rounds - action.toSet()
        VoteListUiState(
            action = action,
            waiting = rest.filter { roundState(now, it.closesAt, it.decidedAt) != RoundState.DECIDED },
            done = rest.filter { roundState(now, it.closesAt, it.decidedAt) == RoundState.DECIDED },
            isOwner = isOwner,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoteListUiState())

    init {
        viewModelScope.launch { voting.refresh(api, tripId) }
    }
}
