package app.ransel.feature.voting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.voting.VotingStore
import app.ransel.feature.explore.toRequest
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.RoundState
import app.ransel.shared.domain.firstUnvotedIndex
import app.ransel.shared.domain.sortByLikes
import app.ransel.shared.domain.state
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.RoundPlaceDto
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Vote
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** Which of the voting designs the screen shows. */
enum class VoteMode {
    LOADING,
    ERROR,
    /** The round expired or never existed (design: snackbar text on the itinerary, here a small state). */
    GONE,
    /** `Voting`: swiping cards; a vote is recorded per swipe. */
    VOTING,
    /** `VotingMenunggu`: every card has my vote, voting still open. */
    WAITING,
    /** `VotingHasil`: the OWNER's result and decision (also after deciding, read-only). */
    RESULT,
    /** `VotingDitutupMenunggu` / `VotingDitutup`: a member after the window closed. */
    CLOSED,
}

data class VoteUiState(
    val loading: Boolean = true,
    val failure: ApiFailure? = null,
    val gone: Boolean = false,
    val round: RoundDto? = null,
    val tripTitle: String = "",
    val destination: String = "",
    /** The trip OWNER's name ("Rina yang akan memutuskan"); null until the members are loaded. */
    val ownerName: String? = null,
    /** Index of the card being voted on; null = not voting. Equal to the card count while a next batch loads. */
    val cursor: Int? = null,
    /** My votes, updated before the server answers. */
    val votes: Map<String, Vote> = emptyMap(),
    /** For undo: each vote of this visit with what it replaced. */
    val history: List<Pair<String, Vote?>> = emptyList(),
    /** OWNER: the places ticked for the itinerary. */
    val selected: Set<String> = emptySet(),
    val busy: Boolean = false,
    /** A next batch for this round is being fetched (requester only). */
    val loadingMore: Boolean = false,
    val now: Instant = Clock.System.now(),
) {
    val isOwner: Boolean get() = round?.voters != null
    val roundState: RoundState? get() = round?.state(now)
    val likedCount: Int get() = votes.count { it.value == Vote.LIKE }

    val mode: VoteMode
        get() {
            val r = round
            return when {
                gone -> VoteMode.GONE
                r == null && failure != null -> VoteMode.ERROR
                r == null -> VoteMode.LOADING
                roundState == RoundState.OPEN && cursor != null -> VoteMode.VOTING
                isOwner -> VoteMode.RESULT
                roundState == RoundState.OPEN -> VoteMode.WAITING
                else -> VoteMode.CLOSED
            }
        }

    /** Places in result order (most likes first); card order while the tally is hidden. */
    val ranked: List<RoundPlaceDto>
        get() {
            val r = round ?: return emptyList()
            val tally = r.tally ?: return r.places
            val byId = r.places.associateBy { it.item.placeId }
            return sortByLikes(r.places.map { it.item.placeId }, tally).mapNotNull { byId[it] }
        }
}

sealed interface VoteEvent {
    /** A change was rejected or could not be sent; [code] is an `ErrorCode`. */
    data class Problem(val code: String) : VoteEvent
    /** The OWNER put [count] places into the itinerary. */
    data class Decided(val count: Int) : VoteEvent
}

private const val PREFETCH_AT = 3

/**
 * One voting round (docs/specs/voting-spec.md), for every member: swipe to vote, see "your vote is in", and for the
 * OWNER the result and the decision. Server data only (like members); [showResult] opens the OWNER on the result
 * even when they still have cards to vote on.
 */
