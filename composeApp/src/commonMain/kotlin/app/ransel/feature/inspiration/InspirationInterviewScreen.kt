package app.ransel.feature.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.SkeletonCards
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
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
import ransel.composeapp.generated.resources.explore_ai_title
import ransel.composeapp.generated.resources.explore_ask_charge_note
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
    InspirationInterviewContent(state, viewModel::answer, viewModel::setOtherText, viewModel::answerOther, viewModel::finish, viewModel::retry, onBack)
}

@Composable
fun InspirationInterviewContent(
    state: InspirationInterviewUiState,
    onAnswer: (String) -> Unit = {},
    onOtherText: (String) -> Unit = {},
    onAnswerOther: () -> Unit = {},
    onFinish: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    if (state.lostSession) {
        FullScreenState(stringResource(Res.string.explore_interview_lost_title), stringResource(Res.string.explore_interview_lost_body), primaryLabel = stringResource(Res.string.explore_retry), onPrimary = onBack)
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("‹") }
            Text(stringResource(Res.string.explore_ai_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("×") }
        }
        val question = state.question
        val progress = ((question?.stepsDone ?: 0) + (question?.skippedSlots?.size ?: 0)).coerceIn(0, 5)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(5) { index ->
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(4.dp).background(if (index < progress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)))
            }
        }
        Text(stringResource(Res.string.explore_step_label, (progress + 2).coerceAtMost(6), 6), style = MaterialTheme.typography.bodySmall)
        if (progress == 0) Text(stringResource(Res.string.explore_ask_charge_note), style = MaterialTheme.typography.bodySmall)
        when {
            state.loading && question == null -> {
                Text(stringResource(Res.string.explore_question_generating), style = MaterialTheme.typography.bodySmall)
                SkeletonCards(cards = 3, cardHeight = 64.dp, padding = 0.dp)
            }
            state.inlineError -> {
                Text(stringResource(Res.string.explore_question_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.explore_retry)) }
            }
            question != null -> {
                Text(question.text, style = MaterialTheme.typography.headlineSmall)
                if (state.loading) SkeletonCards(cards = 3, cardHeight = 64.dp, padding = 0.dp)
                else {
                    question.options.forEach { option -> OptionRow(option, onAnswer) }
                    if (question.allowOther) {
                        Text(stringResource(Res.string.explore_other_option), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(state.otherText, onOtherText, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.explore_other_hint)) }, supportingText = { Text("${state.otherText.length}/80") })
                        Button(onClick = onAnswerOther, enabled = state.otherText.isNotBlank()) { Text("✓") }
                    }
                    if (question.canFinishEarly) {
                        TextButton(onClick = onFinish) { Text(stringResource(Res.string.explore_enough)) }
                        Text(stringResource(Res.string.explore_enough_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable private fun OptionRow(option: InspirationOptionDto, onClick: (String) -> Unit) {
    OutlinedButton(onClick = { onClick(option.id) }, modifier = Modifier.fillMaxWidth()) {
        Column { Text(option.title, style = MaterialTheme.typography.titleMedium); option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
    }
}

private val previewQuestion = InspirationQuestionDto(InspirationSlot.DISTANCE, "Seberapa jauh kamu ingin pergi?", listOf(InspirationOptionDto("same_city", "Dalam kota"), InspirationOptionDto("domestic", "Dalam negeri"), InspirationOptionDto("abroad", "Luar negeri")), canFinishEarly = true, stepsDone = 2)
@Preview @Composable private fun InterviewQuestionPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion, loading = false)) }
@Preview @Composable private fun InterviewLoadingPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(loading = true)) }
@Preview @Composable private fun InterviewErrorPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion, inlineError = true)) }
@Preview @Composable private fun InterviewEnoughPreview() = IrisPreview { InspirationInterviewContent(InspirationInterviewUiState(question = previewQuestion.copy(canFinishEarly = true), loading = false)) }
