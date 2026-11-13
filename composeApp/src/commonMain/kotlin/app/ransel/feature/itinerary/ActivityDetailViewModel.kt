package app.ransel.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.shared.domain.activityLinkHost
import app.ransel.shared.domain.activityLinkLabel
import app.ransel.shared.domain.activityLinks
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One clickable row of "Detail aktivitas": the automatic [label] ("Booking.com"), the full [host] and the stored [url]. */
data class ActivityLinkRow(val url: String, val label: String, val host: String)

data class ActivityDetailState(
    val loading: Boolean = true,
    val item: ItineraryItemDto? = null,
    val links: List<ActivityLinkRow> = emptyList(),
    /** Editors and owners see "Edit"; viewers read the details and open the links. */
    val canEdit: Boolean = false,
) {
    /** The loaded activity is gone (deleted from the form, or by another member): the screen closes. */
    val gone: Boolean get() = !loading && item == null
}

/**
 * The read-only "Detail aktivitas" screen (spec "Banyak link di aktivitas"): what the timeline row shows, the notes and
 * the links as rows that open outside the app. Editing is the existing form behind the Edit button.
 */
class ActivityDetailViewModel(
    tripId: String,
    private val itemId: String,
    trips: TripRepository,
    itinerary: ItineraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityDetailState())
    val state: StateFlow<ActivityDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(itinerary.observe(tripId), trips.observeTrip(tripId)) { items, trip ->
                val item = items.firstOrNull { it.id == itemId && it.deletedAt == null }
                ActivityDetailState(
                    loading = false,
                    item = item,
                    links = item?.activityLinks().orEmpty().map { ActivityLinkRow(it, activityLinkLabel(it), activityLinkHost(it)) },
                    canEdit = trip?.myRole?.atLeast(Role.EDITOR) == true,
                )
            }.collect { _state.value = it }
        }
    }
}
