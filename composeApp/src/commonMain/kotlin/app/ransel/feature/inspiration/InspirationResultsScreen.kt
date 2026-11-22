package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.remote.apiImageUrl
import app.ransel.feature.explore.label
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationChipDto
import app.ransel.shared.model.Interest
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.SkeletonCards
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.flow.collect
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource as composeStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_undo
import ransel.composeapp.generated.resources.ai_paused_title
import ransel.composeapp.generated.resources.explore_activity_region
import ransel.composeapp.generated.resources.explore_access_value
import ransel.composeapp.generated.resources.explore_ask_again
import ransel.composeapp.generated.resources.explore_ask_again_paid
import ransel.composeapp.generated.resources.explore_buddy_link
import ransel.composeapp.generated.resources.explore_buddy_noted
import ransel.composeapp.generated.resources.credits_error_generic
import ransel.composeapp.generated.resources.explore_best_season_value
import ransel.composeapp.generated.resources.explore_edit_hint
import ransel.composeapp.generated.resources.explore_enough
import ransel.composeapp.generated.resources.explore_interview_lost_body
import ransel.composeapp.generated.resources.explore_interview_lost_title
import ransel.composeapp.generated.resources.explore_level_value
import ransel.composeapp.generated.resources.explore_no_credits_taken
import ransel.composeapp.generated.resources.explore_other_hint
import ransel.composeapp.generated.resources.explore_other_option
import ransel.composeapp.generated.resources.explore_offline
import ransel.composeapp.generated.resources.explore_results_title
import ransel.composeapp.generated.resources.explore_retry
import ransel.composeapp.generated.resources.explore_save
import ransel.composeapp.generated.resources.explore_saved_view
import ransel.composeapp.generated.resources.explore_saved_oldest_removed
import ransel.composeapp.generated.resources.explore_saved_snackbar
import ransel.composeapp.generated.resources.explore_surprise_chip
import ransel.composeapp.generated.resources.explore_results_leave_title
import ransel.composeapp.generated.resources.explore_results_leave_body
import ransel.composeapp.generated.resources.explore_again_divider
import ransel.composeapp.generated.resources.saved_ideas_title
import ransel.composeapp.generated.resources.swipe_photo_credit_google
import ransel.composeapp.generated.resources.state_connection_title

@Composable
fun InspirationResultsScreen(
    viewModel: InspirationResultsViewModel,
    onBack: () -> Unit,
    onOpenSavedIdeas: () -> Unit,
    onOpenCredits: () -> Unit,
    /** "Bikin trip dari sini": the card and the session's interests go to "Buat trip". */
    onCreateTrip: (InspirationCardDto, Set<app.ransel.shared.model.Interest>) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    var buddyOpen by remember { mutableStateOf(false) }
    val buddyNoted = stringResource(Res.string.explore_buddy_noted)
    val buddyFailed = stringResource(Res.string.credits_error_generic)
    val snackbarHost = remember { SnackbarHostState() }
    val savedMessage = stringResource(Res.string.explore_saved_snackbar)
    val oldestMessage = stringResource(Res.string.explore_saved_oldest_removed)
    val viewAction = stringResource(Res.string.explore_saved_view)
    val undoAction = stringResource(Res.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                InspirationResultsEvent.OutOfCredits -> onOpenCredits()
                InspirationResultsEvent.SaveFailed, InspirationResultsEvent.ChipEditFailed -> snackbarHost.showSnackbar(buddyFailed)
                is InspirationResultsEvent.Saved -> {
                    val message = if (event.removedOldest) oldestMessage else savedMessage
                    val action = if (event.removedOldest) undoAction else viewAction
                    val result = snackbarHost.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
                    if (event.removedOldest && result == SnackbarResult.ActionPerformed) viewModel.undoSave(event.newIdeaId)
                    if (!event.removedOldest && result == SnackbarResult.ActionPerformed) onOpenSavedIdeas()
                }
                is InspirationResultsEvent.BuddyNoted -> snackbarHost.showSnackbar(if (event.recorded) buddyNoted else buddyFailed)
            }
        }
    }
    InspirationResultsContent(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onOpenSavedIdeas = onOpenSavedIdeas,
        onRetry = viewModel::load,
        onAskAgain = viewModel::askAgain,
        onAnswer = viewModel::answer,
        onOtherText = viewModel::setOtherText,
        onOpenOther = viewModel::openOther,
        onAnswerOther = viewModel::answerOther,
        onCloseQuestion = viewModel::closeAskAgain,
        onSave = viewModel::save,
        onBuddy = { buddyOpen = true },
        onCreateTrip = { card ->
            viewModel.makeTripTapped()
            onCreateTrip(card, viewModel.interestsForTrip())
        },
        onCardOpened = viewModel::cardOpened,
        onEditChip = viewModel::openChipEdit,
        onChipEditValue = viewModel::setChipEditValue,
        onChipEditSubmit = viewModel::editChip,
        onChipEditAnswer = viewModel::answerChipEdit,
        onChipEditAnswerOther = viewModel::answerChipEditOther,
        onChipEditOpenOther = viewModel::openOther,
        onChipEditClose = viewModel::closeChipEdit,
    )
    if (buddyOpen) {
        BuddySheet(onNotify = { buddyOpen = false; viewModel.notifyBuddy() }, onDismiss = { buddyOpen = false })
    }
}

