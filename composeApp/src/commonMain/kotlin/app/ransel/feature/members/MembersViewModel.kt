package app.ransel.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.feature.invite.INVITE_MAX_USES
import app.ransel.feature.invite.INVITE_VALID_DAYS
import app.ransel.shared.domain.normalizePhone
import app.ransel.shared.domain.validateMemberName
import app.ransel.shared.dto.AddMemberRequest
import app.ransel.shared.dto.CreateInviteRequest
import app.ransel.shared.dto.InviteDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.util.newId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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

/** Why the name of a new member is not accepted. */
enum class NameProblem { REQUIRED, TOO_LONG }

/** The "Tambah anggota" sheet: a person added by name, without an account. */
data class AddMemberForm(
    val name: String = "",
    val phone: String = "",
    val nameProblem: NameProblem? = null,
    val phoneInvalid: Boolean = false,
    val saving: Boolean = false,
)

/** Shown to the OWNER after someone took a name ("Kamu yang mana?"), until the screen is left. */
data class ClaimBanner(
    val guestMemberId: String,
    val guestName: String,
    val claimerName: String?,
    val undoing: Boolean = false,
)

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
    /** Owner only: invites that can still be used. */
    val invites: List<InviteDto> = emptyList(),
    val inviteSheetOpen: Boolean = false,
    val addForm: AddMemberForm? = null,
    val claimBanner: ClaimBanner? = null,
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
    /** A fresh invite link for [member], who was added by name: open WhatsApp (or the share sheet) with it. */
    data class SendLink(val member: TripMemberDto, val url: String, val tripTitle: String) : MembersEvent
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
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, members = result.value.sortedWith(memberOrder)) }
                    loadInvites()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
        }
    }

    /** Only the owner may list invites; for everyone else the call is skipped. */
    fun loadInvites() {
        if (!_state.value.isOwner) return
        viewModelScope.launch {
            when (val result = api.invites(tripId)) {
                is ApiResult.Success -> _state.update { it.copy(invites = result.value.filter(::isUsable)) }
                is ApiResult.Failure -> Unit // the list is a convenience; the screen stays usable without it
            }
        }
    }

    fun revokeInvite(invite: InviteDto) {
        _state.update { it.copy(invites = it.invites - invite) }
        viewModelScope.launch {
            val result = api.revokeInvite(tripId, invite.code)
            if (result is ApiResult.Failure) {
                events.send(MembersEvent.Problem(result.error.code))
                loadInvites()
            }
        }
    }

    fun openInviteSheet() = _state.update { it.copy(inviteSheetOpen = true) }

    fun closeInviteSheet() {
        _state.update { it.copy(inviteSheetOpen = false) }
        loadInvites()
    }

    fun changeRole(member: TripMemberDto, role: Role) {
        if (role == member.role || role == Role.OWNER) return
        if (member.isGuest) return // a person without an account is always a viewer
        change { api.updateMemberRole(tripId, member.memberId, role) }
    }

    // ── add a person by name ────────────────────────────────────────────────

    fun openAddForm() = _state.update { it.copy(addForm = AddMemberForm()) }
    fun closeAddForm() = _state.update { it.copy(addForm = null) }
    fun onAddName(value: String) = _state.update { it.copy(addForm = it.addForm?.copy(name = value, nameProblem = null)) }
    fun onAddPhone(value: String) = _state.update { it.copy(addForm = it.addForm?.copy(phone = value, phoneInvalid = false)) }

    fun submitAdd() {
        val form = _state.value.addForm ?: return
        if (form.saving) return
        val nameProblem = validateMemberName(form.name)["displayName"]?.let { NameProblem.valueOf(it) }
        val phoneInvalid = form.phone.isNotBlank() && normalizePhone(form.phone) == null
        if (nameProblem != null || phoneInvalid) {
            _state.update { it.copy(addForm = form.copy(nameProblem = nameProblem, phoneInvalid = phoneInvalid)) }
            return
        }
        _state.update { it.copy(addForm = form.copy(saving = true)) }
        viewModelScope.launch {
            val request = AddMemberRequest(newId(), form.name.trim(), form.phone.trim().ifEmpty { null })
            when (val result = api.addMember(tripId, request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(addForm = null) }
                    reloadMembers()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(addForm = form.copy(saving = false)) }
                    events.send(MembersEvent.Problem(result.error.code))
                }
            }
        }
    }

    /** "Kirim link" on a person added by name: a fresh invite, then the screen opens WhatsApp with it. */
    fun sendLink(member: TripMemberDto) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val request = CreateInviteRequest(Role.EDITOR, INVITE_MAX_USES, Clock.System.now() + INVITE_VALID_DAYS.days)
            when (val result = api.createInvite(tripId, request)) {
                is ApiResult.Success -> events.send(MembersEvent.SendLink(member, result.value.url, _state.value.tripTitle))
                is ApiResult.Failure -> events.send(MembersEvent.Problem(result.error.code))
            }
            _state.update { it.copy(busy = false) }
        }
    }

    /** "Lepas dari browser": the guest's name can be picked on another device (their new phone). */
    fun releaseGuest(member: TripMemberDto) = change { api.releaseGuestName(tripId, member.memberId) }

    // ── undo a claim (the OWNER, after the MEMBER_CLAIMED notification) ─────

    fun showClaimUndo(guestMemberId: String, guestName: String, claimerName: String?) =
        _state.update { it.copy(claimBanner = ClaimBanner(guestMemberId, guestName, claimerName)) }

    fun undoClaim() {
        val banner = _state.value.claimBanner ?: return
        if (banner.undoing) return
        _state.update { it.copy(claimBanner = banner.copy(undoing = true)) }
        viewModelScope.launch {
            when (val result = api.undoClaim(tripId, banner.guestMemberId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(claimBanner = null) }
                    reloadMembers()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(claimBanner = banner.copy(undoing = false)) }
                    events.send(MembersEvent.Problem(result.error.code))
                }
            }
        }
    }

    private suspend fun reloadMembers() {
        when (val list = api.members(tripId)) {
            is ApiResult.Success -> _state.update { it.copy(members = list.value.sortedWith(memberOrder)) }
            is ApiResult.Failure -> Unit
        }
    }

    fun ask(dialog: MembersDialog) = _state.update { it.copy(dialog = dialog) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun confirmDialog() {
        val dialog = _state.value.dialog ?: return
        _state.update { it.copy(dialog = null) }
        when (dialog) {
            is MembersDialog.Remove -> change { api.removeMember(tripId, dialog.member.memberId) }
            is MembersDialog.MakeOwner -> dialog.member.userId?.let { newOwner -> change { api.transferOwnership(tripId, newOwner) } }
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
            reloadMembers()
            _state.update { it.copy(busy = false) }
        }
    }

    private fun isUsable(invite: InviteDto): Boolean =
        invite.revokedAt == null &&
            invite.expiresAt > Clock.System.now() &&
            (invite.maxUses == null || invite.usedCount < invite.maxUses!!)

    private companion object {
        /** Owner first, then editors, then viewers; by join time within a role. */
        val memberOrder = compareByDescending<TripMemberDto> { it.role.ordinal }.thenBy { it.joinedAt }
    }
}
