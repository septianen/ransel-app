package app.ransel.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.TripDto
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.dayNumber
import app.ransel.feature.trips.daysUntilStart
import app.ransel.feature.trips.phase
import app.ransel.feature.trips.totalDays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Where the trip stands, for the card at the top of the Group tab. */
sealed interface Countdown {
    /** [days] until the first day (0 = today). */
    data class Upcoming(val days: Int) : Countdown
    data class Ongoing(val day: Int, val totalDays: Int) : Countdown
    data object Finished : Countdown
}

fun countdownFor(trip: TripDto, today: LocalDate): Countdown = when (trip.phase(today)) {
    TripPhase.UPCOMING -> Countdown.Upcoming(trip.daysUntilStart(today))
    TripPhase.ONGOING -> Countdown.Ongoing(trip.dayNumber(today), trip.totalDays())
    TripPhase.FINISHED -> Countdown.Finished
}

data class GroupUiState(
    val loading: Boolean = true,
    val members: List<TripMemberDto> = emptyList(),
    val failure: ApiFailure? = null,
) {
    /** People added by name, without an account. */
    val guests: List<TripMemberDto> get() = members.filter { it.isGuest }
}

/**
 * The Group tab: the trip's hub. Members live on the server, so this only loads for signed-in users; a guest sees the
 * invitation to sign in instead.
 */
class GroupViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupUiState(loading = session.user != null))
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    /** Account id of the signed-in user; null for a guest. */
    val myUserId: String? get() = session.user?.id

    init {
        load()
    }

    fun load() {
        if (session.user == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            when (val result = api.members(tripId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, members = result.value, failure = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }
}
