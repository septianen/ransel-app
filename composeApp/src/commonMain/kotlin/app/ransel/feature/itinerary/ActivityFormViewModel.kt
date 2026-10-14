package app.ransel.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.activityDurationMinutes
import app.ransel.shared.domain.endsNextDay
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.util.newId
import app.ransel.util.plusDays
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil

data class ActivityFormState(
    val isEditing: Boolean,
    val loading: Boolean = true,
    val tripTitle: String = "",
    val days: List<LocalDate> = emptyList(),
    val title: String = "",
    val category: Category = Category.ATTRACTION,
    /** null = "Belum dijadwalkan". */
    val day: LocalDate? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val placeName: String = "",
    val notes: String = "",
    val bookingUrl: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    val titleError: Boolean get() = showErrors && title.isBlank()
    /** End earlier than start = the activity ends the next day (23:00–01:00 = 2 h). */
    val endsNextDay: Boolean get() = endsNextDay(start, end)
    val durationMin: Int? get() = activityDurationMinutes(start, end)?.takeIf { it > 0 }
}

sealed interface ActivityFormEvent {
    data object Saved : ActivityFormEvent
    data object Deleted : ActivityFormEvent
}

class ActivityFormViewModel(
    private val tripId: String,
    private val itemId: String?,
    initialDay: String?,
    private val trips: TripRepository,
    private val itinerary: ItineraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityFormState(isEditing = itemId != null, day = initialDay?.let(LocalDate::parse)))
    val state: StateFlow<ActivityFormState> = _state.asStateFlow()
    private val events = Channel<ActivityFormEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var original: ItineraryItemDto? = null

    init {
        viewModelScope.launch {
            val trip: TripDto? = trips.observeTrip(tripId).first()
            val days = trip?.let { t -> (0..t.startDate.daysUntil(t.endDate)).map { t.startDate.plusDays(it) } }.orEmpty()
            val item = itemId?.let { id -> itinerary.observe(tripId).first().firstOrNull { it.id == id } }
            original = item
            _state.update { s ->
                if (item == null) {
                    s.copy(loading = false, tripTitle = trip?.title.orEmpty(), days = days, day = s.day ?: days.firstOrNull())
                } else {
                    s.copy(
                        loading = false, tripTitle = trip?.title.orEmpty(), days = days,
                        title = item.title, category = item.category, day = item.dayDate,
                        start = item.startTime, end = item.endTime, placeName = item.placeName.orEmpty(),
                        notes = item.notes.orEmpty(), bookingUrl = item.bookingUrl.orEmpty(),
                    )
                }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onCategory(v: Category) = _state.update { it.copy(category = v) }
    fun onDay(v: LocalDate?) = _state.update { it.copy(day = v) }
    fun onStart(v: LocalTime?) = _state.update { it.copy(start = v) }
    fun onEnd(v: LocalTime?) = _state.update { it.copy(end = v) }
    fun onPlace(v: String) = _state.update { it.copy(placeName = v) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }
    fun onBooking(v: String) = _state.update { it.copy(bookingUrl = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.title.isBlank()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val siblings = itinerary.observe(tripId).first().filter { it.dayDate == s.day && it.id != itemId }
            val previous = original
            val startTime = if (s.day != null) s.start else null
            // Keep the place when nothing that decides it changed (so a title edit never reshuffles a
            // day the user arranged by hand); otherwise slot it in by start time.
            val unchanged = previous != null && previous.dayDate == s.day && previous.startTime == startTime
            val sortOrder = if (unchanged) previous!!.sortOrder else sortOrderForStart(siblings, startTime)
            val scheduled = s.day != null
            itinerary.save(
                tripId,
                ItineraryItemInput(
                    id = itemId ?: newId(),
                    dayDate = s.day,
                    // Unscheduled places carry a duration, not clock times.
                    startTime = if (scheduled) s.start else null,
                    endTime = if (scheduled) s.end else null,
                    title = s.title.trim(),
                    category = s.category,
                    placeName = s.placeName.trim().ifEmpty { null },
                    placeId = previous?.placeId,
                    lat = previous?.lat,
                    lng = previous?.lng,
                    durationMin = s.durationMin ?: previous?.durationMin,
                    notes = s.notes.trim().ifEmpty { null },
                    bookingUrl = s.bookingUrl.trim().ifEmpty { null },
                    source = previous?.source ?: ItemSource.MANUAL,
                    sortOrder = sortOrder,
                ),
            )
            events.send(ActivityFormEvent.Saved)
        }
    }

    fun delete() {
        val id = itemId ?: return
        viewModelScope.launch {
            itinerary.delete(tripId, id)
            events.send(ActivityFormEvent.Deleted)
        }
    }
}
