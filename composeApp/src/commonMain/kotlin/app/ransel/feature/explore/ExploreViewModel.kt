package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trips.pickFeaturedTrip
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.BudgetLevel
import app.ransel.shared.model.Interest
import app.ransel.shared.model.TravelStyle
import app.ransel.util.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user is typing; the destination follows the chosen trip until they edit it. */
private data class FormInput(
    val tripId: String? = null,
    val destination: String? = null,
    val budget: BudgetLevel = BudgetLevel.SEDANG,
    val interests: List<Interest> = emptyList(),
    val style: TravelStyle = TravelStyle.SANTAI,
    val notes: String = "",
)

data class ExploreUiState(
    val loading: Boolean = true,
    /** Explore needs an account (mobile guests): shows the "Masuk dulu" state. */
    val signedIn: Boolean = false,
    val trips: List<TripDto> = emptyList(),
    val trip: TripDto? = null,
    val destination: String = "",
    val budget: BudgetLevel = BudgetLevel.SEDANG,
    val interests: List<Interest> = emptyList(),
    val style: TravelStyle = TravelStyle.SANTAI,
    val notes: String = "",
    /** An unfinished search: shown as the "Lanjutkan" card. */
    val resume: ExploreSession? = null,
) {
    val canSearch: Boolean get() = trip != null && destination.isNotBlank() && interests.isNotEmpty()
}

class ExploreViewModel(
    trips: TripRepository,
    sessionManager: SessionManager,
    private val store: ExploreSessionStore,
) : ViewModel() {
    private val input = MutableStateFlow(FormInput(tripId = store.takePreferredTrip()))

    val state: StateFlow<ExploreUiState> = combine(
        trips.observeTrips(), sessionManager.state, store.session, input,
    ) { list, session, resume, form ->
        val chosen = list.firstOrNull { it.id == form.tripId } ?: pickFeaturedTrip(list, today()) ?: list.firstOrNull()
        ExploreUiState(
            loading = session is SessionState.Loading,
            signedIn = session is SessionState.SignedIn,
            trips = list,
            trip = chosen,
            destination = form.destination ?: chosen?.destination.orEmpty(),
            budget = form.budget,
            interests = form.interests,
            style = form.style,
            notes = form.notes,
            resume = resume?.takeIf { !it.finished },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    fun selectTrip(trip: TripDto) = input.update { it.copy(tripId = trip.id, destination = null) }
    fun setDestination(value: String) = input.update { it.copy(destination = value) }
    fun setBudget(level: BudgetLevel) = input.update { it.copy(budget = level) }
    fun setStyle(style: TravelStyle) = input.update { it.copy(style = style) }
    fun setNotes(value: String) = input.update { it.copy(notes = value.take(300)) }
    fun toggleInterest(interest: Interest) = input.update {
        it.copy(interests = if (interest in it.interests) it.interests - interest else it.interests + interest)
    }

    /** Stores a fresh session (no cards yet); the swipe screen makes the request. [onReady] navigates there. */
    fun start(onReady: () -> Unit) {
        val s = state.value
        val trip = s.trip ?: return
        if (!s.canSearch) return
        viewModelScope.launch {
            store.save(
                ExploreSession(
                    ExploreForm(
                        tripId = trip.id, tripTitle = trip.title, destination = s.destination.trim(),
                        startDate = trip.startDate, endDate = trip.endDate, budget = s.budget,
                        interests = s.interests, style = s.style, notes = s.notes,
                    ),
                ),
            )
            onReady()
        }
    }
}
