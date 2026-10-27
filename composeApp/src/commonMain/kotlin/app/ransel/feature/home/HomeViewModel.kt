package app.ransel.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trips.pickFeaturedTrip
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.model.Role
import app.ransel.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        /** More than one member: the card opens the Group tab instead of the itinerary. */
        val isGroup: Boolean = false,
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    trips: TripRepository,
    itinerary: ItineraryRepository,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    /** Members per trip from the server; the phone's own summary does not know them (members are server-only). */
    private val memberCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    val state: StateFlow<HomeUiState> = trips.observeTrips()
        .flatMapLatest { list ->
            val today = today()
            val featured = pickFeaturedTrip(list, today)
            if (featured == null) {
                flowOf<HomeUiState>(HomeUiState.Empty)
            } else {
                combine(trips.observeSummary(featured.id), itinerary.observe(featured.id), memberCounts) { summary, items, counts ->
                    HomeUiState.Content(
                        trip = featured,
                        summary = summary,
                        firstDayCount = items.count { it.dayDate == featured.startDate },
                        today = today,
                        // a trip someone else owns is shared by definition; otherwise the summary (web) or the server count
                        isGroup = featured.myRole != Role.OWNER || maxOf(summary.memberCount, counts[featured.id] ?: 0) > 1,
                    )
                }.onStart { countMembers(featured.id) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private fun countMembers(tripId: String) {
        if (session.user == null || tripId in memberCounts.value) return
        viewModelScope.launch {
            (api.members(tripId) as? ApiResult.Success)?.let { r -> memberCounts.update { it + (tripId to r.value.size) } }
        }
    }

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
