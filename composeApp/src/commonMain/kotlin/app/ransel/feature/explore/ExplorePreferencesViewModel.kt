package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.explore.ExploreForm
import app.ransel.data.explore.ExploreSession
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.data.repository.TripRepository
import app.ransel.data.sync.SyncStatus
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.util.newId
import app.ransel.util.today
import app.ransel.feature.destination.DestinationChoice
import app.ransel.shared.dto.TripPlaceDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** The "Khas <destination>" box. It never blocks the form: every failure is [Hidden]. */
sealed interface SuggestionsState {
    data object Hidden : SuggestionsState
    data class Loading(val destination: String) : SuggestionsState
    data class Loaded(val interests: List<String>) : SuggestionsState
}

/** "Dipilih dari budget trip …": amount per person per day in the trip's base currency. */
data class BudgetNote(val amountMinor: Long, val currency: String)

data class ExplorePreferencesState(
    /** The trip is not loaded yet. */
    val loading: Boolean,
    val tripTitle: String = "",
    /** The existing trip's own destination (shown on the trip card); the field below starts with it. */
    val tripDestination: String = "",
    /** Where to explore: typed for a new trip, prefilled from the trip and editable for an existing one. */
    val destination: String = "",
    /** A new trip's destination picked from the suggestions (stored on the trip). */
    val destinationPlace: TripPlaceDto? = null,
    /** A trip without dates ("Tanggal belum pasti") has none. */
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val priceLevel: PriceLevel = PriceLevel.MEDIUM,
    val budgetNote: BudgetNote? = null,
    /** The trip has no budget (existing trips only): shows the "Sedang dipilih sebagai awal" note. */
    val noBudget: Boolean = false,
    val interests: List<Interest> = emptyList(),
    /** Ticked "Khas …" chips. */
    val extraInterests: List<String> = emptyList(),
    val suggestions: SuggestionsState = SuggestionsState.Hidden,
    val style: TravelStyle = TravelStyle.SANTAI,
    val notes: String = "",
    /** Creating the trip / saving the session. */
    val starting: Boolean = false,
    /** Members of the existing trip; 2+ means the search becomes a voting round (docs/specs/voting-spec.md). */
    val memberCount: Int = 1,
    val showErrors: Boolean = false,
) {
    val days: Int get() = if (startDate != null && endDate != null) startDate.daysUntil(endDate) + 1 else 0
    val hasDates: Boolean get() = startDate != null && endDate != null
    val hasInterest: Boolean get() = interests.isNotEmpty() || extraInterests.isNotEmpty()
    val tripReady: Boolean get() = destination.isNotBlank() && !loading
    val canSearch: Boolean get() = tripReady && hasInterest
}

/** [month] null = a new trip without dates: the server answers with the general chips of the destination. */
private data class SuggestionsQuery(val tripId: String?, val destination: String, val month: String?, val debounce: Boolean, val nonce: Int) {
    val key: String get() = "${tripId.orEmpty()}|${destination.trim().lowercase()}|${month.orEmpty()}"
}

internal fun LocalDate.monthKey(): String = "$year-${monthNumber.toString().padStart(2, '0')}"

private const val SUGGESTIONS_DEBOUNCE_MILLIS = 600L
private const val SUGGESTIONS_TIMEOUT_MILLIS = 8_000L

