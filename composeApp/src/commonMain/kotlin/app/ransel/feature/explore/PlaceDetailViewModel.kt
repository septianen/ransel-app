package app.ransel.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.explore.ExploreCard
import app.ransel.data.explore.ExploreSessionStore
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.navigation.PlaceDetailRoute
import app.ransel.shared.dto.OpeningHoursLine
import app.ransel.shared.dto.PlaceDetailDto
import app.ransel.shared.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the detail page shows about one place, whichever way it was opened: from a swipe card (all of it is on
 * the card) or from the itinerary (loaded by place id; [category], [reason] and [priceLevel] are not known then).
 */
data class PlaceDetailInfo(
    val placeId: String,
    val name: String,
    val category: Category? = null,
    val reason: String? = null,
    val priceLevel: Int? = null,
    val durationMin: Int? = null,
    val address: String? = null,
    val photoUrl: String? = null,
    val photoAttribution: String? = null,
    val about: String? = null,
    val highlights: List<String> = emptyList(),
    val openingHours: List<OpeningHoursLine> = emptyList(),
    val bestTime: String? = null,
    val estimatedCostMinor: Long? = null,
    val costCurrency: String? = null,
)

internal fun ExploreCard.toDetailInfo() = PlaceDetailInfo(
    placeId = item.placeId,
    name = item.name,
    category = category,
    reason = item.reason,
    priceLevel = item.priceLevel,
    durationMin = item.suggestedDurationMin,
    address = item.address,
    photoUrl = item.photoUrl,
    photoAttribution = item.photoAttribution,
    about = item.about,
    highlights = item.highlights,
    openingHours = item.openingHours,
    bestTime = item.bestTime,
    estimatedCostMinor = item.estimatedCostMinor,
    costCurrency = item.costCurrency,
)

internal fun PlaceDetailDto.toDetailInfo() = PlaceDetailInfo(
    placeId = placeId,
    name = name,
    address = address,
    photoUrl = photoUrl,
    photoAttribution = photoAttribution,
    about = about,
    highlights = highlights,
    openingHours = openingHours,
    bestTime = bestTime,
    estimatedCostMinor = estimatedCostMinor,
    costCurrency = costCurrency,
)

/** [info] null while loading (itinerary entry only; a swipe card already has everything). */
data class PlaceDetailUiState(
    val info: PlaceDetailInfo? = null,
    val loading: Boolean = false,
    val failure: ApiFailure? = null,
)

class PlaceDetailViewModel(
    private val route: PlaceDetailRoute,
    private val store: ExploreSessionStore,
    private val api: RanselApi,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaceDetailUiState())
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

    init {
        val card = if (route.fromItinerary) null else store.session(route.tripId)?.cards?.firstOrNull { it.item.placeId == route.placeId }
        if (card != null) _state.value = PlaceDetailUiState(info = card.toDetailInfo()) else load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = PlaceDetailUiState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = api.placeDetail(route.placeId, route.name, route.currency)) {
                is ApiResult.Success -> PlaceDetailUiState(info = result.value.toDetailInfo())
                is ApiResult.Failure -> PlaceDetailUiState(failure = result.error)
            }
        }
    }
}
