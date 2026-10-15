package app.ransel.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.TripRepository
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

data class TripCardData(val trip: TripDto, val summary: TripSummaryDto)

sealed interface TripListUiState {
    data object Loading : TripListUiState
    data object Empty : TripListUiState
    data class Content(
        val ongoing: List<TripCardData>,
        val upcoming: List<TripCardData>,
        val finished: List<TripCardData>,
        val today: LocalDate,
    ) : TripListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripListViewModel(trips: TripRepository, private val sync: SyncStatus) : ViewModel() {
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

    val state: StateFlow<TripListUiState> = trips.observeTrips()
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf<TripListUiState>(TripListUiState.Empty)
            } else {
                combine(list.map { trip -> trips.observeSummary(trip.id).map { TripCardData(trip, it) } }) { cards ->
                    val today = today()
                    val all = cards.toList()
                    TripListUiState.Content(
                        ongoing = all.filter { it.trip.phase(today) == TripPhase.ONGOING }.sortedBy { it.trip.endDate },
                        upcoming = all.filter { it.trip.phase(today) == TripPhase.UPCOMING }.sortedBy { it.trip.startDate },
                        finished = all.filter { it.trip.phase(today) == TripPhase.FINISHED }
                            .sortedByDescending { it.trip.endDate },
                        today = today,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState.Loading)
}
