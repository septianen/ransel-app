package app.ransel.feature.tripform.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.sync.SyncStatus
import app.ransel.data.travel.AnswerChange
import app.ransel.data.travel.TravelAnswerChanges
import app.ransel.feature.interview.KhasState
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.TASTE_NOTES_MAX
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.domain.canSaveFromSheet
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

/** The section "Perjalanan" of Edit trip: the trip (live), its Seleramu, and the sheet "Ubah seleramu" with its draft. */
data class TripTravelUiState(
    val trip: TripDto? = null,
    /** The Seleramu of this trip; null = not answered ("Belum diisi · Isi"). */
    val taste: InterviewTaste? = null,
    /** The sheet is open and this is its draft. */
    val draft: InterviewTaste? = null,
    val khas: KhasState = KhasState.Hidden,
    /** The range the trip's budget points to (the server's answer with the chips); null = no budget or not known: Sedang. */
    val budgetPriceLevel: PriceLevel? = null,
)

internal fun ExploreForm.toTaste() = InterviewTaste(
    interests = interests.toSet(), extraInterests = extraInterests.toSet(), style = style, priceLevel = priceLevel, notes = notes,
)

/**
 * Edit trip's section "Perjalanan" (spec 1638556): the four rows read the trip live, so a saved answer shows at once. Only the
 * owner has the section (the form is the owner's). The Seleramu is kept per trip on this device, like the interview's.
 */
class TripTravelViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val explore: ExploreSessionStore,
    private val api: RanselApi?,
    private val session: SessionManager?,
    private val preferences: PreferencesRepository,
    private val answerChanges: TravelAnswerChanges,
    private val sync: SyncStatus = NoSyncStatus,
) : ViewModel() {
    private val _state = MutableStateFlow(TripTravelUiState(taste = explore.taste(tripId)?.toTaste()))
    val state: StateFlow<TripTravelUiState> = _state.asStateFlow()

    /** Saved answers waiting for Edit trip to say something about them. */
    val changes = answerChanges.changes

    private var khasJob: Job? = null

    init {
        viewModelScope.launch { trips.observeTrip(tripId).filterNotNull().collect { t -> _state.update { it.copy(trip = t) } } }
    }

    // ── the sheet ───────────────────────────────────────────────────────────

    fun openTasteSheet() {
        _state.update { it.copy(draft = it.taste ?: InterviewTaste(), khas = KhasState.Hidden) }
        loadKhas()
    }

    fun closeTasteSheet() = _state.update { it.copy(draft = null) }

    private fun draft(change: (InterviewTaste) -> InterviewTaste) = _state.update { s -> s.draft?.let { s.copy(draft = change(it)) } ?: s }
    fun draftToggleInterest(interest: Interest) = draft { t -> t.copy(interests = if (interest in t.interests) t.interests - interest else t.interests + interest) }
    fun draftToggleExtra(label: String) = draft { t -> t.copy(extraInterests = if (label in t.extraInterests) t.extraInterests - label else t.extraInterests + label) }
    fun draftStyle(style: TravelStyle) = draft { it.copy(style = style) }
    fun draftPriceLevel(level: PriceLevel) = draft { it.copy(priceLevel = level) }
    fun draftNotes(value: String) = draft { it.copy(notes = value.take(TASTE_NOTES_MAX)) }

    /** Simpan: this trip's Seleramu, stored at once (needs an interest). Edit trip says "Seleramu diubah." with "Urungkan". */
    fun saveTasteSheet() {
        val s = _state.value
        val draft = s.draft ?: return
        val trip = s.trip ?: return
        if (!draft.canSaveFromSheet()) return
        val before = explore.taste(tripId)
        _state.update { it.copy(taste = draft, draft = null) }
        viewModelScope.launch {
            explore.saveTaste(draft.toForm(trip, s.budgetPriceLevel))
            answerChanges.post(AnswerChange(tripId = tripId, row = TravelRow.TASTE, tasteChanged = true, undoTaste = before))
        }
    }

    /** The destination's own interests for the sheet: online and signed in only; every failure just hides them. */
    private fun loadKhas() {
        val api = api ?: return
        val trip = _state.value.trip ?: return
        if (session?.user == null) return
        khasJob?.cancel()
        _state.update { it.copy(khas = KhasState.Loading) }
        khasJob = viewModelScope.launch {
            val language = preferences.observe().first().language.tag
            val result = withContext(Dispatchers.Default) {
                withTimeoutOrNull(KHAS_TIMEOUT_MILLIS) { api.exploreSuggestions(tripId = tripId, destination = trip.destination, language = language) }
            }
            val answer = (result as? ApiResult.Success)?.value
            val interests = answer?.interests.orEmpty()
            _state.update {
                it.copy(
                    khas = if (interests.isEmpty()) KhasState.Hidden else KhasState.Loaded(interests, trip.destination),
                    budgetPriceLevel = answer?.suggestedPriceLevel,
                )
            }
        }
    }

    // ── undo ────────────────────────────────────────────────────────────────

    /** "Urungkan": puts back the answers (the request holds them all) or the Seleramu as it was. */
    fun undo(change: AnswerChange) {
        viewModelScope.launch {
            change.undo?.let { trips.update(change.tripId, it) }
            if (change.tasteChanged) {
                val before = change.undoTaste
                if (before != null) explore.saveTaste(before) else explore.clearTaste(change.tripId)
                _state.update { it.copy(taste = before?.toTaste()) }
            }
        }
    }

    /** The search uses the range the user picked, else the budget's, else Sedang. */
    private fun InterviewTaste.toForm(trip: TripDto, budgetLevel: PriceLevel?) = ExploreForm(
        tripId = tripId, tripTitle = trip.title, destination = trip.destination, startDate = trip.startDate, endDate = trip.endDate,
        priceLevel = priceLevel ?: budgetLevel ?: PriceLevel.MEDIUM, interests = interests.toList(), extraInterests = extraInterests.toList().take(5),
        style = style ?: TravelStyle.SANTAI, notes = notes,
    )

    private companion object {
        const val KHAS_TIMEOUT_MILLIS = 8_000L
    }
}