@Composable
fun InspirationResultsContent(
    state: InspirationResultsUiState,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onOpenSavedIdeas: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAskAgain: () -> Unit = {},
    onAnswer: (String) -> Unit = {},
    onOtherText: (String) -> Unit = {},
    onOpenOther: () -> Unit = {},
    onAnswerOther: () -> Unit = {},
    onCloseQuestion: () -> Unit = {},
    onSave: (InspirationCardDto) -> Unit = {},
    onBuddy: () -> Unit = {},
    onCreateTrip: (InspirationCardDto) -> Unit = {},
    onCardOpened: (InspirationCardDto) -> Unit = {},
    onEditChip: (InspirationChipDto) -> Unit = {},
    onChipEditValue: (String) -> Unit = {},
    onChipEditSubmit: () -> Unit = {},
    onChipEditAnswer: (String) -> Unit = {},
    onChipEditAnswerOther: () -> Unit = {},
    onChipEditOpenOther: () -> Unit = {},
    onChipEditClose: () -> Unit = {},
) {
    var detailCard by remember { mutableStateOf<InspirationCardDto?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = detailCard == null && !state.askAgainOpen && !state.chipEditOpen) { confirmLeave = true }
    when {
        state.lostSession -> FullScreenState(
            modifier = Modifier.statusBarsPadding(),
            title = stringResource(Res.string.explore_interview_lost_title),
            body = stringResource(Res.string.explore_interview_lost_body),
            primaryLabel = stringResource(Res.string.action_back), onPrimary = onBack,
        )
        state.offline -> FullScreenState(
            modifier = Modifier.statusBarsPadding(),
            title = stringResource(Res.string.state_connection_title),
            body = stringResource(Res.string.explore_offline),
            primaryLabel = stringResource(Res.string.explore_retry), onPrimary = onRetry,
            secondaryLabel = stringResource(Res.string.action_close), onSecondary = onBack,
        )
        state.loading && state.result == null -> SkeletonCards(cards = 3)
        else -> Scaffold(
            modifier = Modifier.statusBarsPadding(),
            snackbarHost = {
                SnackbarHost(snackbarHost) { data ->
                    UndoSnackbar(data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() })
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ResultsTopBar(onBookmark = onOpenSavedIdeas, onBack = { confirmLeave = true })
                val result = state.result
                if (result != null) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        ProfileHeader(result.profile, onEditChip)
                        Text(stringResource(Res.string.explore_edit_hint), Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.recalculating) Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
                        if (state.inlineError) InlineResultsError(onRetry)
                        state.cards.forEachIndexed { index, item ->
                            if (state.appendDividerAt == index) AgainDivider()
                            InspirationCard(
                                item = item,
                                onOpen = {
                                    onCardOpened(item.card)
                                    detailCard = item.card
                                },
                                onSave = { onSave(item.card) },
                                saved = item.card.id in state.savedCardIds,
                                modifier = Modifier.graphicsLayer(alpha = if (state.recalculating) 0.38f else 1f),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (result.showFindBuddy) TextButton(onClick = onBuddy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.explore_buddy_link), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onAskAgain, enabled = !state.askAgainLoading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.askAgainPaidCost == null) stringResource(Res.string.explore_ask_again) else composeStringResource(Res.string.explore_ask_again_paid, state.askAgainPaidCost!!))
                        }
                    }
                } else if (state.inlineError) {
                    InlineResultsError(onRetry)
                }
            }
        }
    }
    detailCard?.let { card ->
        CardDetailSheet(card, onDismiss = { detailCard = null }, onCreateTrip = { onCreateTrip(it); detailCard = null }, onSave = { onSave(it); detailCard = null })
    }
    if (state.askAgainOpen) {
        AskAgainSheet(state, onAnswer, onOtherText, onOpenOther, onAnswerOther, onCloseQuestion)
    }
    if (state.chipEditOpen && state.chipEditKey != null) {
        ChipEditSheet(
            key = state.chipEditKey,
            value = state.chipEditValue,
            freeLeft = state.chipEditsFreeLeft,
            extraCost = state.extraCost,
            question = state.chipEditQuestion,
            otherText = state.otherText,
            otherOpen = state.otherOpen,
            loading = state.chipEditLoading,
            onValueChange = onChipEditValue,
            onOpenOther = onChipEditOpenOther,
            onAnswer = onChipEditAnswer,
            onAnswerOther = onChipEditAnswerOther,
            onSubmit = onChipEditSubmit,
            onDismiss = onChipEditClose,
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(Res.string.explore_results_leave_title),
            body = stringResource(Res.string.explore_results_leave_body),
            confirmLabel = stringResource(Res.string.action_back),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun ResultsTopBar(onBack: () -> Unit, onBookmark: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
        Text(stringResource(Res.string.explore_results_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onBookmark) { Symbol(Symbols.bookmark, contentDescription = stringResource(Res.string.saved_ideas_title)) }
    }
}

@Composable
private fun ProfileHeader(profile: List<InspirationChipDto>, onEdit: (InspirationChipDto) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        profile.forEach { chip ->
            Surface(
                modifier = Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable { onEdit(chip) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Symbols.edit, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(chip.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun InspirationCard(item: InspirationFeedCard, onOpen: () -> Unit, onSave: () -> Unit, saved: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                val photo = apiImageUrl(item.card.photoUrl)
                if (photo != null) {
                    AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(IrisTheme.accents.teal), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Symbol(Symbols.explore, size = 40.dp, tint = IrisTheme.accents.onAccent)
                            Text(item.card.region, style = MaterialTheme.typography.titleMedium, color = IrisTheme.accents.onAccent)
                        }
                    }
                }
                if (item.card.photoAttribution != null || photo != null) {
                    Box(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .4f)))))
                    Text(item.card.photoAttribution ?: "", Modifier.align(Alignment.BottomStart).padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
                IconButton(onClick = onSave, enabled = !saved, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .4f))) {
                    Symbol(Symbols.bookmark, filled = saved, tint = Color.White, contentDescription = stringResource(Res.string.explore_save))
                }
                if (item.surprise) {
                    Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Symbol(Symbols.star, size = 16.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(stringResource(Res.string.explore_surprise_chip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(composeStringResource(Res.string.explore_activity_region, item.card.activity, item.card.region), style = MaterialTheme.typography.titleLarge)
                Text(item.card.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(
                        item.card.access?.let { composeStringResource(Res.string.explore_access_value, it) },
                        item.card.bestSeason?.let { composeStringResource(Res.string.explore_best_season_value, it) },
                        item.card.level?.let { composeStringResource(Res.string.explore_level_value, it) },
                    ).forEach { value ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Text(value, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun InlineResultsError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.explore_no_credits_taken), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.explore_retry), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun AgainDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(stringResource(Res.string.explore_again_divider), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun AskAgainSheet(
    state: InspirationResultsUiState,
    onAnswer: (String) -> Unit,
    onOtherText: (String) -> Unit,
    onOpenOther: () -> Unit,
    onAnswerOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.askAgainLoading && state.askAgainQuestion == null) SkeletonCards(cards = 3, cardHeight = 64.dp)
            state.askAgainQuestion?.let { question ->
                Text(question.text, style = MaterialTheme.typography.headlineSmall)
                if (state.inlineError) Text(stringResource(Res.string.explore_no_credits_taken), color = MaterialTheme.colorScheme.error)
                else question.options.forEach { option ->
                    OutlinedButton(onClick = { onAnswer(option.id) }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium)
                            option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (question.allowOther && !state.otherOpen) TextButton(onClick = onOpenOther) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Symbol(Symbols.editNote, size = 18.dp); Text(stringResource(Res.string.explore_other_option))
                    }
                }
                if (question.allowOther && state.otherOpen) {
                    androidx.compose.material3.OutlinedTextField(state.otherText, onOtherText, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.explore_other_hint)) })
                    IconButton(onClick = onAnswerOther, enabled = state.otherText.isNotBlank()) { Symbol(Symbols.check, contentDescription = stringResource(Res.string.explore_other_option)) }
                }
            }
        }
    }
}

private val previewCards = listOf(
    InspirationFeedCard(InspirationCardDto("c1", "Melihat penyu", "Nusa Penida", why = "Sesuai minat satwa", access = "Akses mudah", bestSeason = "Apr–Okt", level = "Pemula")),
    InspirationFeedCard(InspirationCardDto("c2", "Tur kuliner", "Ubud", why = "Ragam rasa lokal", access = "Jalan kaki", bestSeason = "Sep–Nov")),
    InspirationFeedCard(InspirationCardDto("c3", "Belajar fotografi", "Canggu", why = "Cahaya pagi", level = "Pemula")),
    InspirationFeedCard(InspirationCardDto("c4", "Jalur pantai", "Sanur", why = "Pemandangan laut", access = "Akses mudah")),
    InspirationFeedCard(InspirationCardDto("s1", "Jalur hutan", "Munduk", why = "Ide tak terduga"), surprise = true),
)
@Preview @Composable private fun ResultsPreview() = IrisPreview {
    InspirationResultsContent(
        InspirationResultsUiState(
            result = app.ransel.shared.dto.InspirationResultDto(
                cards = previewCards.dropLast(1).map { it.card },
                surprise = previewCards.last().card,
                createdAt = Instant.parse("2026-11-21T00:00:00Z"),
            ),
            cards = previewCards,
        ),
    )
}
@Preview @Composable private fun ResultsAgainPreview() = IrisPreview {
    InspirationResultsContent(
        InspirationResultsUiState(
            result = app.ransel.shared.dto.InspirationResultDto(cards = previewCards.take(3).map { it.card }, createdAt = Instant.parse("2026-11-21T00:00:00Z")),
            cards = previewCards,
            appendDividerAt = 3,
        ),
    )
}
@Preview @Composable private fun ResultsLoadingPreview() = IrisPreview { InspirationResultsContent(InspirationResultsUiState(loading = true)) }
@Preview @Composable private fun ResultsRecalculatingPreview() = IrisPreview {
    InspirationResultsContent(
        InspirationResultsUiState(
            result = app.ransel.shared.dto.InspirationResultDto(cards = previewCards.dropLast(1).map { it.card }, createdAt = Instant.parse("2026-11-21T00:00:00Z")),
            cards = previewCards.dropLast(1),
            recalculating = true,
        ),
    )
}
