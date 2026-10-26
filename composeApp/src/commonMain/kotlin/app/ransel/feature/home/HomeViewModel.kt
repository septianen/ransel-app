package app.ransel.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trips.pickFeaturedTrip
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.minus
import kotlinx.datetime.LocalDate

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(
        val trip: TripDto,
        val summary: TripSummaryDto,
        /** Scheduled activities on the first day of the trip ("4 aktivitas di hari 1"). */
        val firstDayCount: Int,
        val today: LocalDate,
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    trips: TripRepository,
    itinerary: ItineraryRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = trips.observeTrips()
        .flatMapLatest { list ->
            val today = today()
            val featured = pickFeaturedTrip(list, today)
            if (featured == null) {
                flowOf<HomeUiState>(HomeUiState.Empty)
            } else {
                combine(trips.observeSummary(featured.id), itinerary.observe(featured.id)) { summary, items ->
                    HomeUiState.Content(
                        trip = featured,
                        summary = summary,
                        firstDayCount = items.count { it.dayDate == featured.startDate },
                        today = today,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Design "BerandaRekap": the trip that ended most recently, for two weeks after its last day. */
    val recapTrip: StateFlow<TripDto?> = trips.observeTrips()
        .map { list ->
            val today = today()
            list.filter { app.ransel.shared.domain.recapAvailable(it.endDate, today) && it.endDate >= today.minus(kotlinx.datetime.DatePeriod(days = RECAP_CARD_DAYS)) }
                .maxByOrNull { it.endDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        const val RECAP_CARD_DAYS = 14
    }
}
