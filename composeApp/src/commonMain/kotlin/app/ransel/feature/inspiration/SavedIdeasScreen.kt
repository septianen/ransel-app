package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.remote.apiImageUrl
import app.ransel.shared.dto.SavedIdeaDto
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.SkeletonList
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.RefreshOnReturn
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.action_undo
import ransel.composeapp.generated.resources.credits_error_generic
import ransel.composeapp.generated.resources.explore_guest_body
import ransel.composeapp.generated.resources.explore_guest_title
import ransel.composeapp.generated.resources.explore_guest_sign_in
import ransel.composeapp.generated.resources.explore_guest_sign_up
import ransel.composeapp.generated.resources.explore_ai_title
import ransel.composeapp.generated.resources.explore_retry
import ransel.composeapp.generated.resources.explore_saved_view
import ransel.composeapp.generated.resources.saved_ideas_count
import ransel.composeapp.generated.resources.saved_ideas_empty_body
import ransel.composeapp.generated.resources.saved_ideas_empty_title
import ransel.composeapp.generated.resources.saved_ideas_meta_duration
import ransel.composeapp.generated.resources.saved_ideas_removed
import ransel.composeapp.generated.resources.saved_ideas_refresh
import ransel.composeapp.generated.resources.saved_ideas_refresh_wait
import ransel.composeapp.generated.resources.saved_ideas_stale
import ransel.composeapp.generated.resources.saved_ideas_title
import ransel.composeapp.generated.resources.ic_binocular

@Composable
fun SavedIdeasScreen(
    viewModel: SavedIdeasViewModel,
    signedIn: Boolean,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onAskAi: () -> Unit,
    /** "Bikin trip dari sini" in an idea's sheet: Buat trip prefilled from the idea. */
    onCreateTrip: (SavedIdeaDto) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackHost = remember { SnackbarHostState() }
    val removedMessage = stringResource(Res.string.saved_ideas_removed)
    val undoLabel = stringResource(Res.string.action_undo)
    val genericError = stringResource(Res.string.credits_error_generic)
    LaunchedEffect(signedIn) { if (signedIn) viewModel.load() }
    if (signedIn) RefreshOnReturn(viewModel::load)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SavedIdeasEvent.Removed -> {
                    if (snackHost.showSnackbar(removedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemove(event.id)
                    }
                }
                SavedIdeasEvent.Error -> snackHost.showSnackbar(genericError)
            }
        }
    }
    SavedIdeasContent(
        state = state,
        signedIn = signedIn,
        snackbarHost = snackHost,
        onBack = onBack,
        onSignIn = onSignIn,
        onSignUp = onSignUp,
        onAskAi = onAskAi,
        onCreateTrip = onCreateTrip,
        onRemove = viewModel::remove,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::load,
    )
}

