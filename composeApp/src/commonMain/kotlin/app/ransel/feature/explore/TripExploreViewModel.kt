package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.explore.TripTasteRepository
import app.ransel.data.explore.toForm
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.interview.KhasState
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.TASTE_NOTES_MAX
import app.ransel.shared.domain.canSaveFromSheet
import app.ransel.shared.domain.toTripTaste
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate

internal fun LocalDate.monthKey(): String = "$year-${monthNumber.toString().padStart(2, '0')}"

data class TripExploreState(
    /** The trip and its Seleramu are not loaded yet. */
    val loading: Boolean = true,
    val trip: TripDto? = null,
    /** What the sheet shows: the trip's Seleramu, changed by the user; dropped when the sheet is closed. */
    val draft: InterviewTaste = InterviewTaste(),
    val khas: KhasState = KhasState.Hidden,
    /** The range the trip's budget points to; null = no budget or not known (Sedang). */
    val budgetPriceLevel: PriceLevel? = null,
    /** 2+ members: the search becomes a voting round ("Hasilnya divoting semua anggota."). */
    val group: Boolean = false,
    /** Saving the Seleramu and the session before the swipe screen opens. */
    val starting: Boolean = false,
) {
    val canSearch: Boolean get() = !loading && !starting && trip != null && draft.canSaveFromSheet()
}

/**
 * "Cari tempat" from a trip (plan explore-trip-one-tap v4, owner 22 Nov 2026): the "Ubah seleramu" sheet, filled in from
 * the trip's Seleramu, with the search as its button. Two taps: open, search. Whatever is selected when searching becomes
 * the trip's Seleramu; closing the sheet changes nothing. The swipe screen makes the request (loading, failure, gates).
 */
class TripExploreViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val api: RanselApi,
    private val tastes: TripTasteRepository,
    private val store: ExploreSessionStore,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TripExploreState())
    val state: StateFlow<TripExploreState> = _state.asStateFlow()

    /** The Seleramu as loaded: a search saves the draft only when it differs. */
    private var saved: InterviewTaste? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val members = api.members(tripId)
            if (members is ApiResult.Success) _state.update { it.copy(group = members.value.size >= 2) }
        }
    }

    /** The sheet opened: start from the trip's Seleramu again (another member may have changed it). */
    fun open() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, starting = false, draft = saved ?: InterviewTaste()) }
        loadJob = viewModelScope.launch {
            val trip = trips.observeTrip(tripId).filterNotNull().first()
            val taste = tastes.load(trip, canEdit = true)
            saved = taste
            _state.update { it.copy(loading = false, trip = trip, draft = taste ?: InterviewTaste()) }
            loadKhas(trip)
        }
    }

    private fun draft(change: (InterviewTaste) -> InterviewTaste) = _state.update { it.copy(draft = change(it.draft)) }
    fun toggleInterest(interest: Interest) = draft { t -> t.copy(interests = if (interest in t.interests) t.interests - interest else t.interests + interest) }
    fun toggleExtra(label: String) = draft { t ->
        // at most 5 are sent (server limit)
        t.copy(extraInterests = if (label in t.extraInterests) t.extraInterests - label else (t.extraInterests + label).take(5).toSet())
    }
    fun setStyle(style: TravelStyle) = draft { it.copy(style = style) }
    fun setPriceLevel(level: PriceLevel) = draft { it.copy(priceLevel = level) }
    fun setNotes(value: String) = draft { it.copy(notes = value.take(TASTE_NOTES_MAX)) }

    /**
     * "Cari tempat": the draft becomes the trip's Seleramu when it changed (a failed save still searches; this device keeps
     * it), then a fresh session is stored and [onReady] opens the swipe screen, which asks the server.
     */
    fun search(onReady: (tripId: String) -> Unit) {
        val s = _state.value
        val trip = s.trip ?: return
        if (!s.canSearch) return
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            val draft = s.draft
            if (draft.toTripTaste() != saved?.toTripTaste()) {
                tastes.save(trip, draft, s.budgetPriceLevel)
                saved = draft
            }
            store.save(ExploreSession(draft.toForm(trip, s.budgetPriceLevel)))
            _state.update { it.copy(starting = false) }
            onReady(tripId)
        }
    }

    /** The destination's own interests; every failure just hides them. Saved "Khas" chips stay listed so they can be removed. */
    private suspend fun loadKhas(trip: TripDto) {
        _state.update { it.copy(khas = KhasState.Loading) }
        val language = preferences.observe().first().language.tag
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(KHAS_TIMEOUT_MILLIS) { api.exploreSuggestions(tripId = trip.id, destination = trip.destination, language = language) }
        }
        val answer = (result as? ApiResult.Success)?.value
        _state.update {
            val chips = (answer?.interests.orEmpty() + it.draft.extraInterests).distinct()
            it.copy(
                khas = if (chips.isEmpty()) KhasState.Hidden else KhasState.Loaded(chips, trip.destination),
                budgetPriceLevel = answer?.suggestedPriceLevel,
            )
        }
    }

    private companion object {
        const val KHAS_TIMEOUT_MILLIS = 8_000L
    }
}
