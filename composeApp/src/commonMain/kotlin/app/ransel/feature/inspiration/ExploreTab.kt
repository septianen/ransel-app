package app.ransel.feature.inspiration

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.SavedIdeaDto
import app.ransel.ui.RefreshOnReturn
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.SkeletonList
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.flow.collect
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_undo
import ransel.composeapp.generated.resources.credits_error_generic
import ransel.composeapp.generated.resources.explore_ai_title
import ransel.composeapp.generated.resources.explore_home_title
import ransel.composeapp.generated.resources.saved_ideas_removed

/** What the Explore tab shows after the account's saved ideas are known. */
enum class ExploreTabBody {
    Loading,
    /** No saved ideas: the Ask AI form stays the tab itself. */
    Input,
    /** One or more saved ideas: the list, with a FAB for a new search. */
    SavedList,
}

fun exploreTabBody(state: SavedIdeasUiState): ExploreTabBody = when {
    state.ideas.isNotEmpty() -> ExploreTabBody.SavedList
    !state.loaded -> ExploreTabBody.Loading
    else -> ExploreTabBody.Input
}

@Composable
fun ExploreTabScreen(
    savedViewModel: SavedIdeasViewModel,
    homeViewModel: InspirationHomeViewModel,
    onStartInterview: (String) -> Unit,
    onOpenCredits: () -> Unit,
    onVerifyEmail: () -> Unit,
    onOpenSavedIdeas: () -> Unit,
    onAskAi: () -> Unit,
    onCreateTrip: (SavedIdeaDto) -> Unit,
) {
    val saved by savedViewModel.state.collectAsState()
    val snackHost = remember { SnackbarHostState() }
    val removedMessage = stringResource(Res.string.saved_ideas_removed)
    val undoLabel = stringResource(Res.string.action_undo)
    val genericError = stringResource(Res.string.credits_error_generic)
    LaunchedEffect(Unit) { savedViewModel.load() }
    RefreshOnReturn(savedViewModel::load)
    LaunchedEffect(savedViewModel) {
        savedViewModel.eventFlow.collect { event ->
            when (event) {
                is SavedIdeasEvent.Removed -> {
                    if (snackHost.showSnackbar(removedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        savedViewModel.undoRemove(event.id)
                    }
                }
                SavedIdeasEvent.Error -> snackHost.showSnackbar(genericError)
            }
        }
    }
    val body = exploreTabBody(saved)
    ExploreTabScaffold(
        snackbarHost = snackHost,
        showFab = body == ExploreTabBody.SavedList,
        onAskAi = onAskAi,
    ) {
        when (body) {
            ExploreTabBody.Loading -> SkeletonList(rows = 5)
            ExploreTabBody.Input -> InspirationHomeScreen(
                viewModel = homeViewModel,
                onStartInterview = onStartInterview,
                onOpenCredits = onOpenCredits,
                onVerifyEmail = onVerifyEmail,
                onOpenSavedIdeas = onOpenSavedIdeas,
            )
            ExploreTabBody.SavedList -> SavedIdeasContent(
                state = saved,
                signedIn = true,
                snackbarHost = snackHost,
                showSnackbarHost = false,
                showBack = false,
                title = stringResource(Res.string.explore_home_title),
                applyStatusBarsPadding = false,
                listBottomSpace = 96.dp,
                onCreateTrip = onCreateTrip,
                onRemove = savedViewModel::remove,
                onRefresh = savedViewModel::refresh,
                onRetry = savedViewModel::load,
            )
        }
    }
}

/**
 * Tab-level snackbar + FAB. The FAB offsets by the host height so Undo stays tappable.
 * [SavedIdeasContent] keeps `showSnackbarHost = false` so this bar outlives the last unsave.
 */
@Composable
internal fun ExploreTabScaffold(
    snackbarHost: SnackbarHostState,
    showFab: Boolean,
    onAskAi: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var snackbarHeightPx by remember { mutableIntStateOf(0) }
    val fabLift by animateDpAsState(
        targetValue = with(density) { snackbarHeightPx.toDp() },
        label = "exploreFabLift",
    )
    Box(Modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { snackbarHeightPx = it.height },
        ) { data ->
            UndoSnackbar(data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() })
        }
        if (showFab) {
            IconFab(
                Symbols.autoAwesome,
                label = stringResource(Res.string.explore_ai_title),
                onClick = onAskAi,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).offset(y = -fabLift),
            )
        }
    }
}
