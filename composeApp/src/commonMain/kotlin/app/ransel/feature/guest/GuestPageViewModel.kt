package app.ransel.feature.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.guestNameProblem
import app.ransel.shared.dto.GuestIdentityRequest
import app.ransel.shared.dto.GuestMemberDto
import app.ransel.shared.dto.GuestPageDto
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollVoteInput
import app.ransel.shared.dto.VotePollRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something to say once (a snackbar). */
enum class GuestNotice { NAME_TAKEN, STALE, CLOSED, SENT, FAILED }

data class GuestPageUiState(
    val loading: Boolean = true,
    val page: GuestPageDto? = null,
    /** ErrorCode of a page that could not be opened (NOT_FOUND = the link is off or wrong). */
    val failure: String? = null,
    /** "Bukan Bima? Ganti": the name list is open again. */
    val changing: Boolean = false,
    /** "Nama saya tidak ada": the field is open. */
    val writing: Boolean = false,
    val name: String = "",
    val nameError: String? = null,
    /** The written name already exists: "Itu kamu?". */
    val sameName: GuestMemberDto? = null,
    val busy: Boolean = false,
    /** Choices not sent yet, per poll. */
    val selections: Map<String, Set<String>> = emptyMap(),
    val notice: GuestNotice? = null,
    /** Signed in and a member of this trip: the poll can open in the app instead (trip id to poll id). */
    val memberPoll: Pair<String, String>? = null,
) {
    val me: GuestMemberDto? get() = page?.me
    val choosingName: Boolean get() = page != null && (me == null || changing)

    /** Members a guest can be: those without an account. */
    val pickable: List<GuestMemberDto> get() = page?.members.orEmpty().filter { !it.hasAccount }
}

/**
 * The guest page on the web (designs "WebPollingTamu", "WebPollingPenginapanTamu", "WebGabungPratinjau"): pick or
 * write a name, vote, tick the packing list. The guest token is kept per trip in this browser's storage, so the poll
 * link and the invite link of one trip are the same person.
 */
