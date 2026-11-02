package app.ransel.feature.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trip.zone
import app.ransel.shared.domain.TripRecap
import app.ransel.shared.domain.tripRecap
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecapUiState(
    val trip: TripDto? = null,
    val recap: TripRecap? = null,
    /** Members for the avatar row (server; empty offline or for guests). */
    val members: List<TripMemberDto> = emptyList(),
    /** Open transfers ("Supaya lunas"); null = not known (offline, guest). 0 = "Semua patungan lunas". */
    val openTransfers: Int? = null,
)

/**
 * The trip recap (`RecapScreen`, and the card on Home; designs "RekapTrip", "WebRekapTrip", "BerandaRekap"). The numbers come from the device's own itinerary
 * and expenses (`domain/Recap.kt`), so the recap works offline; whether everything is settled comes from the server.
 */
class RecapViewModel(
    private val tripId: String,
    trips: TripRepository,
    itinerary: ItineraryRepository,
    expenses: ExpenseRepository,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(trips.observeTrip(tripId), itinerary.observe(tripId), expenses.observe(tripId)) { trip, items, costs ->
                // a trip without dates has no recap (the entry points are hidden too)
                val start = trip?.startDate
                val end = trip?.endDate
                trip to if (trip != null && start != null && end != null) tripRecap(start, end, trip.baseCurrency, items, costs, trip.zone()) else null
            }.collect { (trip, recap) -> _state.update { it.copy(trip = trip, recap = recap) } }
        }
        refresh()
    }

    /** Members and open transfers (server data; the recap itself comes from the device). */
    fun refresh() {
        if (session.user == null) return
        viewModelScope.launch {
            (api.members(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(members = r.value) } }
            (api.balances(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(openTransfers = r.value.transfers.size) } }
        }
    }
}
