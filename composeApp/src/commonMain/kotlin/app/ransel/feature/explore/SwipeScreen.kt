package app.ransel.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.ransel.data.explore.ExploreCard
import app.ransel.data.explore.ExploreSession
import app.ransel.data.remote.apiOrigin
import app.ransel.shared.api.ApiRoutes
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.model.BudgetLevel
import app.ransel.ui.category.itineraryLabel
import app.ransel.ui.category.symbol
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.format.formatDurationApprox
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.components.ScooterLoading
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*

/**
 * Swipe through recommendations (designs `Swipe*`). Right = save (to the trip's "Belum dijadwalkan"), left =
 * skip. No rotation; the card gets an outline and a SIMPAN / LEWATI label while dragged, and is thrown past 40 %
 * of its width. The ✕ / undo / ♥ buttons do the same (and are the way to swipe with a mouse or keyboard).
 */
@Composable
fun SwipeScreen(
    onClose: () -> Unit,
    onChangePreferences: () -> Unit,
    onOpenItinerary: (tripId: String) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SwipeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.action_undo)
    LaunchedEffect(viewModel) {
        // collectLatest: showSnackbar suspends until the snackbar is gone, so a plain collect would make the
        // next one wait. A new event cancels the previous showSnackbar (which dismisses it) and shows at once.
        viewModel.eventFlow.collectLatest { event ->
            if (event is SwipeEvent.Saved) {
                val result = snackbar.showSnackbar(
                    message = getString(Res.string.swipe_saved, event.title),
                    actionLabel = undoLabel,
                    // With an action, Material keeps the snackbar until dismissed; Short closes it after about 4 s.
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undo()
            }
        }
    }
    LaunchedEffect(state) { if (state is SwipeUiState.Empty) onClose() }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).statusBarsPadding()) {
        when (val s = state) {
            SwipeUiState.Empty -> Unit
            SwipeUiState.Loading -> LoadingContent(onClose, onChangePreferences)
            is SwipeUiState.Cards -> CardsContent(
                session = s.session,
                onClose = onClose,
                onTune = { viewModel.discard(); onChangePreferences() },
                onLike = viewModel::like,
                onSkip = viewModel::skip,
                onUndo = viewModel::undo,
            )
            is SwipeUiState.Finished -> StateWithClose(onClose) {
                FinishedContent(
                    s.session,
                    onItinerary = { onOpenItinerary(s.session.form.tripId) },
                    onAgain = { viewModel.discard(); onChangePreferences() },
                )
            }
            is SwipeUiState.NoResults -> StateWithClose(onClose) {
                FullScreenState(
                    title = stringResource(Res.string.swipe_none_title),
                    body = stringResource(
                        Res.string.swipe_none_body,
                        s.session.form.destination,
                        stringResource(s.session.form.budget.label),
                    ),
                    illustration = painterResource(Res.drawable.capybara_magnifier_map),
                    primaryLabel = stringResource(Res.string.swipe_change_preferences),
                    onPrimary = { viewModel.discard(); onChangePreferences() },
                    secondaryLabel = stringResource(Res.string.swipe_back_home),
                    onSecondary = onHome,
                )
            }
            is SwipeUiState.Error -> StateWithClose(onClose) {
                if (s.failure.isConnection) {
                    ConnectionErrorState(onRetry = viewModel::retry, errorCode = s.failure.code, onBack = onHome)
                } else {
                    FullScreenState(
                        title = stringResource(Res.string.swipe_error_title),
                        body = stringResource(Res.string.swipe_error_body),
                        illustration = painterResource(Res.drawable.capybara_umbrella_rain),
                        errorCode = s.failure.code.takeIf { it != ErrorCode.INTERNAL_ERROR } ?: ErrorCode.AI_UNAVAILABLE,
                        primaryLabel = stringResource(Res.string.state_retry),
                        onPrimary = viewModel::retry,
                        secondaryLabel = stringResource(Res.string.swipe_change_preferences),
                        onSecondary = { viewModel.discard(); onChangePreferences() },
                    )
                }
            }
            is SwipeUiState.QuotaExceeded -> StateWithClose(onClose) {
                FullScreenState(
                    title = stringResource(Res.string.swipe_quota_title),
                    body = stringResource(Res.string.swipe_quota_body, s.quota?.limit ?: 10),
                    illustration = painterResource(Res.drawable.capybara_firefly),
                    errorCode = ErrorCode.AI_QUOTA_EXCEEDED,
                    primaryLabel = stringResource(Res.string.swipe_back_home),
                    onPrimary = onHome,
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            snackbar = { data -> UndoSnackbar(data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() }) },
        )
    }
}

