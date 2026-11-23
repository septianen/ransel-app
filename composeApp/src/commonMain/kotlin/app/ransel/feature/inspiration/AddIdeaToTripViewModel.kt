package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import app.ransel.data.inspiration.AddIdeaToTripRequests
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.itinerary.sortOrderForStart
import app.ransel.feature.trips.canAddIdeaToExistingTrip
import app.ransel.feature.trips.canReceiveIdea
import app.ransel.feature.trips.tripsThatCanReceiveIdea
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.TripDto
import app.ransel.util.newId
import app.ransel.util.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddIdeaToTripState(
    val trips: List<TripDto> = emptyList(),
    /** At least one editable unfinished trip: the idea sheet shows "Add to existing trip". */
    val canAdd: Boolean = false,
    val adding: Boolean = false,
)

sealed interface AddIdeaToTripEvent {
    data class Added(val tripId: String, val tripTitle: String) : AddIdeaToTripEvent
    data object Failed : AddIdeaToTripEvent
}

class AddIdeaToTripViewModel(
    private val trips: TripRepository,
    private val itinerary: ItineraryRepository,
    private val requests: AddIdeaToTripRequests,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val adding = MutableStateFlow(false)

    val state: StateFlow<AddIdeaToTripState> = combine(trips.observeTrips(), adding) { all, busy ->
        val eligible = tripsThatCanReceiveIdea(all, today())
        AddIdeaToTripState(trips = eligible, canAdd = canAddIdeaToExistingTrip(all, today()), adding = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddIdeaToTripState())

    private val events = Channel<AddIdeaToTripEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val card: InspirationCardDto? get() = requests.card

    fun addTo(tripId: String) {
        val card = requests.card ?: return
        if (adding.value) return
        adding.value = true
        viewModelScope.launch {
            val trip = trips.observeTrip(tripId).first()
            if (trip == null || !trip.canReceiveIdea(today())) {
                adding.update { false }
                events.send(AddIdeaToTripEvent.Failed)
                return@launch
            }
            val ok = runCatching {
                val unscheduled = itinerary.observe(tripId).first().filter { it.dayDate == null }
                itinerary.save(tripId, unscheduledItemFromIdea(card, newId(), sortOrderForStart(unscheduled, null)))
            }.isSuccess
            adding.update { false }
            if (ok) {
                analytics.event(AnalyticsEvent.ActivityAdded(app.ransel.shared.model.Category.ATTRACTION, scheduled = false))
                events.send(AddIdeaToTripEvent.Added(tripId, trip.title))
            } else {
                events.send(AddIdeaToTripEvent.Failed)
            }
        }
    }
}
