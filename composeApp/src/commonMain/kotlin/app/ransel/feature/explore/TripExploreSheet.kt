package app.ransel.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.credits.rememberCredits
import app.ransel.feature.group.ExplorePrice
import app.ransel.feature.group.explorePrice
import app.ransel.feature.interview.InterviewBottomBar
import app.ransel.feature.interview.KhasState
import app.ransel.feature.interview.TasteFields
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.SkeletonCards
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.trip_explore_group_line
import ransel.composeapp.generated.resources.trip_explore_search
import ransel.composeapp.generated.resources.trip_explore_search_credits
import ransel.composeapp.generated.resources.trip_explore_search_free
import ransel.composeapp.generated.resources.trip_explore_sheet_title

/**
 * "Cari tempat" from a trip (plan explore-trip-one-tap v4): the Seleramu sheet filled in from the trip, the search price
 * on its button. [onSearch] gets the trip id once the session is stored; the swipe screen does the rest.
 */
@Composable
fun TripExploreSheet(
    tripId: String,
    onSearch: (tripId: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TripExploreViewModel = koinViewModel(key = "trip-explore-$tripId") { parametersOf(tripId) },
) {
    LaunchedEffect(viewModel) { viewModel.open() }
    val state by viewModel.state.collectAsState()
    val price = explorePrice(rememberCredits().value, resumes = false)
    TripExploreContent(
        state = state,
        price = price,
        onInterest = viewModel::toggleInterest,
        onExtra = viewModel::toggleExtra,
        onStyle = viewModel::setStyle,
        onPrice = viewModel::setPriceLevel,
        onNotes = viewModel::setNotes,
        onSearch = { viewModel.search(onSearch) },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun TripExploreContent(
    state: TripExploreState,
    price: ExplorePrice?,
    onInterest: (Interest) -> Unit,
    onExtra: (String) -> Unit,
    onStyle: (TravelStyle) -> Unit,
    onPrice: (PriceLevel) -> Unit,
    onNotes: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val destination = state.trip?.destination.orEmpty()
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // v1 searches the trip's destination only: the city is part of the title, not a field
            Text(
                stringResource(Res.string.trip_explore_sheet_title, destination),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.loading) {
                SkeletonCards(cards = 2, padding = 0.dp)
            } else {
                TasteFields(
                    draft = state.draft,
                    khas = state.khas,
                    destination = destination,
                    onInterest = onInterest,
                    onExtra = onExtra,
                    onStyle = onStyle,
                    onPrice = onPrice,
                    onNotes = onNotes,
                    defaultPrice = state.budgetPriceLevel ?: PriceLevel.MEDIUM,
                    priceFromBudget = state.budgetPriceLevel != null,
                )
            }
        }
        InterviewBottomBar(
            primaryLabel = when (price) {
                is ExplorePrice.Credits -> stringResource(Res.string.trip_explore_search_credits, price.amount)
                ExplorePrice.FreeToday -> stringResource(Res.string.trip_explore_search_free)
                null -> stringResource(Res.string.trip_explore_search)
            },
            primaryEnabled = state.canSearch,
            primaryLoading = state.starting,
            onPrimary = onSearch,
            note = if (state.group) stringResource(Res.string.trip_explore_group_line) else null,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val previewTrip = app.ransel.shared.dto.TripDto(
    id = "t1", ownerId = "u1", title = "Jepang musim gugur", destination = "Kyoto", baseCurrency = "IDR",
    createdAt = kotlin.time.Instant.parse("2026-11-01T00:00:00Z"), updatedAt = kotlin.time.Instant.parse("2026-11-01T00:00:00Z"),
)

@Preview
@Composable
private fun TripExploreFilledPreview() = IrisPreview {
    TripExploreContent(
        state = TripExploreState(
            loading = false, trip = previewTrip, group = true,
            draft = InterviewTaste(interests = setOf(Interest.FOOD, Interest.CULTURE), extraInterests = setOf("Momiji"), style = TravelStyle.SANTAI),
            khas = KhasState.Loaded(listOf("Momiji", "Onsen", "Kuil"), "Kyoto"),
        ),
        price = ExplorePrice.Credits(3),
        onInterest = {}, onExtra = {}, onStyle = {}, onPrice = {}, onNotes = {}, onSearch = {}, onDismiss = {},
    )
}

@Preview
@Composable
private fun TripExploreEmptyPreview() = IrisPreview {
    TripExploreContent(
        state = TripExploreState(loading = false, trip = previewTrip),
        price = ExplorePrice.FreeToday,
        onInterest = {}, onExtra = {}, onStyle = {}, onPrice = {}, onNotes = {}, onSearch = {}, onDismiss = {},
    )
}