class GuestPageViewModel(
    private val code: String,
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(GuestPageUiState())
    val state: StateFlow<GuestPageUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(failure = null, loading = it.page == null) }
        viewModelScope.launch {
            val knownTrip = preferences.getString(tripKey(code))
            var token = knownTrip?.let { preferences.getString(tokenKey(it)) }
            var result = api.guestPage(code, token)
            // first time through this link, but the trip is known from another one
            if (result is ApiResult.Success && token == null) {
                preferences.getString(tokenKey(result.value.tripId))?.let { saved ->
                    token = saved
                    result = api.guestPage(code, saved)
                }
            }
            when (val r = result) {
                is ApiResult.Success -> {
                    val page = r.value
                    preferences.putString(tripKey(code), page.tripId)
                    // a released or claimed name: forget the token
                    if (page.me == null && token != null) preferences.putString(tokenKey(page.tripId), null)
                    _state.update { s ->
                        s.copy(
                            loading = false, page = page,
                            selections = page.polls.associate { p -> p.id to (s.selections[p.id] ?: p.myVotes.toSet()) },
                        )
                    }
                    checkMember(page)
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = r.error.code) }
            }
        }
    }

    /** A signed-in member gets "Buka di RANSEL" instead of being a guest. */
    private suspend fun checkMember(page: GuestPageDto) {
        if (session.user == null) return
        val pollId = page.focusPollId ?: page.polls.firstOrNull()?.id ?: return
        if (api.poll(pollId) is ApiResult.Success) _state.update { it.copy(memberPoll = page.tripId to pollId) }
    }

    fun startChanging() = _state.update { it.copy(changing = true, writing = false, sameName = null) }
    fun cancelChanging() = _state.update { it.copy(changing = false, writing = false, sameName = null, nameError = null) }
    fun startWriting() = _state.update { it.copy(writing = true, sameName = null) }
    fun onName(text: String) = _state.update { it.copy(name = text, nameError = null, sameName = null) }
    fun noticeShown() = _state.update { it.copy(notice = null) }

    fun pick(memberId: String) = identify(GuestIdentityRequest(memberId = memberId))

    fun submitName() {
        val name = _state.value.name
        guestNameProblem(name)?.let { problem -> _state.update { it.copy(nameError = problem) }; return }
        identify(GuestIdentityRequest(name = name.trim()))
    }

    private fun identify(request: GuestIdentityRequest) {
        val page = _state.value.page ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val token = preferences.getString(tokenKey(page.tripId))
            when (val result = api.guestIdentity(code, token, request)) {
                is ApiResult.Success -> {
                    preferences.putString(tokenKey(page.tripId), result.value.token)
                    _state.update { it.copy(busy = false, changing = false, writing = false, name = "", sameName = null) }
                    load()
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    _state.update { s ->
                        when (error.code) {
                            ErrorCode.GUEST_NAME_EXISTS -> s.copy(busy = false, sameName = s.page?.members?.firstOrNull { it.memberId == error.details?.get("memberId") })
                            ErrorCode.GUEST_NAME_TAKEN -> s.copy(busy = false, notice = GuestNotice.NAME_TAKEN)
                            ErrorCode.VALIDATION_FAILED -> s.copy(busy = false, nameError = error.details?.get("name") ?: "REQUIRED")
                            else -> s.copy(busy = false, notice = GuestNotice.FAILED)
                        }
                    }
                    if (error.code == ErrorCode.GUEST_NAME_TAKEN) load()
                }
            }
        }
    }

    fun toggle(poll: PollDto, optionId: String) = _state.update { s ->
        val current = s.selections[poll.id].orEmpty()
        val next = when {
            optionId in current -> current - optionId
            poll.multiChoice -> current + optionId
            else -> setOf(optionId)
        }
        s.copy(selections = s.selections + (poll.id to next))
    }

    /** "Kirim pilihan". */
    fun send(poll: PollDto) {
        val page = _state.value.page ?: return
        if (_state.value.busy) return
        val chosen = _state.value.selections[poll.id].orEmpty()
        val votes = poll.options.filter { it.id in chosen }.map { PollVoteInput(it.id, it.version) }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val token = preferences.getString(tokenKey(page.tripId))
            when (val result = api.guestVote(code, token, poll.id, VotePollRequest(votes))) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        busy = false, notice = GuestNotice.SENT,
                        page = s.page?.copy(polls = s.page.polls.map { if (it.id == poll.id) result.value else it }),
                    )
                }
                is ApiResult.Failure -> {
                    val notice = when (result.error.code) {
                        ErrorCode.VOTE_STALE -> GuestNotice.STALE
                        ErrorCode.POLL_CLOSED -> GuestNotice.CLOSED
                        else -> GuestNotice.FAILED
                    }
                    if (result.error.code == ErrorCode.GUEST_NAME_REQUIRED) preferences.putString(tokenKey(page.tripId), null)
                    _state.update { s ->
                        // the options changed: start from what the server has
                        val reset = if (notice == GuestNotice.STALE) s.selections - poll.id else s.selections
                        s.copy(busy = false, notice = notice, selections = reset)
                    }
                    load()
                }
            }
        }
    }

    fun tick(item: PersonalItemDto) {
        val page = _state.value.page ?: return
        val packed = !item.isPacked
        // shown at once; put back if the server says no
        fun set(value: Boolean) = _state.update { s ->
            s.copy(page = s.page?.copy(packing = s.page.packing.map { if (it.id == item.id) it.copy(isPacked = value) else it }))
        }
        set(packed)
        viewModelScope.launch {
            val token = preferences.getString(tokenKey(page.tripId))
            if (api.guestTick(code, token, item.id, packed) is ApiResult.Failure) {
                set(!packed)
                _state.update { it.copy(notice = GuestNotice.FAILED) }
            }
        }
    }

    companion object {
        fun tripKey(code: String) = "guest_trip_${code.uppercase()}"
        fun tokenKey(tripId: String) = "guest_token_$tripId"
    }
}

/** A poll a signed-in member chose to open from the guest page ("Buka di RANSEL"); the navigation takes it once. */
class PendingPollLink {
    private val _poll = MutableStateFlow<Pair<String, String>?>(null)
    val poll: StateFlow<Pair<String, String>?> = _poll.asStateFlow()

    fun offer(tripId: String, pollId: String) {
        _poll.value = tripId to pollId
    }

    fun clear() {
        _poll.value = null
    }
}