@Composable
private fun StateWithClose(onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(Symbols.close, stringResource(Res.string.action_close), onClose)
            Text(stringResource(Res.string.swipe_ai_title), style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun IconBox(symbol: String, description: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(symbol, contentDescription = description) }
}

@Composable
private fun SwipeHeader(session: ExploreSession?, destination: String, onClose: () -> Unit, onTune: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(Symbols.close, stringResource(Res.string.action_close), onClose)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(stringResource(Res.string.explore_for, destination), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (session != null) {
                val interests = session.form.interests.map { stringResource(it.label) }.joinToString(", ")
                Text(
                    "$interests · ${stringResource(session.form.style.label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onTune != null) IconBox(Symbols.tune, stringResource(Res.string.swipe_change_preferences), onTune)
    }
}

@Composable
private fun LoadingContent(onClose: () -> Unit, onTune: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SwipeHeader(null, "", onClose, onTune)
        // Design `MemuatRekomendasi`: the capybara rides a scooter while the LLM and Places answer (5-10 s).
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            ScooterLoading(
                messages = listOf(
                    stringResource(Res.string.explore_loading_1),
                    stringResource(Res.string.explore_loading_2),
                    stringResource(Res.string.explore_loading_3),
                    stringResource(Res.string.explore_loading_4),
                ),
                hint = stringResource(Res.string.explore_loading_hint),
            )
        }
    }
}

@Composable
private fun CardsContent(
    session: ExploreSession,
    onClose: () -> Unit,
    onTune: () -> Unit,
    onLike: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    val card = session.cards.getOrNull(session.position)
    val scope = rememberCoroutineScope()
    val dx = remember(card?.item?.placeId) { Animatable(0f) }
    var width = 1f

    fun fling(right: Boolean) {
        scope.launch {
            dx.animateTo(if (right) width * 1.3f else -width * 1.3f, tween(180))
            if (right) onLike() else onSkip()
        }
    }

    // Warm the cache with the next card's photo only (Google photos are billed per request).
    val platformContext = LocalPlatformContext.current
    val nextPhoto = session.cards.getOrNull(session.position + 1)?.let(::photoModel)
    LaunchedEffect(nextPhoto) {
        if (nextPhoto != null) SingletonImageLoader.get(platformContext).enqueue(ImageRequest.Builder(platformContext).data(nextPhoto).build())
    }

    Column(Modifier.fillMaxSize()) {
        SwipeHeader(session, session.form.destination, onClose, onTune)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.swipe_progress, minOf(session.position + 1, session.cards.size), session.cards.size),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.clip(IrisShape.xs).background(IrisTheme.accents.magenta).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Symbol(Symbols.favorite, size = 14.dp, filled = true, tint = IrisTheme.accents.onAccent)
                Text(stringResource(Res.string.swipe_saved_count, session.saved), style = MaterialTheme.typography.labelMedium, color = IrisTheme.accents.onAccent)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            width = constraints.maxWidth.toFloat()
            // Two quiet cards behind the top one (design), so the deck reads as a stack.
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp).clip(IrisShape.xl).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 12.dp).clip(IrisShape.xl).background(MaterialTheme.colorScheme.surface))
            if (card == null) {
                Box(Modifier.fillMaxSize().padding(bottom = 24.dp).clip(IrisShape.xl).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val progress = (dx.value / (width * 0.4f)).coerceIn(-1f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                        .offset { IntOffset(dx.value.roundToInt(), 0) }
                        .pointerInput(card.item.placeId) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        dx.value > width * 0.4f -> fling(true)
                                        dx.value < -width * 0.4f -> fling(false)
                                        else -> scope.launch { dx.animateTo(0f, tween(150)) }
                                    }
                                },
                                onDragCancel = { scope.launch { dx.animateTo(0f, tween(150)) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { dx.snapTo(dx.value + amount) }
                            }
                        },
                ) {
                    RecommendationCard(card, progress)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(Symbols.close, stringResource(Res.string.swipe_skip), MaterialTheme.colorScheme.error, 64, enabled = card != null) { fling(false) }
            RoundAction(Symbols.undo, stringResource(Res.string.action_undo), MaterialTheme.colorScheme.onSurfaceVariant, 48, enabled = session.swipes.isNotEmpty(), onClick = onUndo)
            RoundAction(Symbols.favorite, stringResource(Res.string.swipe_save), IrisTheme.accents.magenta, 64, filled = true, enabled = card != null) { fling(true) }
        }
    }
}

