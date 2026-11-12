package app.ransel.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.destination.LocationState
import app.ransel.platform.LocationFix
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PLACE_QUERY_MIN
import app.ransel.shared.domain.normalizeSearch
import app.ransel.shared.dto.PlaceSearchScope
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.util.newId
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil

/**
 * What the "Tempat" picker hands back to the activity form. [lat] and [lng] come together or not at all; without them
 * the place is plain text ("Tanpa titik di peta"). [fromSuggestion] is true for a Google suggestion: only then an empty
 * activity title is filled with the name.
 */
data class PlaceChoice(
    val name: String,
    val secondary: String? = null,
    val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val fromSuggestion: Boolean = false,
) {
    val hasPoint: Boolean get() = lat != null && lng != null
}

/** A place already used by an activity of this trip ("Dipakai di trip ini"); [day] is the 1-based day, null = not scheduled. */
data class UsedPlace(val choice: PlaceChoice, val day: Int?)

/** What the server side of the search can do right now. */
enum class PlaceSearchMode {
    READY,
    /** The request did not reach the server. */
    OFFLINE,
    /** The server or Google answered with an error, or Google is down (`fallback`). */
    UNAVAILABLE,
}

/** One selectable row, in the order shown: this trip's places first, then Google's suggestions. */
sealed interface PlacePickerRow {
    data class Used(val place: UsedPlace) : PlacePickerRow
    data class Suggestion(val suggestion: PlaceSuggestionDto) : PlacePickerRow
}

data class PlacePickerUiState(
    val query: String = "",
    val suggestions: List<PlaceSuggestionDto> = emptyList(),
    /** A search for the current text is waiting or running. */
    val searching: Boolean = false,
    /** A search for the current text has finished (with or without results). */
    val searched: Boolean = false,
    val mode: PlaceSearchMode = PlaceSearchMode.READY,
    /** Every place the trip's activities use, newest first. */
    val usedAll: List<UsedPlace> = emptyList(),
    val location: LocationState = LocationState.Idle,
    /** A pick is being resolved to coordinates. */
    val resolving: Boolean = false,
    /** Index into [rows] moved with the arrow keys; -1 = none, so Enter means "use the text". */
    val highlight: Int = -1,
) {
    /** From 2 characters the server is asked; below that only this trip's places are filtered. */
    val typing: Boolean get() = normalizeSearch(query).length >= PLACE_QUERY_MIN

    /** This trip's places that match the text: up to [USED_BEFORE_TYPING] when the text is empty, up to [USED_WHILE_TYPING] otherwise. */
    val used: List<UsedPlace>
        get() {
            val q = normalizeSearch(query)
            if (q.isEmpty()) return usedAll.take(USED_BEFORE_TYPING)
            return usedAll.filter { normalizeSearch(it.choice.name).contains(q) }.take(if (typing) USED_WHILE_TYPING else USED_BEFORE_TYPING)
        }

    /** Google's suggestions without the ones that are already shown as this trip's places. */
    val googleRows: List<PlaceSuggestionDto>
        get() {
            val shown = used.mapNotNull { it.choice.placeId }.toSet()
            return suggestions.filter { it.placeId == null || it.placeId !in shown }
        }

    val rows: List<PlacePickerRow>
        get() = used.map { PlacePickerRow.Used(it) } + if (typing) googleRows.map { PlacePickerRow.Suggestion(it) } else emptyList()

    companion object {
        const val USED_BEFORE_TYPING = 5
        const val USED_WHILE_TYPING = 2
    }
}

/**
 * The place picker of the activity form (spec "Field Tempat di form aktivitas"): suggestions 300 ms after the last key
 * from 2 characters (the older request is cancelled) in the ACTIVITY scope, biased to the trip's destination point;
 * one session token per opening, rotated after a pick so Google bills the session once. Free text is always a way out:
 * [useTyped]. Open to guests (owner, 12 Nov 2026): the server needs no account for place search. Offline and a broken
 * server leave only this trip's places and free text.
 */
class PlacePickerViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val trips: TripRepository,
    private val itinerary: ItineraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlacePickerUiState())
    val state: StateFlow<PlacePickerUiState> = _state.asStateFlow()

    private val _picked = Channel<PlaceChoice>(Channel.BUFFERED)
    val picked = _picked.receiveAsFlow()

    private var sessionToken = newId()
    private var searchJob: Job? = null

    /** The trip's destination point: results near it come first (a bias in the server's ranking, not a filter). */
    private var near: Pair<Double, Double>? = null

    init {
        viewModelScope.launch {
            val trip = trips.observeTrip(tripId).first()
            near = trip?.destinationPlace?.let { it.lat to it.lng }
            val start = trip?.startDate
            val used = itinerary.observe(tripId).first()
                .filter { !it.placeName.isNullOrBlank() }
                .sortedByDescending { it.updatedAt }
                .map { item ->
                    val day = item.dayDate?.let { d -> start?.let { s -> s.daysUntil(d) + 1 } }?.takeIf { it >= 1 }
                    UsedPlace(PlaceChoice(item.placeName.orEmpty(), null, item.placeId, item.lat, item.lng), day)
                }
                // one row per place: the same placeId, or the same name when there is no point
                .distinctBy { it.choice.placeId ?: normalizeSearch(it.choice.name) }
            _state.update { it.copy(usedAll = used) }
        }
    }

    fun onQuery(text: String) {
        _state.update { it.copy(query = text, highlight = -1) }
        searchJob?.cancel()
        if (normalizeSearch(text).length < PLACE_QUERY_MIN) {
            _state.update { it.copy(suggestions = emptyList(), searching = false, searched = false) }
            return
        }
        _state.update { it.copy(searching = true, searched = false) }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            when (val result = api.placeAutocomplete(text.trim(), sessionToken, near, PlaceSearchScope.ACTIVITY)) {
                is ApiResult.Success -> _state.update {
                    // `fallback`: Google is down and the server has nothing better to offer for an activity
                    if (result.value.fallback) it.copy(suggestions = emptyList(), mode = PlaceSearchMode.UNAVAILABLE)
                    else it.copy(suggestions = result.value.suggestions, mode = PlaceSearchMode.READY)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(suggestions = emptyList(), mode = if (result.error.status == null) PlaceSearchMode.OFFLINE else PlaceSearchMode.UNAVAILABLE)
                }
            }
            _state.update { it.copy(searching = false, searched = true) }
        }
    }

    /** ↑ / ↓ on the web: moves over [PlacePickerUiState.rows]; from "none" down goes to the first row. */
    fun moveHighlight(delta: Int) = _state.update {
        val last = it.rows.lastIndex
        if (last < 0) it else it.copy(highlight = (it.highlight + delta).coerceIn(-1, last))
    }

    /** Enter: the highlighted row, else the typed text. Nothing is picked on its own. */
    fun enter() {
        val s = _state.value
        when (val row = s.rows.getOrNull(s.highlight)) {
            is PlacePickerRow.Used -> pickUsed(row.place)
            is PlacePickerRow.Suggestion -> pickSuggestion(row.suggestion)
            null -> useTyped()
        }
    }

    /** The typed text as it is ("Pakai '<text>'"): no map point, no Google call. */
    fun useTyped() {
        val name = _state.value.query.trim()
        if (name.isNotEmpty()) finish(PlaceChoice(name))
    }

    /** A place of this trip: name, id and point as the activity had them, nothing to ask the server. */
    fun pickUsed(place: UsedPlace) = finish(place.choice)

    /** Asks for the point once (scope ACTIVITY: no time zone); without it the name still works as free text. */
    fun pickSuggestion(suggestion: PlaceSuggestionDto) {
        val placeId = suggestion.placeId
        if (placeId == null) {
            finish(PlaceChoice(suggestion.name, suggestion.secondary, fromSuggestion = true))
            return
        }
        if (_state.value.resolving) return
        _state.update { it.copy(resolving = true) }
        viewModelScope.launch {
            val place = (api.place(placeId, sessionToken, PlaceSearchScope.ACTIVITY) as? ApiResult.Success)?.value
            _state.update { it.copy(resolving = false) }
            finish(PlaceChoice(suggestion.name, suggestion.secondary, place?.placeId, place?.lat, place?.lng, fromSuggestion = true))
        }
    }

    /**
     * "Gunakan lokasi saat ini": the area name from /places/reverse ("Sekitar Kemang") with the device's own point and no
     * placeId (owner and Benn, 12 Nov 2026). [aroundLabel] is "Sekitar %s" in the UI language.
     */
    fun useLocation(aroundLabel: String, request: suspend () -> LocationFix) {
        if (_state.value.location == LocationState.Locating) return
        _state.update { it.copy(location = LocationState.Locating) }
        viewModelScope.launch {
            when (val fix = request()) {
                is LocationFix.Failed -> _state.update {
                    it.copy(location = if (fix.code == ErrorCode.LOCATION_DENIED) LocationState.Denied else LocationState.Failed(fix.code))
                }
                is LocationFix.Found -> when (val result = api.reverseGeocode(fix.lat, fix.lng)) {
                    is ApiResult.Success -> {
                        val area = result.value.areas.firstOrNull()
                        if (area == null) {
                            _state.update { it.copy(location = LocationState.Failed(ErrorCode.PLACES_UNAVAILABLE)) }
                        } else {
                            _state.update { it.copy(location = LocationState.Idle) }
                            finish(PlaceChoice(aroundLabel.replace("%s", area.name), area.secondary, null, fix.lat, fix.lng))
                        }
                    }
                    is ApiResult.Failure -> _state.update { it.copy(location = LocationState.Failed(result.error.code)) }
                }
            }
        }
    }

    private fun finish(choice: PlaceChoice) {
        sessionToken = newId()
        viewModelScope.launch { _picked.send(choice) }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}
