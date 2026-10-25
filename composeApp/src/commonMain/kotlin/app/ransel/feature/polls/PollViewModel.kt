package app.ransel.feature.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.DecidePollRequest
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.dto.PollVoteInput
import app.ransel.shared.dto.ReopenPollRequest
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.VotePollRequest
import app.ransel.shared.model.Role
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A confirmation over the poll screen. */
sealed interface PollDialog {
    /** [option] null = close without choosing. */
    data class Decide(val option: PollOptionDto?) : PollDialog
    data object Delete : PollDialog
    data class DeleteOption(val option: PollOptionDto) : PollDialog
}

/** The option editor: [option] null = a new one. */
data class OptionSheet(val option: PollOptionDto?)

/** Something the user should know, shown once as a snackbar. */
enum class PollNotice { STALE, CLOSED, VOTE_FAILED, ACTION_FAILED }

data class PollUiState(
    val loading: Boolean = true,
    val poll: PollDto? = null,
    val failure: ApiFailure? = null,
    val members: List<TripMemberDto> = emptyList(),
    val busy: Boolean = false,
    val dialog: PollDialog? = null,
    val optionSheet: OptionSheet? = null,
    val currency: String = "IDR",
    val myMemberId: String? = null,
    val myRole: Role = Role.VIEWER,
) {
    val isOwner: Boolean get() = myRole == Role.OWNER
    val canEditOptions: Boolean get() = myRole.atLeast(Role.EDITOR) && poll?.isOpen == true
    val isManager: Boolean get() = poll?.isManagedBy(myMemberId, isOwner) == true
}

sealed interface PollEvent {
    data class Notice(val notice: PollNotice) : PollEvent
    /** The poll was deleted: leave the screen. */
    data object Deleted : PollEvent
}

/** One poll: vote, and for the creator or the owner close, confirm, reopen or delete it. Online only. */
class PollViewModel(
    private val tripId: String,
    private val pollId: String,
    private val api: RanselApi,
    session: SessionManager,
    trips: TripRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        PollUiState(myMemberId = session.user?.id?.let { memberIdFor(tripId, it) }),
    )
    val state: StateFlow<PollUiState> = _state.asStateFlow()

    private val events = Channel<PollEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            trips.observeTrip(tripId).collect { trip ->
                if (trip != null) _state.update { it.copy(currency = trip.baseCurrency, myRole = trip.myRole) }
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(failure = null, loading = it.poll == null) }
        viewModelScope.launch {
            when (val result = api.poll(pollId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, poll = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, failure = result.error) }
            }
            // names for the voters; the poll is usable without them
            (api.members(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(members = r.value) } }
        }
    }

    /** Taps on an option: one choice replaces the last, several choices toggle. The server's answer is the truth. */
    fun toggle(option: PollOptionDto) {
        val poll = _state.value.poll ?: return
        if (!poll.isOpen || _state.value.busy) return
        val chosen = when {
            option.id in poll.myVotes -> poll.myVotes - option.id
            poll.multiChoice -> poll.myVotes + option.id
            else -> listOf(option.id)
        }
        val versions = poll.options.associate { it.id to it.version }
        _state.update { it.copy(busy = true, poll = poll.copy(myVotes = chosen)) }
        viewModelScope.launch {
            val request = VotePollRequest(chosen.mapNotNull { id -> versions[id]?.let { PollVoteInput(id, it) } })
            when (val result = api.votePoll(pollId, request)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, poll = result.value) }
                is ApiResult.Failure -> {
                    _state.update { it.copy(busy = false, poll = poll) }
                    events.send(
                        PollEvent.Notice(
                            when (result.error.code) {
                                ErrorCode.VOTE_STALE -> PollNotice.STALE
                                ErrorCode.POLL_CLOSED -> PollNotice.CLOSED
                                else -> PollNotice.VOTE_FAILED
                            },
                        ),
                    )
                    // an edited option or a closed poll: show what it looks like now
                    if (result.error.code == ErrorCode.VOTE_STALE || result.error.code == ErrorCode.POLL_CLOSED) load()
                }
            }
        }
    }

    // ── the creator and the owner ───────────────────────────────────────────

    fun close() = act { api.closePoll(pollId) }

    fun reopen() = act { api.reopenPoll(pollId, ReopenPollRequest(Clock.System.now() + 1.days)) }

    fun ask(dialog: PollDialog) = _state.update { it.copy(dialog = dialog) }
    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun confirmDialog() {
        val dialog = _state.value.dialog ?: return
        _state.update { it.copy(dialog = null) }
        when (dialog) {
            is PollDialog.Decide -> act { api.decidePoll(pollId, DecidePollRequest(dialog.option?.id)) }
            PollDialog.Delete -> viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                when (api.deletePoll(pollId)) {
                    is ApiResult.Success -> events.send(PollEvent.Deleted)
                    is ApiResult.Failure -> events.send(PollEvent.Notice(PollNotice.ACTION_FAILED))
                }
                _state.update { it.copy(busy = false) }
            }
            is PollDialog.DeleteOption -> act { api.deletePollOption(pollId, dialog.option.id) }
        }
    }

    // ── options ─────────────────────────────────────────────────────────────

    fun openOptionSheet(option: PollOptionDto?) = _state.update { it.copy(optionSheet = OptionSheet(option)) }
    fun closeOptionSheet() = _state.update { it.copy(optionSheet = null) }

    fun saveOption(input: PollOptionInput) {
        val editing = _state.value.optionSheet?.option != null
        _state.update { it.copy(optionSheet = null) }
        act { if (editing) api.updatePollOption(pollId, input) else api.addPollOption(pollId, input) }
    }

    private fun act(call: suspend () -> ApiResult<PollDto>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = call()) {
                is ApiResult.Success -> _state.update { it.copy(poll = result.value) }
                is ApiResult.Failure -> {
                    events.send(PollEvent.Notice(if (result.error.code == ErrorCode.POLL_CLOSED) PollNotice.CLOSED else PollNotice.ACTION_FAILED))
                    load()
                }
            }
            _state.update { it.copy(busy = false) }
        }
    }
}
