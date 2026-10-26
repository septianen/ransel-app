package app.ransel.feature.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.platform.LocationFix
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PLACE_QUERY_MIN
import app.ransel.shared.domain.normalizeSearch
import app.ransel.shared.domain.regionSuggestions
import app.ransel.shared.dto.NearbyAreaDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.shared.dto.PopularDestinationDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.util.newId
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** What the picker hands back: the name to show, and where it is when the server could tell. */
data class DestinationChoice(
    val name: String,
    val place: TripPlaceDto? = null,
    /** ISO country of the place, for overseas trips. */
    val countryCode: String? = null,
    /** IANA zone of the place. */
    val timeZone: String? = null,
)

/** One of the last ten destinations picked on this device ("Terakhir dipakai"). */
@Serializable
data class RecentDestination(val name: String, val secondary: String? = null, val place: TripPlaceDto? = null)

/** "Gunakan lokasi saat ini". */
sealed interface LocationState {
    data object Idle : LocationState
    data object Locating : LocationState
    data class Areas(val areas: List<NearbyAreaDto>) : LocationState
    /** Permission refused (design "DestinasiIzinDitolak"). */
    data object Denied : LocationState
    /** No fix or no answer; [code] is an ErrorCode. */
    data class Failed(val code: String) : LocationState
}

data class DestinationPickerUiState(
    val query: String = "",
    val suggestions: List<PlaceSuggestionDto> = emptyList(),
    /** Suggestions come from the built-in region list (offline, signed out, or Google down). */
    val fallback: Boolean = false,
    val searching: Boolean = false,
    val recents: List<RecentDestination> = emptyList(),
    val popular: List<PopularDestinationDto> = emptyList(),
    val location: LocationState = LocationState.Idle,
    /** A pick is being resolved to coordinates. */
    val resolving: Boolean = false,
    /** Signed in: the server can be asked (suggestions, location, popular). */
    val online: Boolean = false,
) {
    val typing: Boolean get() = normalizeSearch(query).length >= PLACE_QUERY_MIN
}

/**
 * Destination picker (designs "DestinasiKosong", "DestinasiSaran", "DestinasiGPS", "DestinasiIzinDitolak"): suggestions
 * 300 ms after the last key from 2 characters (the older request is cancelled), one session token per opening, rotated
 * after a pick so Google bills the session once; recents on the device.
 */
class DestinationPickerViewModel(
    private val api: RanselApi,
    private val session: SessionManager,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DestinationPickerUiState(online = session.user != null))
    val state: StateFlow<DestinationPickerUiState> = _state.asStateFlow()

    private val _picked = Channel<DestinationChoice>(Channel.BUFFERED)
    val picked = _picked.receiveAsFlow()

    private var sessionToken = newId()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val recents = preferences.getString(RECENTS_KEY)?.let {
                runCatching { ApiJson.decodeFromString(recentsSerializer, it) }.getOrNull()
            }.orEmpty()
            _state.update { it.copy(recents = recents) }
        }
        if (session.user != null) {
            viewModelScope.launch {
                (api.popularDestinations() as? ApiResult.Success)?.let { r -> _state.update { it.copy(popular = r.value) } }
            }
        }
    }

    fun onQuery(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        if (normalizeSearch(text).length < PLACE_QUERY_MIN) {
            _state.update { it.copy(suggestions = emptyList(), searching = false, fallback = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            if (session.user == null) {
                _state.update { it.copy(suggestions = regionSuggestions(text), fallback = true, searching = false) }
                return@launch
            }
            _state.update { it.copy(searching = true) }
            when (val result = api.placeAutocomplete(text.trim(), sessionToken)) {
                is ApiResult.Success -> _state.update {
                    it.copy(suggestions = result.value.suggestions, fallback = result.value.fallback, searching = false)
                }
                // offline: the built-in list still answers
                is ApiResult.Failure -> _state.update { it.copy(suggestions = regionSuggestions(text), fallback = true, searching = false) }
            }
        }
    }

    /** Typed text used as is ("Pakai '<text>'"): no map point. */
    fun useTyped() {
        val name = _state.value.query.trim()
        if (name.isNotEmpty()) finish(DestinationChoice(name), secondary = null)
    }

    fun pick(suggestion: PlaceSuggestionDto) = resolve(suggestion.placeId, suggestion.name, suggestion.secondary)

    fun pickRecent(recent: RecentDestination) = finish(DestinationChoice(recent.name, recent.place), recent.secondary)

    fun pickPopular(popular: PopularDestinationDto) = resolve(popular.placeId, popular.name, null)

    /** [aroundLabel] is "Sekitar %s" in the UI language, for the widest level. */
    fun pickArea(area: NearbyAreaDto, aroundLabel: String) =
        resolve(area.placeId, if (area.around) aroundLabel.replace("%s", area.name) else area.name, area.secondary)

    /** Called with the provider's result; the coordinates only go to /places/reverse. */
    fun useLocation(request: suspend () -> LocationFix) {
        if (session.user == null) return
        _state.update { it.copy(location = LocationState.Locating) }
        viewModelScope.launch {
            val next = when (val fix = request()) {
                is LocationFix.Failed ->
                    if (fix.code == ErrorCode.LOCATION_DENIED) LocationState.Denied else LocationState.Failed(fix.code)
                is LocationFix.Found -> when (val result = api.reverseGeocode(fix.lat, fix.lng)) {
                    is ApiResult.Success ->
                        if (result.value.areas.isEmpty()) LocationState.Failed(ErrorCode.PLACES_UNAVAILABLE) else LocationState.Areas(result.value.areas)
                    is ApiResult.Failure -> LocationState.Failed(result.error.code)
                }
            }
            _state.update { it.copy(location = next) }
        }
    }

    private fun resolve(placeId: String?, name: String, secondary: String?) {
        if (placeId == null || session.user == null) {
            finish(DestinationChoice(name), secondary)
            return
        }
        if (_state.value.resolving) return
        _state.update { it.copy(resolving = true) }
        viewModelScope.launch {
            // without coordinates the name still works: only Jelajah's centre is lost
            val place = (api.place(placeId, sessionToken) as? ApiResult.Success)?.value
            _state.update { it.copy(resolving = false) }
            finish(
                DestinationChoice(name, place?.let { TripPlaceDto(it.placeId, it.lat, it.lng, it.viewport) }, place?.countryCode, place?.timeZone),
                secondary,
            )
        }
    }

    private fun finish(choice: DestinationChoice, secondary: String?) {
        sessionToken = newId()
        val recents = (listOf(RecentDestination(choice.name, secondary, choice.place)) +
            _state.value.recents.filter { !it.name.equals(choice.name, ignoreCase = true) }).take(MAX_RECENTS)
        _state.update { it.copy(recents = recents) }
        viewModelScope.launch {
            preferences.putString(RECENTS_KEY, ApiJson.encodeToString(recentsSerializer, recents))
            _picked.send(choice)
        }
    }

    companion object {
        const val RECENTS_KEY = "recent_destinations"
        const val MAX_RECENTS = 10
        const val DEBOUNCE_MILLIS = 300L
        private val recentsSerializer = ListSerializer(RecentDestination.serializer())

        /** Kind label shown after a suggestion, for kinds the secondary text does not already say. */
        fun showsKind(kind: PlaceKind) = kind == PlaceKind.ATTRACTION || kind == PlaceKind.COUNTRY || kind == PlaceKind.REGENCY
    }
}