@Composable
private fun RoundAction(
    symbol: String,
    description: String,
    tint: Color,
    size: Int,
    enabled: Boolean,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(size.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Symbol(symbol, contentDescription = description, size = if (size >= 64) 32.dp else 24.dp, filled = filled, tint = tint)
        }
    }
}

/** Absolute URL of the card photo (the API gives a signed path relative to `ApiRoutes.BASE`); null without a photo. */
private fun photoModel(card: ExploreCard): String? {
    val path = card.item.photoUrl?.takeIf { it.isNotBlank() } ?: return null
    if (path.startsWith("http")) return path
    return apiOrigin().trimEnd('/') + ApiRoutes.BASE + "/" + path.trimStart('/')
}

/** The card itself. [drag] is -1..1 (left..right); the outline and label fade in with it, nothing rotates. */
@Composable
private fun RecommendationCard(card: ExploreCard, drag: Float) {
    val item = card.item
    val scheme = MaterialTheme.colorScheme
    val outline = when {
        drag > 0.05f -> IrisTheme.accents.magenta
        drag < -0.05f -> scheme.error
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxSize()
            .shadow(12.dp, IrisShape.xl)
            .clip(IrisShape.xl)
            .background(scheme.surface)
            .border(if (abs(drag) > 0.05f) 3.dp else 0.dp, outline.copy(alpha = abs(drag).coerceIn(0.3f, 1f)), IrisShape.xl),
    ) {
        // The category colour and icon stay underneath: placeholder while loading and fallback without a photo or on error.
        Box(Modifier.weight(1f).fillMaxWidth().background(card.category.tileColor())) {
            Symbol(card.category.symbol, size = 96.dp, filled = true, tint = IrisTheme.accents.onAccent.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.Center))
            val photo = photoModel(card)
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                // The credit is shown whenever a photo is requested; a failed load leaves it over the colour, which is harmless.
                Text(
                    item.photoAttribution?.let { stringResource(Res.string.swipe_photo_credit, it) } ?: stringResource(Res.string.swipe_photo_credit_google),
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(16.dp).clip(CircleShape).background(scheme.surface).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Symbol(card.category.symbol, size = 16.dp, filled = true, tint = card.category.tileColor())
                Text(stringResource(card.category.itineraryLabel), style = MaterialTheme.typography.labelMedium)
            }
            if (abs(drag) > 0.05f) {
                val right = drag > 0
                Row(
                    Modifier
                        .align(if (right) Alignment.TopStart else Alignment.TopEnd)
                        .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                        .alpha(abs(drag))
                        .clip(IrisShape.md)
                        .background(scheme.surface)
                        .border(2.dp, outline, IrisShape.md)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Symbol(if (right) Symbols.favorite else Symbols.close, size = 20.dp, filled = true, tint = outline)
                    Text(
                        stringResource(if (right) Res.string.swipe_label_save else Res.string.swipe_label_skip),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = outline,
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                item.rating?.let { rating ->
                    val tenths = (rating * 10).roundToInt()
                    val separator = stringResource(Res.string.swipe_decimal_separator)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Symbol(Symbols.star, size = 18.dp, filled = true, tint = IrisTheme.accents.yellow)
                        Text("${tenths / 10}$separator${tenths % 10}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item.priceLevel?.let { level ->
                    val budget = when { level <= 1 -> BudgetLevel.HEMAT; level == 2 -> BudgetLevel.SEDANG; else -> BudgetLevel.BEBAS }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Symbol(Symbols.payments, size = 18.dp, tint = scheme.onSurfaceVariant)
                        Text(stringResource(budget.label), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Symbol(Symbols.schedule, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(formatDurationApprox(item.suggestedDurationMin), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.tertiaryContainer).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.autoAwesome, size = 18.dp, filled = true, tint = scheme.onTertiaryContainer)
                Text(item.reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun FinishedContent(session: ExploreSession, onItinerary: () -> Unit, onAgain: () -> Unit) {
    FullScreenState(
        title = stringResource(Res.string.swipe_done_title),
        body = stringResource(Res.string.swipe_done_body, session.saved, session.form.tripTitle),
        illustration = painterResource(Res.drawable.capybara_pinning_map),
        illustrationSize = 160.dp,
        primaryLabel = stringResource(Res.string.swipe_done_itinerary),
        onPrimary = onItinerary,
        secondaryLabel = stringResource(Res.string.swipe_done_again),
        onSecondary = onAgain,
    )
}
