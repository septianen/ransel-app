package app.ransel.admin.feature.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.adjust_add
import app.ransel.admin.generated.resources.adjust_after
import app.ransel.admin.generated.resources.adjust_amount
import app.ransel.admin.generated.resources.adjust_amount_range
import app.ransel.admin.generated.resources.adjust_balance_now
import app.ransel.admin.generated.resources.adjust_direction
import app.ransel.admin.generated.resources.adjust_exceeds_balance
import app.ransel.admin.generated.resources.adjust_free_today
import app.ransel.admin.generated.resources.adjust_note
import app.ransel.admin.generated.resources.adjust_reason
import app.ransel.admin.generated.resources.adjust_reason_hint
import app.ransel.admin.generated.resources.adjust_reason_length
import app.ransel.admin.generated.resources.adjust_save_add
import app.ransel.admin.generated.resources.adjust_save_subtract
import app.ransel.admin.generated.resources.adjust_subtract
import app.ransel.admin.generated.resources.adjust_title
import app.ransel.admin.generated.resources.cancel
import app.ransel.admin.generated.resources.close
import app.ransel.admin.generated.resources.error_code
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.failureText
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.model.CreditDirection
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource

/**
 * "Atur kredit" (design `PenggunaAturKredit`): add to or subtract from one user's wallet with a required reason and a
 * live "Saldo setelahnya". The form logic is [AdjustCreditsForm]; this is only its look.
 */
@Composable
fun AdjustCreditsDialog(
    userName: String,
    userEmail: String,
    credits: CreditsDto,
    state: AdjustState,
    onEdit: ((AdjustCreditsForm) -> AdjustCreditsForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = IrisShape.xl,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            AdjustCreditsContent(userName, userEmail, credits, state, onEdit, onSave, onDismiss)
        }
    }
}

@Composable
fun AdjustCreditsContent(
    userName: String,
    userEmail: String,
    credits: CreditsDto,
    state: AdjustState,
    onEdit: ((AdjustCreditsForm) -> AdjustCreditsForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val form = state.form
    val isAdd = form.direction == CreditDirection.ADD
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.adjust_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("$userName · $userEmail", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, enabled = !state.busy) {
                Symbol(Symbols.close, size = 22.dp, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.close))
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure(stringResource(Res.string.adjust_balance_now), format.number(form.balance), Modifier.weight(1f).widthIn(min = 140.dp))
            Figure(
                stringResource(Res.string.adjust_free_today),
                "${format.number(credits.dailyFreeLeft)}/${format.number(credits.dailyFreeLimit)}",
                Modifier.weight(1f).widthIn(min = 140.dp),
            )
        }

        val directionLabel = stringResource(Res.string.adjust_direction)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = directionLabel }) {
            CreditDirection.entries.forEachIndexed { index, direction ->
                SegmentedButton(
                    selected = form.direction == direction,
                    onClick = { onEdit { it.copy(direction = direction) } },
                    shape = SegmentedButtonDefaults.itemShape(index, CreditDirection.entries.size),
                    icon = { Symbol(if (direction == CreditDirection.ADD) Symbols.add else Symbols.remove, size = 20.dp) },
                    enabled = !state.busy,
                ) {
                    Text(stringResource(if (direction == CreditDirection.ADD) Res.string.adjust_add else Res.string.adjust_subtract))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { text -> onEdit { it.copy(amountText = text.filter(Char::isDigit).take(5)) } },
                label = { Text(stringResource(Res.string.adjust_amount)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.showsExceeds,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustCreditsForm.PRESETS.forEach { preset ->
                    OutlinedButton(
                        onClick = { onEdit { it.copy(amountText = preset.toString()) } },
                        enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text((if (isAdd) "+" else "−") + preset) }
                }
            }
            when {
                state.showsExceeds -> ErrorLine(stringResource(Res.string.adjust_exceeds_balance, format.number(form.balance)))
                form.amountText.isNotEmpty() && form.problem == AdjustProblem.AMOUNT_RANGE ->
                    ErrorLine(stringResource(Res.string.adjust_amount_range))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = form.reason,
                onValueChange = { text -> onEdit { it.copy(reason = text.take(AdjustCreditsForm.REASON_LENGTH.last)) } },
                label = { Text(stringResource(Res.string.adjust_reason)) },
                placeholder = { Text(stringResource(Res.string.adjust_reason_hint)) },
                minLines = 2,
                enabled = !state.busy,
                supportingText = { Text("${form.reason.length}/${AdjustCreditsForm.REASON_LENGTH.last}") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.reason.isNotEmpty() && form.problem == AdjustProblem.REASON_LENGTH) {
                ErrorLine(stringResource(Res.string.adjust_reason_length))
            }
        }

        AfterBanner(form, state.showsExceeds)

        Text(stringResource(Res.string.adjust_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

        state.error?.let { error ->
            ErrorLine(failureText(error) + if (error.isConnection) "" else " · " + stringResource(Res.string.error_code, error.code))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, enabled = !state.busy, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.cancel))
            }
            val amount = format.number(form.amount ?: 0)
            Button(
                onClick = onSave,
                enabled = form.canSave && !state.showsExceeds && !state.busy,
                colors = if (isAdd) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Box(Modifier.size(8.dp))
                }
                Text(stringResource(if (isAdd) Res.string.adjust_save_add else Res.string.adjust_save_subtract, amount))
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, IrisShape.md).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    }
}

/** "Saldo setelahnya": green when adding, amber when subtracting, red when it would go below zero. */
@Composable
private fun AfterBanner(form: AdjustCreditsForm, exceeds: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val isAdd = form.direction == CreditDirection.ADD
    val (bg, fg) = when {
        exceeds -> scheme.errorContainer to scheme.onErrorContainer
        isAdd -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> IrisTheme.accents.yellow.copy(alpha = 0.18f) to scheme.onSurface
    }
    val amount = form.amount ?: 0
    val after = form.balanceAfter
    Row(
        Modifier.fillMaxWidth().background(bg, IrisShape.md).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(
            when {
                exceeds -> Symbols.cancel
                isAdd -> Symbols.add
                else -> Symbols.remove
            },
            size = 22.dp,
            tint = fg,
        )
        Text(
            stringResource(Res.string.adjust_after),
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (exceeds || after == null) "—" else format.number(after),
            style = MaterialTheme.typography.titleMedium,
            color = fg,
        )
        Text(
            "(" + (if (isAdd) "+" else "−") + format.number(amount) + ")",
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier.background(bg, CircleShape),
        )
    }
}
