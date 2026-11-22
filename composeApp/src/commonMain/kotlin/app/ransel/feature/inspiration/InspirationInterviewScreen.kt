package app.ransel.feature.inspiration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.SkeletonCards
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource as composeStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.explore_ai_title
import ransel.composeapp.generated.resources.explore_ask_charge_note
import ransel.composeapp.generated.resources.explore_discard_body
import ransel.composeapp.generated.resources.explore_discard_title
import ransel.composeapp.generated.resources.explore_enough
import ransel.composeapp.generated.resources.explore_enough_note
import ransel.composeapp.generated.resources.explore_interview_lost_body
import ransel.composeapp.generated.resources.explore_interview_lost_title
import ransel.composeapp.generated.resources.explore_other_hint
import ransel.composeapp.generated.resources.explore_other_option
import ransel.composeapp.generated.resources.explore_question_failed
import ransel.composeapp.generated.resources.explore_question_generating
import ransel.composeapp.generated.resources.explore_retry
import ransel.composeapp.generated.resources.explore_step_label
import ransel.composeapp.generated.resources.explore_step_left
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationOptionDto
import app.ransel.shared.model.InspirationSlot

@Composable
fun InspirationInterviewScreen(
    viewModel: InspirationInterviewViewModel,
    onResults: () -> Unit,
    onOutOfCredits: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                InspirationInterviewEvent.Results -> onResults()
                InspirationInterviewEvent.OutOfCredits -> onOutOfCredits()
            }
        }
    }
    InspirationInterviewContent(
        state = state,
        onAnswer = viewModel::answer,
        onOtherText = viewModel::setOtherText,
        onOpenOther = viewModel::openOther,
        onAnswerOther = viewModel::answerOther,
        onFinish = viewModel::enough,
        onRetry = viewModel::retry,
        onBack = onBack,
    )
}

@Composable
fun InspirationInterviewContent(
    state: InspirationInterviewUiState,
    onAnswer: (String) -> Unit = {},
    onOtherText: (String) -> Unit = {},
    onOpenOther: () -> Unit = {},
    onAnswerOther: () -> Unit = {},
    onFinish: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    if (state.lostSession) {
        FullScreenState(
            title = stringResource(Res.string.explore_interview_lost_title),
            body = stringResource(Res.string.explore_interview_lost_body),
            primaryLabel = stringResource(Res.string.action_close),
            onPrimary = onBack,
        )
        return
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(Res.string.explore_discard_title),
            body = stringResource(Res.string.explore_discard_body),
            confirmLabel = stringResource(Res.string.action_close),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Text(stringResource(Res.string.explore_ai_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (state.hasAnswers) confirmDiscard = true else onBack() }) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close))
            }
        }
        val question = state.question
        val progress = ((question?.stepsDone ?: 0) + (question?.skippedSlots?.size ?: 0)).coerceIn(0, 5)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(5) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp).background(
                        if (index < progress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(4.dp),
                    ),
                )
            }
        }
        Text(stringResource(Res.string.explore_step_label, (progress + 2).coerceAtMost(6), 6), style = MaterialTheme.typography.bodySmall)
        if (question?.stepsDone == 0) Text(stringResource(Res.string.explore_ask_charge_note), style = MaterialTheme.typography.bodySmall)
        if (state.loading && question == null) {
            Text(stringResource(Res.string.explore_question_generating), style = MaterialTheme.typography.bodySmall)
            SkeletonCards(cards = 3, cardHeight = 64.dp, padding = 0.dp)
        } else if (question != null) {
            Text(question.text, style = MaterialTheme.typography.headlineSmall)
            when {
                state.inlineError -> ErrorInline(onRetry)
                state.loading -> SkeletonCards(cards = 3, cardHeight = 64.dp, padding = 0.dp)
                else -> {
                    question.options.forEach { option -> OptionRow(option, onAnswer) }
                    if (question.allowOther) {
                        if (!state.otherOpen) {
                            TextButton(onClick = onOpenOther) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Symbol(Symbols.editNote, size = 18.dp)
                                    Text(stringResource(Res.string.explore_other_option), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = state.otherText,
                                onValueChange = onOtherText,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(Res.string.explore_other_hint)) },
                                supportingText = { Text("${state.otherText.length}/80") },
                            )
                            IconButton(onClick = onAnswerOther, enabled = state.otherText.isNotBlank()) {
                                Symbol(Symbols.check, contentDescription = stringResource(Res.string.explore_other_option))
                            }
                        }
                    }
                    if (question.canFinishEarly) {
                        TextButton(onClick = onFinish) { Text(stringResource(Res.string.explore_enough)) }
                        Text(stringResource(Res.string.explore_enough_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (state.inlineError) {
            ErrorInline(onRetry)
        }
    }
}

@Composable
private fun ErrorInline(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.explore_question_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.explore_retry), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun OptionRow(option: InspirationOptionDto, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(option.id) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(option.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private val previewQuestion = InspirationQuestionDto(
    InspirationSlot.DISTANCE,
    "Seberapa jauh kamu ingin pergi?",
    listOf(InspirationOptionDto("same_city", "Dalam kota"), InspirationOptionDto("domestic", "Dalam negeri"), InspirationOptionDto("abroad", "Luar negeri")),
    canFinishEarly = true,
    stepsDone = 2,
)
@Preview @Composable private fun InterviewQuestionPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion, loading = false)) }
@Preview @Composable private fun InterviewLoadingPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(loading = true)) }
@Preview @Composable private fun InterviewErrorPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion, inlineError = true)) }
@Preview @Composable private fun InterviewEnoughPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion.copy(canFinishEarly = true), loading = false)) }
