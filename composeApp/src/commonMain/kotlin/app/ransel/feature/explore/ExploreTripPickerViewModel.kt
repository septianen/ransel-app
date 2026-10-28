package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.AuthRepository
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.repository.TripRepository
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.phase
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.model.Role
import app.ransel.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the trip picker: the trip and the one-line context under it. */
/** [resume]: the unfinished search of this trip, shown inside its row ("Lanjutkan"). */
data class ExplorePickerTrip(val trip: TripDto, val phase: TripPhase, val summary: TripSummaryDto?, val resume: ExploreSession? = null)

data class ExploreTripPickerState(
    val loading: Boolean = true,
    /** Explore needs an account (mobile guests): shows the sign-in state. */
    val signedIn: Boolean = false,
    /** Ongoing and upcoming trips where the user is OWNER or EDITOR, nearest first. */
    val trips: List<ExplorePickerTrip> = emptyList(),
    /** Signed in but with nothing to pick: the empty state with the "Trip baru" button. */
    val noEligibleTrip: Boolean = false,
    /** False until the sign-up email is verified: the verify gate replaces the picker (design `JelajahBelumVerifikasi`). */
    val emailVerified: Boolean = true,
    val email: String = "",
    val gate: VerifyGateState = VerifyGateState(),
)

/** The verify gate's buttons: resend the link, or check again after opening it. */
data class VerifyGateState(
    val resending: Boolean = false,
    val checking: Boolean = false,
    val message: VerifyGateMessage? = null,
)

enum class VerifyGateMessage { SENT, NOT_YET, CONNECTION, RATE_LIMITED, GENERIC }

class ExploreTripPickerViewModel(
    trips: TripRepository,
    private val sessionManager: SessionManager,
    store: ExploreSessionStore,
    private val auth: AuthRepository,
) : ViewModel() {
    private val gate = MutableStateFlow(VerifyGateState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eligible = trips.observeTrips().flatMapLatest { all ->
        val today = today()
        // The server only lets editors and owners search; finished trips cannot be searched either (422 TRIP_ENDED).
        val list = all
            .filter { it.myRole.atLeast(Role.EDITOR) && it.phase(today) != TripPhase.FINISHED }
            .sortedWith(compareBy({ it.phase(today) != TripPhase.ONGOING }, { it.startDate == null }, { if (it.phase(today) == TripPhase.ONGOING) it.endDate else it.startDate }))
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { trip -> trips.observeSummary(trip.id) }) { summaries ->
            list.mapIndexed { i, trip -> ExplorePickerTrip(trip, trip.phase(today), summaries[i]) }
        }
    }

    val state: StateFlow<ExploreTripPickerState> = combine(eligible, sessionManager.state, store.sessions, gate) { list, session, sessions, gate ->
        val user = (session as? SessionState.SignedIn)?.user
        ExploreTripPickerState(
            loading = session is SessionState.Loading,
            signedIn = session is SessionState.SignedIn,
            trips = list.map { row -> row.copy(resume = sessions[row.trip.id]?.takeIf { !it.finished }) },
            noEligibleTrip = list.isEmpty(),
            emailVerified = user?.emailVerified ?: true,
            email = user?.email.orEmpty(),
            gate = gate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreTripPickerState())

    init {
        // The link may have been opened on another device since the session was stored.
        viewModelScope.launch {
            val user = sessionManager.user
            if (user != null && !user.emailVerified) auth.refreshUser()
        }
    }

    fun resendVerification() {
        if (gate.value.resending) return
        gate.update { it.copy(resending = true, message = null) }
        viewModelScope.launch {
            val result = auth.resendVerificationEmail()
            gate.update { it.copy(resending = false, message = if (result is ApiResult.Failure) messageOf(result.error) else VerifyGateMessage.SENT) }
        }
    }

    /** "Sudah verifikasi": reload the account; the gate disappears by itself once the server says verified. */
    fun checkVerified() {
        if (gate.value.checking) return
        gate.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val result = auth.refreshUser()
            val verified = sessionManager.user?.emailVerified == true
            gate.update {
                it.copy(
                    checking = false,
                    message = when {
                        result is ApiResult.Failure -> messageOf(result.error)
                        verified -> null
                        else -> VerifyGateMessage.NOT_YET
                    },
                )
            }
        }
    }

    private fun messageOf(error: ApiFailure): VerifyGateMessage = when {
        error.isConnection -> VerifyGateMessage.CONNECTION
        error.code == ErrorCode.RATE_LIMITED -> VerifyGateMessage.RATE_LIMITED
        else -> VerifyGateMessage.GENERIC
    }
}
