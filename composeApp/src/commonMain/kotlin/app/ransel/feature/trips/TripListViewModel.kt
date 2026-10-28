package app.ransel.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.TripRepository
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.RanselApi
import app.ransel.data.voting.VotingStore
import app.ransel.data.voting.needsMyVote
import app.ransel.shared.domain.hoursLeft
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import app.ransel.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** [votingHoursLeft]: set while the user still has cards to vote on in a round of this trip (the magenta dot). */
data class TripCardData(val trip: TripDto, val summary: TripSummaryDto, val votingHoursLeft: Int? = null)

sealed interface TripListUiState {
    data object Loading : TripListUiState
    data object Empty : TripListUiState
    data class Content(
        val ongoing: List<TripCardData>,
        val upcoming: List<TripCardData>,
        val finished: List<TripCardData>,
        val today: LocalDate,
        /** "Cari trip": the trimmed query the groups are filtered by; empty = no search. */
        val query: String = "",
    ) : TripListUiState {
        val isSearching: Boolean get() = query.isNotEmpty()
        /** Searching found nothing in any group. */
        val noResults: Boolean get() = isSearching && ongoing.isEmpty() && upcoming.isEmpty() && finished.isEmpty()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripListViewModel(
    trips: TripRepository,
    private val sync: SyncStatus,
    private val voting: VotingStore,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    /** What is typed in the search field (untrimmed). */
    val query: StateFlow<String> = _query

    fun onQuery(value: String) {
        _query.value = value
    }

    fun clearSearch() {
        _query.value = ""
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Pull-to-refresh. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                sync.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }

    init {
        // Voting rounds live on the server: ask for the trips that are not over, once per visit (signed in only).
        viewModelScope.launch {
            val list = trips.observeTrips().first()
            if (session.user == null) return@launch
            val today = today()
            list.filter { it.phase(today) != TripPhase.FINISHED }.forEach { voting.refresh(api, it.id) }
        }
    }

    val state: StateFlow<TripListUiState> = combine(trips.observeTrips(), voting.rounds) { list, rounds -> list to rounds }
        .flatMapLatest { (list, rounds) ->
            if (list.isEmpty()) {
                flowOf<TripListUiState>(TripListUiState.Empty)
            } else {
                val now = Clock.System.now()
                combine(list.map { trip ->
                    val open = rounds[trip.id].orEmpty().firstOrNull { it.needsMyVote(now) }
                    trips.observeSummary(trip.id).map { TripCardData(trip, it, open?.let { r -> hoursLeft(now, r.closesAt) }) }
                }) { cards ->
                    val today = today()
                    val all = cards.toList()
                    TripListUiState.Content(
                        ongoing = all.filter { it.trip.phase(today) == TripPhase.ONGOING }.sortedBy { it.trip.endDate },
                        upcoming = all.filter { it.trip.phase(today) == TripPhase.UPCOMING }.sortedWith(compareBy(upcomingOrder) { it.trip }),
                        finished = all.filter { it.trip.phase(today) == TripPhase.FINISHED }
                            .sortedByDescending { it.trip.endDate },
                        today = today,
                    )
                }
            }
        }
        .combine(_query) { state, query -> if (state is TripListUiState.Content) state.searched(query) else state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState.Loading)
}
