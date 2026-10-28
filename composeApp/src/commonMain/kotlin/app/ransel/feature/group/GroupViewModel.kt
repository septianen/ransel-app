package app.ransel.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.model.SettlementStatus
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

    /** The trip has no dates yet ("Tanggal belum pasti"). */
    data object NoDates : Countdown
}

fun countdownFor(trip: TripDto, today: LocalDate): Countdown = when (trip.phase(today)) {
    TripPhase.UPCOMING -> trip.daysUntilStart(today)?.let { Countdown.Upcoming(it) } ?: Countdown.NoDates
    TripPhase.ONGOING -> Countdown.Ongoing(trip.dayNumber(today), trip.totalDays())
    TripPhase.FINISHED -> Countdown.Finished
}

data class GroupUiState(
    val loading: Boolean = true,
    val members: List<TripMemberDto> = emptyList(),
    val polls: List<PollDto> = emptyList(),
    /** Documents of the trip, for "Dokumen trip 3 ›"; null until known (offline, not loaded). */
    val documentCount: Int? = null,
    val failure: ApiFailure? = null,
    /** "Perlu tindakanmu" money: my open transfers and claims that wait for me (shared trips only). */
    val owes: List<TransferDto> = emptyList(),
    val claimsToConfirm: List<SettlementDto> = emptyList(),
    val currency: String = "IDR",
) {
    /** Only the user is in the trip (as far as the server said): "Ringkasan" leaves out the group parts. */
    val solo: Boolean get() = !loading && failure == null && members.size <= 1

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

    /** My member in this trip (the id the apps compute offline); null for a guest. */
    val myMemberId: String? get() = session.user?.id?.let { memberIdFor(tripId, it) }

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
            // polls are a convenience on this tab: if they fail to load the rest still shows
            (api.polls(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(polls = r.value) } }
            (api.documents(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(documentCount = r.value.items.size) } }
            // so is money: what I owe and what waits for my confirmation
            if (_state.value.members.size > 1) {
                val me = myMemberId
                val claims = (api.settlements(tripId) as? ApiResult.Success)?.value.orEmpty()
                    .filter { it.status == SettlementStatus.CLAIMED_PAID }
                val claimed = claims.map { it.fromMemberId to it.toMemberId }.toSet()
                (api.balances(tripId) as? ApiResult.Success)?.value?.let { b ->
                    _state.update { state ->
                        state.copy(
                            owes = b.transfers.filter { it.fromMemberId == me && (it.fromMemberId to it.toMemberId) !in claimed },
                            claimsToConfirm = claims.filter { it.toMemberId == me },
                            currency = b.baseCurrency,
                        )
                    }
                }
            }
        }
    }
}
