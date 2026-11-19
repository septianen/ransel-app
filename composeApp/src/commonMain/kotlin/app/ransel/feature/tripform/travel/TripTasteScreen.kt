package app.ransel.feature.tripform.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.feature.interview.InterviewBottomBar
import app.ransel.feature.interview.KhasState
import app.ransel.feature.interview.OfflineBanner
import app.ransel.feature.interview.TasteFields
import app.ransel.navigation.SheetBackHandler
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.canSaveFromSheet
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.interview_save
import ransel.composeapp.generated.resources.trip_answer_taste_title

/**
 * Edit trip → Your taste: the same chrome as Starting city / Getting there (back, title, scroll, pinned Save),
 * not a sheet. Save still needs at least one interest.
 */
@Composable
fun TripTasteScreen(
    tripId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripTravelViewModel = koinViewModel(key = "trip-travel-$tripId") { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.trip?.id) {
        if (state.trip != null && state.draft == null) viewModel.openTasteSheet()
    }
    val close = {
        viewModel.closeTasteSheet()
        onBack()
    }
    SheetBackHandler(close)
    val trip = state.trip
    TripTasteContent(
        destination = trip?.destination.orEmpty(),
        draft = state.draft ?: InterviewTaste(),
        khas = state.khas,
        defaultPrice = state.budgetPriceLevel ?: PriceLevel.MEDIUM,
        priceFromBudget = state.budgetPriceLevel != null,
        loading = trip == null,
        offline = false,
        onInterest = viewModel::draftToggleInterest,
        onExtra = viewModel::draftToggleExtra,
        onStyle = viewModel::draftStyle,
        onPrice = viewModel::draftPriceLevel,
        onNotes = viewModel::draftNotes,
        onSave = {
            viewModel.saveTasteSheet()
            if (viewModel.state.value.draft == null) onSaved()
        },
        onBack = close,
        modifier = modifier,
    )
}

@Composable
internal fun TripTasteContent(
    destination: String,
    draft: InterviewTaste,
    khas: KhasState,
    defaultPrice: PriceLevel,
    priceFromBudget: Boolean,
    loading: Boolean,
    offline: Boolean,
    onInterest: (Interest) -> Unit,
    onExtra: (String) -> Unit,
    onStyle: (TravelStyle) -> Unit,
    onPrice: (PriceLevel) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back), tint = scheme.onSurface)
            }
            Text(
                stringResource(Res.string.trip_answer_taste_title),
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
        }
        if (offline) OfflineBanner()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                TasteFields(
                    draft = draft,
                    khas = khas,
                    destination = destination,
                    onInterest = onInterest,
                    onExtra = onExtra,
                    onStyle = onStyle,
                    onPrice = onPrice,
                    onNotes = onNotes,
                    defaultPrice = defaultPrice,
                    priceFromBudget = priceFromBudget,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        InterviewBottomBar(
            primaryLabel = stringResource(Res.string.interview_save),
            primaryEnabled = draft.canSaveFromSheet() && !loading,
            onPrimary = onSave,
            elevated = false,
        )
    }
}

@Preview
@Composable
private fun TripTasteContentPreview() = IrisPreview {
    TripTasteContent(
        destination = "Bandung",
        draft = InterviewTaste(),
        khas = KhasState.Hidden,
        defaultPrice = PriceLevel.MEDIUM,
        priceFromBudget = false,
        loading = false,
        offline = false,
        onInterest = {},
        onExtra = {},
        onStyle = {},
        onPrice = {},
        onNotes = {},
        onSave = {},
        onBack = {},
    )
}