class VoteViewModel(
    private val tripId: String,
    private val roundId: String,
    private val showResult: Boolean,
    private val api: RanselApi,
    private val itinerary: ItineraryRepository,
    private val explore: ExploreSessionStore,
    private val voting: VotingStore,
    private val notifications: NotificationsStore,
    private val credits: CreditsStore,
    trips: TripRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VoteUiState())
    val state: StateFlow<VoteUiState> = _state.asStateFlow()

    private val events = Channel<VoteEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var selectionMade = false

    init {
        viewModelScope.launch {
            trips.observeTrip(tripId).collect { trip ->
                if (trip != null) _state.update { it.copy(tripTitle = trip.title, destination = trip.destination) }
            }
        }
        load(first = true)
        markNotificationsRead()
        viewModelScope.launch {
            val members = api.members(tripId)
            if (members is ApiResult.Success) {
                _state.update { it.copy(ownerName = members.value.firstOrNull { m -> m.role == app.ransel.shared.model.Role.OWNER }?.displayName) }
            }
        }
    }

    fun retry() = load(first = true)

    private fun load(first: Boolean = false) {
        if (first) _state.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            when (val result = api.round(tripId, roundId)) {
                is ApiResult.Success -> apply(result.value, first)
                is ApiResult.Failure -> _state.update {
                    if (result.error.code == ErrorCode.ROUND_NOT_FOUND) it.copy(loading = false, gone = true)
                    else it.copy(loading = false, failure = result.error)
                }
            }
        }
    }

    private fun apply(round: RoundDto, first: Boolean) {
        val now = Clock.System.now()
        _state.update { current ->
            val cursor = when {
                round.state(now) != RoundState.OPEN -> null
                first -> if (showResult && round.voters != null) null else round.firstUnvotedIndex()
                // A next batch arrived while waiting at the end: carry on with the first new card.
                current.cursor != null && current.cursor >= current.round?.places?.size ?: 0 ->
                    current.cursor.takeIf { it < round.places.size }
                else -> current.cursor
            }
            current.copy(
                loading = false, failure = null, round = round, now = now,
                votes = if (first) round.myVotes else current.votes,
                cursor = cursor,
                selected = if (selectionMade) current.selected else preselect(round),
            )
        }
        if (_state.value.cursor == null) finishedVoting()
        prefetchIfNeeded()
    }

    /** Places most members liked (more than half) start ticked; the owner changes it from there. */
    private fun preselect(round: RoundDto): Set<String> {
        if (round.decidedAt != null) return round.chosenPlaceIds.toSet()
        val tally = round.tally ?: return emptySet()
        return tally.filter { it.likes * 2 > round.memberCount }.map { it.placeId }.toSet()
    }

    // ── voting ──────────────────────────────────────────────────────────────

    fun like() = vote(Vote.LIKE)
    fun skip() = vote(Vote.SKIP)

    /** Vote on [placeId] from the place detail (the card on top of the deck). */
    fun voteFromDetail(placeId: String, vote: Vote) {
        val s = _state.value
        val index = s.round?.places?.indexOfFirst { it.item.placeId == placeId } ?: return
        if (index == s.cursor) vote(vote)
    }

    private fun vote(vote: Vote) {
        val s = _state.value
        val round = s.round ?: return
        val index = s.cursor ?: return
        val place = round.places.getOrNull(index)?.item?.placeId ?: return
        val before = s.votes[place]
        _state.update {
            it.copy(votes = it.votes + (place to vote), history = it.history + (place to before), cursor = index + 1)
        }
        if (index + 1 >= round.places.size && !_state.value.loadingMore) _state.update { it.copy(cursor = null) }
        viewModelScope.launch {
            val result = api.vote(tripId, roundId, place, vote)
            if (result is ApiResult.Failure) handleFailure(result.error, place, before)
            if (_state.value.cursor == null) finishedVoting()
        }
        prefetchIfNeeded()
    }

    fun undo() {
        val s = _state.value
        val (place, before) = s.history.lastOrNull() ?: return
        val index = s.round?.places?.indexOfFirst { it.item.placeId == place } ?: return
        _state.update {
            it.copy(
                votes = if (before == null) it.votes - place else it.votes + (place to before),
                history = it.history.dropLast(1),
                cursor = index,
            )
        }
        viewModelScope.launch {
            val result = if (before == null) api.undoVote(tripId, roundId, place) else api.vote(tripId, roundId, place, before)
            if (result is ApiResult.Failure) {
                events.send(VoteEvent.Problem(result.error.code))
                if (result.error.code == ErrorCode.VOTING_CLOSED) load()
            }
        }
    }

    /** "Ubah pilihanku": go through every card again; each new swipe replaces the earlier vote. */
    fun changeVotes() = _state.update { it.copy(cursor = 0, history = emptyList()) }

    private suspend fun handleFailure(error: ApiFailure, place: String, before: Vote?) {
        events.send(VoteEvent.Problem(error.code))
        if (error.code == ErrorCode.VOTING_CLOSED) {
            load()
        } else {
            _state.update { it.copy(votes = if (before == null) it.votes - place else it.votes + (place to before)) }
        }
    }

    /** Every card has a vote: refresh what the banner and the trip list show; the requester's session is done. */
    private fun finishedVoting() {
        viewModelScope.launch {
            voting.refresh(api, tripId)
            val session = explore.session(tripId)
            if (session?.roundId == roundId && !_state.value.loadingMore) explore.clear(tripId)
        }
    }

    /** The requester asks for the next batch of the same round when 3 cards are left (cards are appended on the server). */
    private fun prefetchIfNeeded() {
        val s = _state.value
        val round = s.round ?: return
        val cursor = s.cursor ?: return
        val session = explore.session(tripId)?.takeIf { it.roundId == roundId && !it.exhausted } ?: return
        if (s.loadingMore || round.places.size - cursor > PREFETCH_AT) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val exclude = round.places.map { it.item.placeId }
            val result = api.recommend(session.form.toRequest(newSearch = false, exclude = exclude, roundId = roundId))
            var added = false
            if (result is ApiResult.Success) {
                result.value.credits?.let(credits::update)
                added = result.value.groups.any { it.items.isNotEmpty() }
            }
            if (!added) explore.save(session.copy(exhausted = true))
            _state.update { it.copy(loadingMore = false) }
            if (added) {
                load()
            } else if (_state.value.cursor?.let { it >= round.places.size } == true) {
                _state.update { it.copy(cursor = null) }
                finishedVoting()
            }
        }
    }

    // ── deciding (OWNER) ────────────────────────────────────────────────────

    fun toggle(placeId: String) {
        selectionMade = true
        _state.update { it.copy(selected = if (placeId in it.selected) it.selected - placeId else it.selected + placeId) }
    }

    /** "Ikut memilih": the OWNER votes too (the result opens first for them). */
    fun startVoting() {
        val round = _state.value.round ?: return
        _state.update { it.copy(cursor = round.firstUnvotedIndex() ?: 0, history = emptyList()) }
    }

    fun showResult() = _state.update { it.copy(cursor = null) }

    /**
     * Puts the ticked places into "Belum dijadwalkan" through the normal path (local DB + sync on mobile, POST on
     * web), exactly like a swipe-right, then closes the round on the server. A place already in the itinerary is not
     * added twice.
     */
    fun decide() {
        val s = _state.value
        val round = s.round ?: return
        if (s.busy || !s.isOwner || round.decidedAt != null) return
        val chosen = round.places.filter { it.item.placeId in s.selected }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val existing = itinerary.observe(tripId).first().filter { it.deletedAt == null }.mapNotNull { it.placeId }.toSet()
            chosen.filter { it.item.placeId !in existing }.forEach { place ->
                val item = place.item
                itinerary.save(
                    tripId,
                    ItineraryItemInput(
                        id = newId(), dayDate = null, title = item.name, category = place.category,
                        placeName = item.name, placeId = item.placeId, lat = item.lat, lng = item.lng,
                        durationMin = item.suggestedDurationMin, source = ItemSource.AI,
                    ),
                )
            }
            when (val result = api.decideRound(tripId, roundId, chosen.map { it.item.placeId })) {
                is ApiResult.Success -> {
                    events.send(VoteEvent.Decided(chosen.size))
                    voting.refresh(api, tripId)
                    load()
                }
                is ApiResult.Failure -> {
                    events.send(VoteEvent.Problem(result.error.code))
                    if (result.error.code == ErrorCode.ROUND_ALREADY_DECIDED) load()
                }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Opening the round answers its notifications, so the bell stops asking. */
    private fun markNotificationsRead() {
        viewModelScope.launch {
            notifications.inbox.value?.items.orEmpty()
                .filter { it.readAt == null && it.params["roundId"] == roundId }
                .forEach { notifications.markRead(api, it.id) }
        }
    }

}
