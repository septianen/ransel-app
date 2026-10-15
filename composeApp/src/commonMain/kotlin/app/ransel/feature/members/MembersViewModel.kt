package app.ransel.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A confirmation shown over the members screen. */
sealed interface MembersDialog {
    data class Remove(val member: TripMemberDto) : MembersDialog
    data class MakeOwner(val member: TripMemberDto) : MembersDialog
    data object Leave : MembersDialog
}

data class MembersUiState(
    val loading: Boolean = true,
    val tripTitle: String = "",
    val members: List<TripMemberDto> = emptyList(),
    val myUserId: String? = null,
    /** Load failure: shows the full-screen error state. */
    val failure: ApiFailure? = null,
    /** A change is being sent; the controls are disabled. */
    val busy: Boolean = false,
    val dialog: MembersDialog? = null,
) {
    val me: TripMemberDto? get() = members.firstOrNull { it.userId == myUserId }
    /** Unknown until the list is loaded: the safest (read-only) role. */
    val myRole: Role get() = me?.role ?: Role.VIEWER
    val isOwner: Boolean get() = myRole == Role.OWNER
}

sealed interface MembersEvent {
    /** The user left the trip: close the trip screens. */
    data object Left : MembersEvent
    /** A change was rejected or could not be sent; [code] is an `ErrorCode`. */
    data class Problem(val code: String) : MembersEvent
}

class MembersViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val session: SessionManager,
    trips: TripRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MembersUiState(myUserId = session.user?.id))
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    private val events = Channel<MembersEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            trips.observeTrip(tripId).collect { trip ->
                if (trip != null) _state.update { it.copy(tripTitle = trip.title) }
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, failure = null, myUserId = session.user?.id) }
        viewModelScope.launch {
            when (val result = api.members(tripId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, members = result.value.sortedWith(memberOrder))
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    fun changeRole(member: TripMemberDto, role: Role) {
        if (role == member.role || role == Role.OWNER) return
        change { api.updateMemberRole(tripId, member.userId, role) }
    }

    fun ask(dialog: MembersDialog) = _state.update { it.copy(dialog = dialog) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun confirmDialog() {
        val dialog = _state.value.dialog ?: return
        _state.update { it.copy(dialog = null) }
        when (dialog) {
            is MembersDialog.Remove -> change { api.removeMember(tripId, dialog.member.userId) }
            is MembersDialog.MakeOwner -> change { api.transferOwnership(tripId, dialog.member.userId) }
            MembersDialog.Leave -> viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                when (val result = api.leaveTrip(tripId)) {
                    is ApiResult.Success -> events.send(MembersEvent.Left)
                    is ApiResult.Failure -> events.send(MembersEvent.Problem(result.error.code))
                }
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Sends one change, then reloads the list so roles always come from the server. */
    private fun change(call: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = call()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> events.send(MembersEvent.Problem(result.error.code))
            }
            when (val list = api.members(tripId)) {
                is ApiResult.Success -> _state.update { it.copy(members = list.value.sortedWith(memberOrder)) }
                is ApiResult.Failure -> Unit
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private companion object {
        /** Owner first, then editors, then viewers; by join time within a role. */
        val memberOrder = compareByDescending<TripMemberDto> { it.role.ordinal }.thenBy { it.joinedAt }
    }
}
