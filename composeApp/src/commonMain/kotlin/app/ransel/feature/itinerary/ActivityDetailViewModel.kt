package app.ransel.feature.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.TripRepository
import app.ransel.feature.explore.PlaceDetailInfo
import app.ransel.feature.explore.toDetailInfo
import app.ransel.shared.domain.activityLinkHost
import app.ransel.shared.domain.activityLinkLabel
import app.ransel.shared.domain.activityLinks
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One clickable row of "Detail aktivitas": the automatic [label] ("Booking.com"), the full [host] and the stored [url]. */
data class ActivityLinkRow(val url: String, val label: String, val host: String)

/** The place part of the screen (photo, cost, best time, About, Good to know, Opening hours). */
sealed interface PlaceText {
    /** No place id, a guest, or not asked yet: the screen shows the activity alone. */
    data object None : PlaceText
    data object Loading : PlaceText
    data object Failed : PlaceText
    data class Loaded(val info: PlaceDetailInfo) : PlaceText
    /** An item made from an idea (plan idea-activity-detail): the idea card fills the sections; [placeId] = the region's. */
    data class Idea(val card: app.ransel.shared.dto.InspirationCardDto, val placeId: String? = null) : PlaceText
}

data class ActivityDetailState(
    val loading: Boolean = true,
    val item: ItineraryItemDto? = null,
    val trip: TripDto? = null,
    val links: List<ActivityLinkRow> = emptyList(),
    /** Editors and owners see Ubah and ⋮ (Hapus); viewers read the details and open the links. */
    val canEdit: Boolean = false,
    val place: PlaceText = PlaceText.None,
) {
    /** The loaded activity is gone (deleted here, from the form, or by another member): the screen closes. */
    val gone: Boolean get() = !loading && item == null

    /** "Jadwalkan" on an unscheduled activity: an editor on a trip with days. */
    val canSchedule: Boolean
        get() = canEdit && item?.dayDate == null && trip?.startDate != null && trip.endDate != null
}

/**
 * One Activity details screen for every itinerary card (plan activity-detail-unify, owner 23 Nov 2026): the activity's own
 * data from the itinerary, and the place's text when there is a Google place id. Opening it never charges credits, and a
 * place the user picked by hand never starts an AI call: it asks the server's saved text only (`cacheOnly`). A place that
 * came from an AI search is asked as the old "i" button did (the text was saved with the search).
 */
class ActivityDetailViewModel(
    private val tripId: String,
    private val itemId: String,
    trips: TripRepository,
    private val itinerary: ItineraryRepository,
    /** Null for a guest (no account to ask with) and in tests that do not need the place text. */
    private val api: RanselApi? = null,
    private val signedIn: () -> Boolean = { api != null },
    /** The idea card of an item made from an idea, while it is only on this device. */
    private val ideas: app.ransel.data.explore.ItineraryIdeas? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityDetailState())
    val state: StateFlow<ActivityDetailState> = _state.asStateFlow()

    /** The place id the text was asked for: an edit of the title or time does not ask again. */
    private var askedFor: String? = null

    init {
        viewModelScope.launch {
            combine(itinerary.observe(tripId), trips.observeTrip(tripId)) { items, trip ->
                items.firstOrNull { it.id == itemId && it.deletedAt == null } to trip
            }.collect { (item, trip) ->
                _state.update {
                    it.copy(
                        loading = false,
                        item = item,
                        trip = trip,
                        links = item?.activityLinks().orEmpty().map { url -> ActivityLinkRow(url, activityLinkLabel(url), activityLinkHost(url)) },
                        canEdit = trip?.myRole?.atLeast(Role.EDITOR) == true,
                        place = if (item == null || (item.placeId == null && !item.mayHaveIdea())) PlaceText.None else it.place,
                    )
                }
                if (item?.placeId != null && item.placeId != askedFor) loadPlace(item, trip)
                else if (item != null && item.placeId == null && item.mayHaveIdea() && askedFor != ideaKey(item)) loadIdea(item)
            }
        }
    }

    fun retryPlace() {
        val s = _state.value
        val item = s.item ?: return
        askedFor = null
        if (item.placeId == null && item.mayHaveIdea()) loadIdea(item) else loadPlace(item, s.trip)
    }

    /** Only AI items without a Google place can come from an idea (swipe items always have one). */
    private fun ItineraryItemDto.mayHaveIdea() = source == ItemSource.AI

    private fun ideaKey(item: ItineraryItemDto) = "idea:${item.id}"

    /**
     * The idea card: the server's (every device and member), else the copy still on this device, which is uploaded now.
     * No AI call and no credit either way; NOT_FOUND = an AI item without an idea, shown as the activity alone.
     */
    private fun loadIdea(item: ItineraryItemDto) {
        askedFor = ideaKey(item)
        viewModelScope.launch {
            val local = ideas?.local(item.id)
            val api = api
            if (api == null || !signedIn()) {
                _state.update { it.copy(place = local?.let { card -> PlaceText.Idea(card) } ?: PlaceText.None) }
                return@launch
            }
            if (local == null) _state.update { it.copy(place = PlaceText.Loading) } else _state.update { it.copy(place = PlaceText.Idea(local)) }
            val result = api.itineraryIdea(tripId, item.id)
            _state.update {
                it.copy(
                    place = when {
                        result is ApiResult.Success -> PlaceText.Idea(result.value.card, result.value.placeId)
                        local != null -> PlaceText.Idea(local)
                        (result as ApiResult.Failure).error.isNotFound -> PlaceText.None
                        else -> PlaceText.Failed
                    },
                )
            }
            if (result !is ApiResult.Success && local != null) ideas?.push(tripId)
        }
    }

    /** Hapus from ⋮ (after the confirm dialog): the screen closes by itself once the item is gone. */
    fun delete() {
        viewModelScope.launch { itinerary.delete(tripId, itemId) }
    }

    private fun loadPlace(item: ItineraryItemDto, trip: TripDto?) {
        val placeId = item.placeId ?: return
        val api = api ?: return
        if (!signedIn()) return
        askedFor = placeId
        _state.update { it.copy(place = PlaceText.Loading) }
        viewModelScope.launch {
            val result = api.placeDetail(
                placeId, name = item.placeName?.takeIf { it.isNotBlank() } ?: item.title, currency = trip?.baseCurrency,
                // a hand-picked place: no AI call, only what the server already has (plan activity-detail-unify)
                cacheOnly = item.source != ItemSource.AI,
            )
            _state.update {
                it.copy(
                    place = when (result) {
                        is ApiResult.Success -> PlaceText.Loaded(result.value.toDetailInfo().copy(category = item.category))
                        is ApiResult.Failure -> PlaceText.Failed
                    },
                )
            }
        }
    }
}
