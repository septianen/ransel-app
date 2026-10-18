package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.phase
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.model.Role
import app.ransel.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** One row of the trip picker: the trip and the one-line context under it. */
/** [resume]: the unfinished search of this trip, shown inside its row ("Lanjutkan"). */
data class ExplorePickerTrip(val trip: TripDto, val phase: TripPhase, val summary: TripSummaryDto?, val resume: ExploreSession? = null)

data class ExploreTripPickerState(
    val loading: Boolean = true,
    /** Explore needs an account (mobile guests): shows the sign-in state. */
    val signedIn: Boolean = false,
    /** Ongoing and upcoming trips where the user is OWNER or EDITOR, nearest first. */
    val trips: List<ExplorePickerTrip> = emptyList(),
    /** Signed in but with nothing to pick: the app goes straight to the new-trip form. */
    val noEligibleTrip: Boolean = false,
)

class ExploreTripPickerViewModel(
    trips: TripRepository,
    sessionManager: SessionManager,
    store: ExploreSessionStore,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eligible = trips.observeTrips().flatMapLatest { all ->
        val today = today()
        // The server only lets editors and owners search; finished trips cannot be searched either (422 TRIP_ENDED).
        val list = all
            .filter { it.myRole.atLeast(Role.EDITOR) && it.phase(today) != TripPhase.FINISHED }
            .sortedWith(compareBy({ it.phase(today) != TripPhase.ONGOING }, { if (it.phase(today) == TripPhase.ONGOING) it.endDate else it.startDate }))
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { trip -> trips.observeSummary(trip.id) }) { summaries ->
            list.mapIndexed { i, trip -> ExplorePickerTrip(trip, trip.phase(today), summaries[i]) }
        }
    }

    val state: StateFlow<ExploreTripPickerState> = combine(eligible, sessionManager.state, store.sessions) { list, session, sessions ->
        ExploreTripPickerState(
            loading = session is SessionState.Loading,
            signedIn = session is SessionState.SignedIn,
            trips = list.map { row -> row.copy(resume = sessions[row.trip.id]?.takeIf { !it.finished }) },
            noEligibleTrip = list.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreTripPickerState())
}
