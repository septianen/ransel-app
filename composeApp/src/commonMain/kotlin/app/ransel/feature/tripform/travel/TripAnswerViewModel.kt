package app.ransel.feature.tripform.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.TripRepository
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.sync.SyncStatus
import app.ransel.data.travel.AnswerChange
import app.ransel.data.travel.TravelAnswerChanges
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.interview.OriginFound
import app.ransel.feature.interview.OriginLocation
import app.ransel.feature.interview.InterviewUiState
import app.ransel.feature.interview.findOriginCity
import app.ransel.platform.LocationFix
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.TravelEdit
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.domain.changeDeparture
import app.ransel.shared.domain.changeLocalTransport
import app.ransel.shared.domain.changeOrigin
import app.ransel.shared.domain.cityDisplayName
import app.ransel.shared.domain.restoreRequest
import app.ransel.shared.domain.travelAnswers
import app.ransel.shared.domain.updateRequestFor
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.LocalTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen of one answer shows: the trip, the choice picked so far (the current answer at first), and the location search. */
data class TripAnswerUiState(
    val row: TravelRow,
    val loading: Boolean = true,
    val trip: TripDto? = null,
    val origin: TripOriginDto? = null,
    val departure: DepartureMode? = null,
    val local: LocalTransport? = null,
    val location: OriginLocation = OriginLocation.Idle,
    val signedIn: Boolean = false,
    val offline: Boolean = false,
    val saving: Boolean = false,
) {
    /** The answers of W1 to W3 as the question screens want them: the trip's, with the pick of this screen in place. */
    fun interviewState(): InterviewUiState {
        val trip = trip ?: return InterviewUiState(door = InterviewDoor.MY_TRIPS)
        val answers = trip.travelAnswers().copy(origin = origin, departure = departure, localTransport = local)
        return InterviewUiState(
            door = InterviewDoor.MY_TRIPS,
            answers = answers,
            // the facts of the cities as they would be after the pick (a new starting city changes the train choice)
            facts = app.ransel.shared.domain.interviewCityFacts(answers.origin, trip.destination),
            signedIn = signedIn,
            offline = offline,
            lastOrigin = trip.origin?.let { it.copy(name = cityDisplayName(it.name)) },
            location = location,
        )
    }

    /** Simpan is on when there is a pick (a screen of "Cara keliling" after an own vehicle starts empty). */
    val hasChoice: Boolean
        get() = when (row) {
            TravelRow.ORIGIN -> origin != null
            TravelRow.DEPARTURE -> departure != null
            TravelRow.LOCAL_TRANSPORT -> local != null
            TravelRow.TASTE -> false
        }

    /** The pick is what the trip has already: Simpan only closes the screen. */
    val unchanged: Boolean
        get() {
            val trip = trip ?: return true
            return when (row) {
                TravelRow.ORIGIN -> origin == trip.origin?.let { it.copy(name = cityDisplayName(it.name)) } || origin == trip.origin
                TravelRow.DEPARTURE -> departure == trip.departureMode
                TravelRow.LOCAL_TRANSPORT -> local == trip.localTransport
                TravelRow.TASTE -> true
            }
        }
}

sealed interface TripAnswerEvent {
    /** Saved (or nothing to save): back to Edit trip. */
    data object Done : TripAnswerEvent

    /** Saved; an own vehicle was replaced, so "Cara keliling" opens straight away (mockup s26-2c). */
    data object OpenLocalTransport : TripAnswerEvent
}

/**
 * One answer of the section "Perjalanan" in its own screen (spec 1638556): "Kota asal", "Cara berangkat" or "Cara keliling", the
 * W1-W3 screens of the interview in "mode ubah". "Simpan" stores the change in the trip at once (local first, synced), with the
 * rules of `shared/domain/TripTravel.kt`, and tells Edit trip what to say about it ([TravelAnswerChanges]).
 */
class TripAnswerViewModel(
    private val tripId: String,
    private val row: TravelRow,
    private val trips: TripRepository,
    private val api: RanselApi?,
    private val session: SessionManager?,
    private val changes: TravelAnswerChanges,
    private val sync: SyncStatus = NoSyncStatus,
) : ViewModel() {
    private val _state = MutableStateFlow(TripAnswerUiState(row = row, signedIn = session?.user != null))
    val state: StateFlow<TripAnswerUiState> = _state.asStateFlow()

    private val events = Channel<TripAnswerEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val trip = trips.observeTrip(tripId).filterNotNull().first()
            _state.update {
                it.copy(
                    loading = false, trip = trip, origin = trip.origin?.let { o -> o.copy(name = cityDisplayName(o.name)) },
                    departure = trip.departureMode, local = trip.localTransport,
                )
            }
        }
        viewModelScope.launch { sync.offline.collect { off -> _state.update { it.copy(offline = off) } } }
    }

    fun pickOrigin(origin: TripOriginDto) = _state.update { it.copy(origin = origin) }

    fun onOriginPicked(choice: DestinationChoice) =
        pickOrigin(TripOriginDto(cityDisplayName(choice.name), choice.place?.lat, choice.place?.lng, choice.place?.placeId, choice.countryCode))

    fun chooseDeparture(mode: DepartureMode) = _state.update { it.copy(departure = mode) }
    fun chooseLocalTransport(choice: LocalTransport) = _state.update { it.copy(local = choice) }

    /** "Gunakan lokasi saat ini": the same search as W1. */
    fun useCurrentLocation(request: suspend () -> LocationFix) {
        val api = api ?: return
        val s = _state.value
        if (!s.signedIn || s.offline || s.location == OriginLocation.Locating) return
        _state.update { it.copy(location = OriginLocation.Locating) }
        viewModelScope.launch {
            val next = when (val found = findOriginCity(api, request)) {
                is OriginFound.City -> {
                    pickOrigin(found.origin)
                    OriginLocation.Idle
                }
                OriginFound.Denied -> OriginLocation.Denied
                OriginFound.Failed -> OriginLocation.Failed
            }
            _state.update { it.copy(location = next) }
        }
    }

    /** Simpan. No change: only closes. Otherwise the change is stored at once and Edit trip is told. */
    fun save() {
        val s = _state.value
        val trip = s.trip ?: return
        if (s.saving || !s.hasChoice) return
        if (s.unchanged) {
            viewModelScope.launch { events.send(TripAnswerEvent.Done) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val edit: TravelEdit = when (row) {
                TravelRow.ORIGIN -> trip.changeOrigin(s.origin)
                TravelRow.DEPARTURE -> trip.changeDeparture(s.departure)
                TravelRow.LOCAL_TRANSPORT -> trip.changeLocalTransport(s.local)
                TravelRow.TASTE -> return@launch
            }
            trips.update(tripId, trip.updateRequestFor(edit))
            changes.post(
                AnswerChange(
                    tripId = tripId, row = row, undo = trip.restoreRequest(),
                    resetDeparture = edit.departureReset.takeIf { row == TravelRow.ORIGIN },
                ),
            )
            events.send(if (edit.askLocalTransportNext && row == TravelRow.DEPARTURE) TripAnswerEvent.OpenLocalTransport else TripAnswerEvent.Done)
        }
    }

}