/**
 * Explore step 2 (spec "Form inputs" and "Destination interests") for an existing trip ("Ubah preferensi", "Cari dengan
 * seleraku"): the form starts with the trip's Seleramu. A new trip is made by the interview ("Buat trip dan cari"), not here.
 * The suggestions load on their own; the search never waits for them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorePreferencesViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val api: RanselApi,
    private val store: ExploreSessionStore,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ExplorePreferencesState(
            loading = true,
            startDate = today().plus(DatePeriod(days = 14)),
            endDate = today().plus(DatePeriod(days = 18)),
        ),
    )
    val state: StateFlow<ExplorePreferencesState> = _state.asStateFlow()

    private val queries = MutableStateFlow<SuggestionsQuery?>(null)
    private var nonce = 0
    private var lastKey: String? = null
    /** The user chose a price level themselves: a suggestion must not overwrite it. */
    private var priceTouched = false

    init {
        viewModelScope.launch {
            queries.filterNotNull().collectLatest { loadSuggestions(it) }
        }
        viewModelScope.launch { loadTrip(tripId) }
        viewModelScope.launch {
            val members = api.members(tripId)
            if (members is ApiResult.Success) _state.update { it.copy(memberCount = members.value.size) }
        }
    }

    private suspend fun loadTrip(id: String) {
        val trip = trips.observeTrip(id).filterNotNull().first()
        val last = store.takeLastForm(id)
        if (last != null) priceTouched = true
        _state.update {
            it.copy(
                loading = false, tripTitle = trip.title, tripDestination = trip.destination,
                // "Ubah preferensi" keeps the destination of the last search (Solo on a Yogyakarta trip).
                destination = last?.destination?.takeIf { d -> d.isNotBlank() } ?: trip.destination,
                startDate = trip.startDate, endDate = trip.endDate,
                noBudget = trip.budgetMinor == null,
                priceLevel = last?.priceLevel ?: it.priceLevel,
                interests = last?.interests ?: it.interests,
                extraInterests = last?.extraInterests ?: it.extraInterests,
                style = last?.style ?: it.style,
                notes = last?.notes ?: it.notes,
            )
        }
        requestSuggestions(debounce = false)
    }

    // ── suggestions ────────────────────────────────────────────────────────

    private fun requestSuggestions(debounce: Boolean) {
        val s = _state.value
        val month = (s.startDate ?: today()).monthKey()
        val query = SuggestionsQuery(tripId, s.destination, month, debounce, nonce++)
        // Blur right after the debounce already loaded this exact query: nothing to do.
        if (!debounce && query.key == lastKey) return
        queries.value = query
    }

    private suspend fun loadSuggestions(q: SuggestionsQuery) {
        if (q.debounce) delay(SUGGESTIONS_DEBOUNCE_MILLIS)
        if (q.destination.trim().length < 2) {
            lastKey = null
            _state.update { it.copy(suggestions = SuggestionsState.Hidden, extraInterests = emptyList()) }
            return
        }
        lastKey = q.key
        _state.update { it.copy(suggestions = SuggestionsState.Loading(q.destination.trim())) }
        val language = preferences.observe().first().language.tag
        // Real-time timeout (Default dispatcher): the spec's 8 s is wall-clock time, not a UI animation delay.
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(SUGGESTIONS_TIMEOUT_MILLIS) {
                api.exploreSuggestions(
                    tripId = q.tripId,
                    // With a trip the destination replaces the trip's for the chips; month and budget stay the trip's.
                    destination = q.destination.trim(),
                    month = null,
                    language = language,
                )
            }
        }
        val response = (result as? ApiResult.Success)?.value
        if (response == null) {
            // Timeout, error, offline: hide the box, no message (spec "Timing and states").
            _state.update { it.copy(suggestions = SuggestionsState.Hidden, extraInterests = emptyList()) }
            return
        }
        _state.update { s ->
            val list = response.interests
            val budgetNote = if (response.budgetPerPersonPerDayMinor != null && response.currency != null) {
                BudgetNote(response.budgetPerPersonPerDayMinor!!, response.currency!!)
            } else {
                null
            }
            s.copy(
                suggestions = if (list.isEmpty()) SuggestionsState.Hidden else SuggestionsState.Loaded(list),
                // Chips already ticked that still exist stay ticked; the others are dropped.
                extraInterests = s.extraInterests.filter { it in list },
                priceLevel = if (!priceTouched && response.suggestedPriceLevel != null) response.suggestedPriceLevel!! else s.priceLevel,
                budgetNote = budgetNote ?: s.budgetNote,
            )
        }
    }

    // ── form ───────────────────────────────────────────────────────────────

    fun setDestination(value: String) {
        _state.update { it.copy(destination = value.take(200), destinationPlace = null) }
        requestSuggestions(debounce = true)
    }

    /** Picked in the destination picker: the chips load right away. */
    fun pickDestination(choice: DestinationChoice) {
        _state.update {
            it.copy(destination = choice.name.take(200), destinationPlace = choice.place)
        }
        requestSuggestions(debounce = false)
    }

    /** The destination field lost focus: load right away if the debounce has not already. */
    fun onDestinationBlur() = requestSuggestions(debounce = false)

    fun setPriceLevel(level: PriceLevel) {
        priceTouched = true
        _state.update { it.copy(priceLevel = level) }
    }
    fun setStyle(style: TravelStyle) = _state.update { it.copy(style = style) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value.take(300)) }
    fun toggleInterest(interest: Interest) = _state.update {
        it.copy(interests = if (interest in it.interests) it.interests - interest else it.interests + interest)
    }
    fun toggleExtraInterest(label: String) = _state.update {
        // At most 5 are sent (spec); the box never offers more than 5.
        it.copy(extraInterests = if (label in it.extraInterests) it.extraInterests - label else (it.extraInterests + label).take(5))
    }

    /**
     * Stores a fresh session (no cards yet) for the trip; the swipe screen makes the request.
     */
    fun start(onReady: (tripId: String) -> Unit) {
        val s = _state.value
        if (s.starting) return
        if (!s.canSearch) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            store.save(
                ExploreSession(
                    ExploreForm(
                        tripId = tripId, tripTitle = s.tripTitle, destination = s.destination.trim(),
                        startDate = s.startDate, endDate = s.endDate,
                        priceLevel = s.priceLevel, interests = s.interests, extraInterests = s.extraInterests,
                        style = s.style, notes = s.notes,
                    ),
                ),
            )
            _state.update { it.copy(starting = false) }
            onReady(tripId)
        }
    }
}
