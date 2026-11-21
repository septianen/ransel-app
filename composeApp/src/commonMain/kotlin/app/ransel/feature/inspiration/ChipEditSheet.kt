package app.ransel.feature.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.model.InspirationChipKey
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_chip_edit_apply
import ransel.composeapp.generated.resources.explore_chip_edit_free
import ransel.composeapp.generated.resources.explore_chip_edit_paid
import ransel.composeapp.generated.resources.explore_chip_edit_title
import ransel.composeapp.generated.resources.explore_distance_abroad
import ransel.composeapp.generated.resources.explore_distance_any
import ransel.composeapp.generated.resources.explore_distance_domestic
import ransel.composeapp.generated.resources.explore_distance_same_city
import ransel.composeapp.generated.resources.explore_other_hint
import ransel.composeapp.generated.resources.explore_other_option
import ransel.composeapp.generated.resources.explore_retry

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ChipEditSheet(
    key: InspirationChipKey,
    value: String,
    freeLeft: Int,
    extraCost: Int,
    question: InspirationQuestionDto? = null,
    otherText: String = "",
    otherOpen: Boolean = false,
    loading: Boolean = false,
    onValueChange: (String) -> Unit,
    onOpenOther: () -> Unit,
    onAnswer: (String) -> Unit,
    onAnswerOther: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChipEditContent(
            key, value, freeLeft, extraCost, question, otherText, otherOpen, loading,
            onValueChange, onOpenOther, onAnswer, onAnswerOther, onSubmit,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipEditContent(
    key: InspirationChipKey,
    value: String,
    freeLeft: Int,
    extraCost: Int,
    question: InspirationQuestionDto? = null,
    otherText: String = "",
    otherOpen: Boolean = false,
    loading: Boolean = false,
    onValueChange: (String) -> Unit = {},
    onOpenOther: () -> Unit = {},
    onAnswer: (String) -> Unit = {},
    onAnswerOther: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(question?.text ?: stringResource(Res.string.explore_chip_edit_title), style = MaterialTheme.typography.headlineSmall)
        if (question != null) {
            question.options.forEach { option ->
                Button(onClick = { onAnswer(option.id) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(option.title, style = MaterialTheme.typography.titleMedium)
                        option.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (question.allowOther && !otherOpen) TextButton(onClick = onOpenOther) {
                Text(stringResource(Res.string.explore_other_option))
            }
            if (question.allowOther && otherOpen) {
                OutlinedTextField(
                    value = otherText,
                    onValueChange = { onValueChange(it.take(80)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.explore_other_hint)) },
                    supportingText = { Text("${otherText.length}/80") },
                )
                Button(onClick = onAnswerOther, enabled = !loading && otherText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.explore_chip_edit_apply))
                }
            }
        } else {
            if (key == InspirationChipKey.DISTANCE) {
                val options = listOf(
                    "same_city" to Res.string.explore_distance_same_city,
                    "domestic" to Res.string.explore_distance_domestic,
                    "abroad" to Res.string.explore_distance_abroad,
                    "any" to Res.string.explore_distance_any,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (id, label) ->
                        FilterChip(
                            selected = value == id,
                            onClick = { onValueChange(id) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(it.take(80)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.explore_chip_edit_title)) },
                    supportingText = { Text("${value.length}/80") },
                )
            }
            Text(
                if (freeLeft > 0) stringResource(Res.string.explore_chip_edit_free, freeLeft)
                else stringResource(Res.string.explore_chip_edit_paid, extraCost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && value.isNotBlank() && (key != InspirationChipKey.DISTANCE || value in setOf("same_city", "domestic", "abroad", "any")),
            ) {
                Text(stringResource(Res.string.explore_chip_edit_apply))
            }
        }
    }
}

@Preview @Composable private fun FreeChipEditPreview() = IrisPreview {
    ChipEditContent(InspirationChipKey.LEVEL, "Pemula", 2, 3)
}
@Preview @Composable private fun PaidChipEditPreview() = IrisPreview {
    ChipEditContent(InspirationChipKey.DISTANCE, "same_city", 0, 3)
}