@Composable
fun SavedIdeasContent(
    state: SavedIdeasUiState,
    signedIn: Boolean,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onAskAi: () -> Unit = {},
    onCreateTrip: (SavedIdeaDto) -> Unit = {},
    onRemove: (SavedIdeaDto) -> Unit = {},
    onRefresh: (SavedIdeaDto) -> Unit = {},
    onRetry: () -> Unit = {},
    showSnackbarHost: Boolean = true,
    showBack: Boolean = true,
    title: String = stringResource(Res.string.saved_ideas_title),
    applyStatusBarsPadding: Boolean = true,
    listBottomSpace: Dp = 0.dp,
) {
    var detail by remember { mutableStateOf<SavedIdeaDto?>(null) }
    val statusBars = if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier
    when {
        !signedIn -> FullScreenState(
            modifier = statusBars,
            title = stringResource(Res.string.explore_guest_title),
            body = stringResource(Res.string.explore_guest_body),
            primaryLabel = stringResource(Res.string.explore_guest_sign_in), onPrimary = onSignIn,
            secondaryLabel = stringResource(Res.string.explore_guest_sign_up), onSecondary = onSignUp,
        )
        state.loading && !state.loaded -> SkeletonList(rows = 5)
        state.failure != null && state.ideas.isEmpty() -> FullScreenState(
            modifier = statusBars,
            title = stringResource(Res.string.credits_error_generic),
            body = stringResource(Res.string.credits_error_generic),
            primaryLabel = stringResource(Res.string.explore_retry), onPrimary = onRetry,
            secondaryLabel = stringResource(Res.string.action_back), onSecondary = onBack,
        )
        state.ideas.isEmpty() -> FullScreenState(
            modifier = statusBars,
            title = stringResource(Res.string.saved_ideas_empty_title),
            body = stringResource(Res.string.saved_ideas_empty_body),
            illustration = painterResource(Res.drawable.ic_binocular),
            illustrationSize = 200.dp,
            primaryLabel = stringResource(Res.string.explore_ai_title), onPrimary = onAskAi,
        )
        else -> Scaffold(
            modifier = statusBars,
            snackbarHost = {
                if (showSnackbarHost) {
                    SnackbarHost(snackbarHost) { UndoSnackbar(it.visuals.message, actionLabel = it.visuals.actionLabel, onAction = { it.performAction() }) }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (showBack) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBack) {
                        IconButton(onClick = onBack) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
                    }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(Res.string.saved_ideas_count, state.ideas.size),
                        Modifier.padding(end = if (showBack) 12.dp else 0.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(
                    Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.ideas.forEach { idea ->
                        SavedIdeaRow(
                            idea = idea,
                            stale = idea.id in state.staleIds,
                            waitRefresh = idea.id in state.waitingRefreshIds,
                            refreshing = idea.id in state.refreshingIds,
                            onOpen = { detail = idea },
                            onRemove = { onRemove(idea) },
                            onRefresh = { onRefresh(idea) },
                        )
                    }
                    if (listBottomSpace > 0.dp) Spacer(Modifier.height(listBottomSpace))
                }
            }
        }
    }
    detail?.let { idea ->
        CardDetailSheet(idea.card, onDismiss = { detail = null }, onCreateTrip = { detail = null; onCreateTrip(idea) }, onSave = { }, showSaveButton = false)
    }
}

@Composable
private fun SavedIdeaRow(
    idea: SavedIdeaDto,
    stale: Boolean,
    waitRefresh: Boolean,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
) {
    val dates = rememberDateFormatter()
    val meta = idea.profile.firstOrNull()?.value ?: idea.card.level.orEmpty()
    val duration = idea.card.durationDays
    val metaLine = if (duration != null) stringResource(Res.string.saved_ideas_meta_duration, meta, duration) else meta
    val modified = dates.monthYear(idea.refreshedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
    Column(Modifier.fillMaxWidth()) {
        if (refreshing) Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
        Row(
            Modifier.fillMaxWidth().graphicsLayer(alpha = if (refreshing) 0.38f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.weight(1f).clickable(onClick = onOpen), color = MaterialTheme.colorScheme.surface, shape = IrisShape.md) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SavedIdeaThumbnail(photoUrl = idea.card.photoUrl, region = idea.card.region)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(idea.card.activity, style = MaterialTheme.typography.titleMedium)
                        Text(idea.card.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (metaLine.isNotBlank()) Text(metaLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stale) Text(stringResource(Res.string.saved_ideas_stale, modified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (waitRefresh) Text(stringResource(Res.string.saved_ideas_refresh_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stale || waitRefresh) TextButton(onClick = onRefresh, enabled = !refreshing && !waitRefresh) { Text(stringResource(Res.string.saved_ideas_refresh)) }
                    }
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Symbol(Symbols.bookmark, filled = true, contentDescription = stringResource(Res.string.saved_ideas_removed))
            }
        }
    }
}

/** Teal Explore tile (same slot while the photo loads, is missing, or fails). */
@Composable
private fun SavedIdeaThumbnail(photoUrl: String?, region: String) {
    Box(Modifier.size(width = 72.dp, height = 72.dp).clip(IrisShape.md)) {
        Box(Modifier.fillMaxSize().background(IrisTheme.accents.teal), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Symbol(Symbols.explore, size = 24.dp, tint = IrisTheme.accents.onAccent)
                Text(region, style = MaterialTheme.typography.labelSmall, color = IrisTheme.accents.onAccent, maxLines = 1)
            }
        }
        apiImageUrl(photoUrl)?.let { photo ->
            AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private val savedPreview = SavedIdeaDto(
    id = "saved-1",
    card = InspirationCardDto("c1", "Freediving pemula", "Nusa Penida", why = "Sesuai", level = "Pemula", durationDays = 3),
    profile = emptyList(),
    savedAt = kotlin.time.Instant.parse("2026-09-01T00:00:00Z"),
    refreshedAt = kotlin.time.Instant.parse("2026-09-01T00:00:00Z"),
)
@Preview @Composable private fun SavedIdeasStalePreview() = IrisPreview { SavedIdeasContent(SavedIdeasUiState(ideas = listOf(savedPreview), loaded = true, staleIds = setOf("saved-1")), true) }
@Preview @Composable private fun SavedIdeasDisabledRefreshPreview() = IrisPreview { SavedIdeasContent(SavedIdeasUiState(ideas = listOf(savedPreview), loaded = true, staleIds = setOf("saved-1"), waitingRefreshIds = setOf("saved-1")), true) }
@Preview @Composable private fun SavedIdeasEmptyPreview() = IrisPreview { SavedIdeasContent(SavedIdeasUiState(loaded = true), true) }
@Preview @Composable private fun SavedIdeasAsExploreTabPreview() = IrisPreview {
    SavedIdeasContent(
        state = SavedIdeasUiState(ideas = listOf(savedPreview), loaded = true),
        signedIn = true,
        showBack = false,
        title = "Jelajah",
        applyStatusBarsPadding = false,
        listBottomSpace = 96.dp,
        showSnackbarHost = false,
    )
}
