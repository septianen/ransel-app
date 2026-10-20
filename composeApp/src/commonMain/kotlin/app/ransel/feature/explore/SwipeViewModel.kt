package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.explore.ExploreCard
import app.ransel.data.explore.ExploreSession
import app.ransel.data.credit.CreditsStore
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.explore.ExploreSwipe
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.FeedbackRequest
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.RecommendationRequest
import app.ransel.shared.model.FeedbackAction
import app.ransel.shared.model.ItemSource
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the swipe screen shows (designs `MemuatRekomendasi`, `Swipe`, `SwipeHabis`, `SwipeError`, `SwipeTidakAda`, `SwipeKuota`). */
sealed interface SwipeUiState {
    data object Loading : SwipeUiState
    data class Cards(val session: ExploreSession) : SwipeUiState
    data class Finished(val session: ExploreSession) : SwipeUiState
    data class NoResults(val session: ExploreSession) : SwipeUiState
    data class Error(val failure: ApiFailure) : SwipeUiState
    /** [cost] and [available] are credits, null when the server did not say (an older server). */
    data class OutOfCredits(val cost: Int?, val available: Int?) : SwipeUiState
    /** No session in the store (opened by mistake): the screen closes. */
    data object Empty : SwipeUiState
}

sealed interface SwipeEvent {
    /** [title] was saved to "Belum dijadwalkan". */
    data class Saved(val title: String) : SwipeEvent
}

private const val PREFETCH_AT = 3

class SwipeViewModel(
    private val tripId: String,
    private val store: ExploreSessionStore,
    private val api: RanselApi,
    private val itinerary: ItineraryRepository,
    private val credits: CreditsStore = CreditsStore(),
) : ViewModel() {
    private val _state = MutableStateFlow<SwipeUiState>(SwipeUiState.Loading)
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val events = Channel<SwipeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var session: ExploreSession? = null
    private var prefetching = false

    init {
        val saved = store.session(tripId)
        if (saved == null) _state.value = SwipeUiState.Empty
        else {
            session = saved
            if (saved.cards.isEmpty()) search(saved) else publish()
        }
    }

    /** First search of a session (counts against the daily quota). */
    private fun search(s: ExploreSession) {
        _state.value = SwipeUiState.Loading
        viewModelScope.launch {
            when (val result = api.recommend(s.form.toRequest(newSearch = true, exclude = emptyList()))) {
                is ApiResult.Success -> {
                    result.value.credits?.let(credits::update)
                    val cards = result.value.groups.flatMap { g -> g.items.map { ExploreCard(g.category, it) } }
                    if (cards.isEmpty()) {
                        _state.value = SwipeUiState.NoResults(s)
                        store.clear(tripId)
                    } else {
                        session = s.copy(cards = cards)
                        persist()
                        publish()
                    }
                }
                is ApiResult.Failure -> _state.value =
                    if (result.error.isOutOfCredits) result.error.toOutOfCredits()
                    else SwipeUiState.Error(result.error)
            }
        }
    }

    fun retry() {
        val s = session ?: store.session(tripId) ?: return
        search(s)
    }

    /** "Ubah preferensi" / finished: drop the session so the form starts fresh (keeps what was saved in the trip). */
    fun discard() = viewModelScope.launch { store.clear(tripId) }

    private suspend fun persist() {
        session?.let { store.save(it) }
    }

    fun like() = swipe(liked = true)
    fun skip() = swipe(liked = false)

    private fun swipe(liked: Boolean) {
        val s = session ?: return
        val card = s.cards.getOrNull(s.position) ?: return
        val item = card.item
        val itemId = if (liked) newId() else null
        session = s.copy(swipes = s.swipes + ExploreSwipe(item.placeId, liked, itemId))
        publish()
        viewModelScope.launch {
            persist()
            if (liked && itemId != null) {
                // Same path as a manual activity: local DB + sync on mobile, POST on web. Unscheduled, source AI.
                itinerary.save(
                    s.form.tripId,
                    ItineraryItemInput(
                        id = itemId, dayDate = null, title = item.name, category = card.category,
                        placeName = item.name, placeId = item.placeId, lat = item.lat, lng = item.lng,
                        durationMin = item.suggestedDurationMin, source = ItemSource.AI,
                    ),
                )
                events.send(SwipeEvent.Saved(item.name))
            }
            // Feedback only trains future results; losing it (offline) must never block swiping.
            api.sendFeedback(
                FeedbackRequest(
                    tripId = s.form.tripId, placeId = item.placeId, placeName = item.name,
                    category = card.category, action = if (liked) FeedbackAction.LIKE else FeedbackAction.SKIP,
                ),
            )
        }
        prefetchIfNeeded()
    }

    /** Takes back the last swipe: removes the item it saved and the stored feedback. */
    fun undo() {
        val s = session ?: return
        val last = s.swipes.lastOrNull() ?: return
        session = s.copy(swipes = s.swipes.dropLast(1))
        publish()
        viewModelScope.launch {
            persist()
            last.itineraryItemId?.let { itinerary.delete(s.form.tripId, it) }
            api.undoFeedback(s.form.tripId, last.placeId)
        }
    }

    /** Asks for the next batch in the background when 3 cards are left (does not count against the quota). */
    private fun prefetchIfNeeded() {
        val s = session ?: return
        if (prefetching || s.exhausted || s.cards.size - s.position > PREFETCH_AT) return
        prefetching = true
        viewModelScope.launch {
            val exclude = (s.seenIds + s.cards.map { it.item.placeId }).distinct()
            when (val result = api.recommend(s.form.toRequest(newSearch = false, exclude = exclude))) {
                is ApiResult.Success -> {
                    result.value.credits?.let(credits::update)
                    val known = exclude.toSet()
                    val more = result.value.groups.flatMap { g -> g.items.map { ExploreCard(g.category, it) } }
                        .filter { it.item.placeId !in known }
                    val current = session ?: s
                    session = current.copy(cards = current.cards + more, exhausted = more.isEmpty())
                    persist()
                    publish()
                }
                // A failed background batch only means "no more cards"; the user keeps swiping what is there.
                is ApiResult.Failure -> {
                    val current = session ?: s
                    session = current.copy(exhausted = true)
                    publish()
                }
            }
            prefetching = false
        }
    }

    private fun publish() {
        val s = session ?: return
        _state.update {
            if (s.position >= s.cards.size && (s.exhausted || s.cards.isEmpty())) SwipeUiState.Finished(s)
            else SwipeUiState.Cards(s)
        }
        if (s.cards.size - s.position <= PREFETCH_AT) prefetchIfNeeded()
    }
}

/** 402 `INSUFFICIENT_CREDITS` (an older server sent 429 `AI_QUOTA_EXCEEDED`). */
private val ApiFailure.isOutOfCredits get() = code == ErrorCode.INSUFFICIENT_CREDITS || code == ErrorCode.AI_QUOTA_EXCEEDED

/** The 402 carries `cost` and `available` in `details`. */
private fun ApiFailure.toOutOfCredits() = SwipeUiState.OutOfCredits(
    cost = details?.get("cost")?.toIntOrNull(),
    available = details?.get("available")?.toIntOrNull(),
)

private fun app.ransel.data.explore.ExploreForm.toRequest(newSearch: Boolean, exclude: List<String>) =
    RecommendationRequest(
        tripId = tripId, focusArea = focusArea.trim().ifEmpty { null }, priceLevel = priceLevel,
        interests = interests, extraInterests = extraInterests, style = style, notes = notes.trim().ifEmpty { null },
        excludePlaceIds = exclude, newSearch = newSearch,
    )
