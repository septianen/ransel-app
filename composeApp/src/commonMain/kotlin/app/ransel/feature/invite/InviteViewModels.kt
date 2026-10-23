package app.ransel.feature.invite

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.NoAnalytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.SessionState
import app.ransel.data.invite.InviteLinks
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.data.sync.SyncStatus
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreateInviteRequest
import app.ransel.shared.dto.InviteDto
import app.ransel.shared.dto.InvitePreviewDto
import app.ransel.shared.model.Role
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What a new invite allows (design "Berlaku 7 hari · maksimal 5 orang"). */
const val INVITE_VALID_DAYS = 7
const val INVITE_MAX_USES = 5

// ── Invite sheet (owner) ────────────────────────────────────────────────────

data class InviteSheetState(
    val role: Role = Role.EDITOR,
    val invite: InviteDto? = null,
    val loading: Boolean = true,
    val failure: ApiFailure? = null,
)

/**
 * Creates an invite when the sheet opens, and a fresh one when the owner picks the other role
 * (the previous code is revoked so no unused links stay around).
 */
class InviteSheetViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(InviteSheetState())
    val state: StateFlow<InviteSheetState> = _state.asStateFlow()

    init {
        create(Role.EDITOR)
    }

    fun selectRole(role: Role) {
        if (role == _state.value.role && _state.value.invite != null) return
        val previous = _state.value.invite
        if (previous != null) viewModelScope.launch { api.revokeInvite(tripId, previous.code) }
        create(role)
    }

    fun retry() = create(_state.value.role)

    private fun create(role: Role) {
        _state.update { it.copy(role = role, invite = null, loading = true, failure = null) }
        viewModelScope.launch {
            val request = CreateInviteRequest(
                role = role,
                maxUses = INVITE_MAX_USES,
                expiresAt = Clock.System.now() + INVITE_VALID_DAYS.days,
            )
            when (val result = api.createInvite(tripId, request)) {
                is ApiResult.Success -> {
                    analytics.event(AnalyticsEvent.InviteCreated(role))
                    _state.update { it.copy(invite = result.value, loading = false) }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }
}

// ── Join trip (invitee) ─────────────────────────────────────────────────────

data class JoinTripState(
    val loading: Boolean = true,
    val preview: InvitePreviewDto? = null,
    val failure: ApiFailure? = null,
    /** Previewing and joining need an account. */
    val signedIn: Boolean = false,
    val joining: Boolean = false,
)

sealed interface JoinTripEvent {
    /** Joined (or already a member): open this trip. */
    data class Joined(val tripId: String) : JoinTripEvent
}

class JoinTripViewModel(
    val code: String,
    private val api: RanselApi,
    private val session: SessionManager,
    private val links: InviteLinks,
    private val trips: TripRepository,
    private val sync: SyncStatus,
    private val analytics: Analytics = NoAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinTripState(signedIn = session.state.value is SessionState.SignedIn))
    val state: StateFlow<JoinTripState> = _state.asStateFlow()

    private val events = Channel<JoinTripEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect { sessionState ->
                val signedIn = sessionState is SessionState.SignedIn
                val was = _state.value.signedIn
                _state.update { it.copy(signedIn = signedIn) }
                if (signedIn && (!was || _state.value.preview == null && _state.value.failure == null)) load()
            }
        }
    }

    fun load() {
        if (!_state.value.signedIn) {
            _state.update { it.copy(loading = false) }
            return
        }
        _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            when (val result = api.invitePreview(code)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, preview = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    fun join() {
        val preview = _state.value.preview ?: return
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, failure = null) }
        viewModelScope.launch {
            when (val result = api.acceptInvite(code)) {
                is ApiResult.Success -> {
                    analytics.event(AnalyticsEvent.MemberJoined)
                    finish(result.value.id)
                }
                is ApiResult.Failure ->
                    if (result.error.code == ErrorCode.ALREADY_MEMBER) finish(preview.tripId)
                    else _state.update { it.copy(joining = false, failure = result.error) }
            }
        }
    }

    /** Mobile keeps trips in a local DB: pull once so the trip is there when its screen opens. */
    private suspend fun finish(tripId: String) {
        sync.syncNow()
        withTimeoutOrNull(8_000) { trips.observeTrip(tripId).filterNotNull().first() }
        links.clear()
        _state.update { it.copy(joining = false) }
        events.send(JoinTripEvent.Joined(tripId))
    }

    /** The user leaves without joining ("Nanti saja", close). */
    fun dismiss() = links.clear()
}
