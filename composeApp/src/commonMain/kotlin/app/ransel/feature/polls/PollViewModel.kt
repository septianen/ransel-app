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
import app.ransel.shared.dto.PollSearchRequest
import app.ransel.shared.dto.PollSearchResult
import app.ransel.shared.dto.PollVoteInput
import app.ransel.shared.model.PollKind
import app.ransel.ui.format.parseAmountMinor
import app.ransel.util.newId
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

/** Why a Jelajah search or the adding of its results did not work. */
enum class PollSearchError { NO_CREDITS, NOT_VERIFIED, UNAVAILABLE, CLOSED, FAILED }

/** The Jelajah sheet of a stay or food poll: the form, then the results to tick. */
data class PollSearchState(
    val area: String = "",
    val min: String = "",
    val max: String = "",
    val needs: String = "",
    val loading: Boolean = false,
    val adding: Boolean = false,
    /** Null = still the form. */
    val results: List<PollSearchResult>? = null,
    val picked: Set<String> = emptySet(),
    val error: PollSearchError? = null,
    val badInput: Boolean = false,
)

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
    val search: PollSearchState? = null,
    val destination: String = "",
    val currency: String = "IDR",
    val myMemberId: String? = null,
    val myRole: Role = Role.VIEWER,
) {
    val isOwner: Boolean get() = myRole == Role.OWNER
    val canEditOptions: Boolean get() = myRole.atLeast(Role.EDITOR) && poll?.isOpen == true
    val canSearch: Boolean get() = canEditOptions && (poll?.kind == PollKind.STAY || poll?.kind == PollKind.FOOD)
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
                if (trip != null) _state.update { it.copy(currency = trip.baseCurrency, myRole = trip.myRole, destination = trip.destination) }
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

    /** The title and picture behind a pasted link, or null when it cannot be read. */
    suspend fun previewLink(url: String): app.ransel.shared.dto.LinkPreviewDto? = (api.linkPreview(url) as? ApiResult.Success)?.value

    fun openOptionSheet(option: PollOptionDto?) = _state.update { it.copy(optionSheet = OptionSheet(option)) }
    fun closeOptionSheet() = _state.update { it.copy(optionSheet = null) }

    fun saveOption(input: PollOptionInput) {
        val editing = _state.value.optionSheet?.option != null
        _state.update { it.copy(optionSheet = null) }
        act { if (editing) api.updatePollOption(pollId, input) else api.addPollOption(pollId, input) }
    }

    // ── Jelajah ─────────────────────────────────────────────────────────────

    fun openSearch() = _state.update { it.copy(search = PollSearchState()) }
    fun closeSearch() = _state.update { it.copy(search = null) }
    fun backToSearchForm() = editSearch { it.copy(results = null, picked = emptySet(), error = null) }
    fun onSearchArea(value: String) = editSearch { it.copy(area = value) }
    fun onSearchMin(value: String) = editSearch { it.copy(min = value, badInput = false) }
    fun onSearchMax(value: String) = editSearch { it.copy(max = value, badInput = false) }
    fun onSearchNeeds(value: String) = editSearch { it.copy(needs = value) }
    fun togglePicked(placeId: String) = editSearch { it.copy(picked = if (placeId in it.picked) it.picked - placeId else it.picked + placeId) }

    private fun editSearch(change: (PollSearchState) -> PollSearchState) = _state.update { s -> s.search?.let { s.copy(search = change(it)) } ?: s }

    fun runSearch() {
        val form = _state.value.search ?: return
        if (form.loading) return
        val currency = _state.value.currency
        val min = form.min.takeIf { it.isNotBlank() }?.let { parseAmountMinor(it, currency) ?: return editSearch { s -> s.copy(badInput = true) } }
        val max = form.max.takeIf { it.isNotBlank() }?.let { parseAmountMinor(it, currency) ?: return editSearch { s -> s.copy(badInput = true) } }
        if (min != null && max != null && min > max) return editSearch { it.copy(badInput = true) }
        editSearch { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val request = PollSearchRequest(
                area = form.area.trim().ifEmpty { null }, minMinor = min, maxMinor = max, needs = form.needs.trim().ifEmpty { null },
            )
            when (val result = api.searchPoll(pollId, request)) {
                is ApiResult.Success -> editSearch { it.copy(loading = false, results = result.value.results, picked = emptySet()) }
                is ApiResult.Failure -> editSearch { it.copy(loading = false, error = result.error.toSearchError()) }
            }
        }
    }

    /** The ticked results become options of the poll. */
    fun addPicked() {
        val form = _state.value.search ?: return
        val chosen = form.results.orEmpty().filter { it.placeId in form.picked }
        if (chosen.isEmpty() || form.adding) return
        editSearch { it.copy(adding = true, error = null) }
        viewModelScope.launch {
            val options = chosen.map {
                PollOptionInput(id = newId(), label = it.name, placeId = it.placeId, priceMinor = it.priceMinor, priceUnit = it.priceUnit)
            }
            when (val result = api.addPollOptionsBulk(pollId, options)) {
                is ApiResult.Success -> _state.update { it.copy(poll = result.value, search = null) }
                is ApiResult.Failure -> editSearch { it.copy(adding = false, error = if (result.error.code == ErrorCode.POLL_CLOSED) PollSearchError.CLOSED else PollSearchError.FAILED) }
            }
        }
    }

    private fun ApiFailure.toSearchError() = when (code) {
        ErrorCode.INSUFFICIENT_CREDITS -> PollSearchError.NO_CREDITS
        ErrorCode.EMAIL_NOT_VERIFIED -> PollSearchError.NOT_VERIFIED
        ErrorCode.AI_UNAVAILABLE -> PollSearchError.UNAVAILABLE
        ErrorCode.POLL_CLOSED -> PollSearchError.CLOSED
        else -> PollSearchError.FAILED
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
