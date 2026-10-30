package app.ransel.admin.feature.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.settings_alert
import app.ransel.admin.generated.resources.settings_budget
import app.ransel.admin.generated.resources.settings_cancel
import app.ransel.admin.generated.resources.settings_confirm_body
import app.ransel.admin.generated.resources.settings_confirm_reset
import app.ransel.admin.generated.resources.settings_confirm_title
import app.ransel.admin.generated.resources.settings_cost_next_batch
import app.ransel.admin.generated.resources.settings_cost_place_detail
import app.ransel.admin.generated.resources.settings_cost_receipt
import app.ransel.admin.generated.resources.settings_cost_search
import app.ransel.admin.generated.resources.settings_credits_note
import app.ransel.admin.generated.resources.settings_credits_subtitle
import app.ransel.admin.generated.resources.settings_credits_title
import app.ransel.admin.generated.resources.settings_daily_free
import app.ransel.admin.generated.resources.settings_last_changed
import app.ransel.admin.generated.resources.settings_llm_model
import app.ransel.admin.generated.resources.settings_mode
import app.ransel.admin.generated.resources.settings_mode_cache_only
import app.ransel.admin.generated.resources.settings_mode_keep_going
import app.ransel.admin.generated.resources.settings_problem_list
import app.ransel.admin.generated.resources.settings_problem_number
import app.ransel.admin.generated.resources.settings_problem_range
import app.ransel.admin.generated.resources.settings_reset
import app.ransel.admin.generated.resources.settings_save
import app.ransel.admin.generated.resources.settings_saved
import app.ransel.admin.generated.resources.settings_signup_grant
import app.ransel.admin.generated.resources.settings_source_env
import app.ransel.admin.generated.resources.settings_source_override
import app.ransel.admin.generated.resources.settings_subtitle
import app.ransel.admin.generated.resources.settings_suggestions
import app.ransel.admin.generated.resources.settings_title
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.TileGrid
import app.ransel.admin.ui.failureText
import app.ransel.shared.domain.AI_BUDGET_ALERT_PERCENTS
import app.ransel.shared.domain.MAX_AI_MONTHLY_BUDGET_USD
import app.ransel.shared.domain.SETTING_RANGES
import app.ransel.shared.model.AiBudgetMode
import app.ransel.shared.model.SettingKey
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val GENERAL = listOf(
    SettingKey.SUGGESTIONS_PER_HOUR, SettingKey.LLM_MODEL, SettingKey.AI_MONTHLY_BUDGET_USD,
    SettingKey.AI_BUDGET_ALERT_PERCENT, SettingKey.AI_BUDGET_MODE,
)
private val CREDITS = listOf(
    SettingKey.SIGNUP_GRANT, SettingKey.DAILY_FREE, SettingKey.COST_SEARCH, SettingKey.COST_NEXT_BATCH,
    SettingKey.COST_PLACE_DETAIL, SettingKey.COST_RECEIPT,
)

private fun SettingKey.label(): StringResource = when (this) {
    SettingKey.SIGNUP_GRANT -> Res.string.settings_signup_grant
    SettingKey.DAILY_FREE -> Res.string.settings_daily_free
    SettingKey.COST_SEARCH -> Res.string.settings_cost_search
    SettingKey.COST_NEXT_BATCH -> Res.string.settings_cost_next_batch
    SettingKey.COST_PLACE_DETAIL -> Res.string.settings_cost_place_detail
    SettingKey.COST_RECEIPT -> Res.string.settings_cost_receipt
    SettingKey.AI_MONTHLY_BUDGET_USD -> Res.string.settings_budget
    SettingKey.AI_BUDGET_ALERT_PERCENT -> Res.string.settings_alert
    SettingKey.AI_BUDGET_MODE -> Res.string.settings_mode
    SettingKey.SUGGESTIONS_PER_HOUR -> Res.string.settings_suggestions
    SettingKey.LLM_MODEL -> Res.string.settings_llm_model
}

/** A value as people read it: the budget mode in words, the alert share with "%"; the rest as stored. */
@Composable
private fun shown(key: SettingKey, value: String): String = when (key) {
    SettingKey.AI_BUDGET_MODE -> when (value) {
        AiBudgetMode.CACHE_ONLY.name -> stringResource(Res.string.settings_mode_cache_only)
        else -> stringResource(Res.string.settings_mode_keep_going)
    }
    SettingKey.AI_BUDGET_ALERT_PERCENT -> "$value%"
    else -> value
}

/**
 * Design `Sistem`, "Pengaturan" (stage C): values that apply without a deploy, each with its source; "Kembalikan
 * bawaan" goes back to the env value. "Simpan" asks first, as every admin action does.
 */
@Composable
fun SettingsCard(presenter: SettingsPresenter) {
    val state by presenter.state.collectAsState()
    val form = state.form
    AdminCard {
        when {
            form == null && state.error != null -> ErrorPanel(state.error!!, presenter::reload)
            form == null -> LoadingPanel()
            else -> SettingsBody(form, state, presenter)
        }
    }
    if (form != null && state.confirming) {
        val lines = form.changes.map { (key, value) ->
            if (value == null) stringResource(Res.string.settings_confirm_reset, stringResource(key.label()), shown(key, form.setting(key).envValue))
            else "${stringResource(key.label())}: ${shown(key, form.setting(key).value)} → ${shown(key, value)}"
        }
        ConfirmDialog(
            title = stringResource(Res.string.settings_confirm_title),
            body = stringResource(Res.string.settings_confirm_body, lines.joinToString("\n")),
            confirmLabel = stringResource(Res.string.settings_save),
            destructive = false,
            busy = state.saving,
            error = state.saveError?.let { failureText(it) },
            onConfirm = presenter::save,
            onDismiss = presenter::dismiss,
        )
    }
}

@Composable
private fun SettingsBody(form: SettingsForm, state: SettingsUiState, presenter: SettingsPresenter) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val last = form.saved.settings.filter { it.updatedAt != null }.maxByOrNull { it.updatedAt!! }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                last?.let { stringResource(Res.string.settings_last_changed, format.dateTime(it.updatedAt!!), it.updatedByEmail ?: "?") }
                    ?: stringResource(Res.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        TileGrid(minWidth = 220.dp, gap = 16.dp, tiles = GENERAL.map { key -> { m -> SettingField(form, key, presenter, m) } })

        HorizontalDivider(color = scheme.outlineVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.settings_credits_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.settings_credits_subtitle), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        TileGrid(minWidth = 180.dp, gap = 16.dp, tiles = CREDITS.map { key -> { m -> SettingField(form, key, presenter, m) } })
        Text(stringResource(Res.string.settings_credits_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

        HorizontalDivider(color = scheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                if (state.saved) Text(stringResource(Res.string.settings_saved), style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
            }
            TextButton(onClick = presenter::cancel, enabled = form.changes.isNotEmpty(), modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.settings_cancel))
            }
            Button(onClick = presenter::askToSave, enabled = form.canSave, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.settings_save))
            }
        }
    }
}

@Composable
private fun SettingField(form: SettingsForm, key: SettingKey, presenter: SettingsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val setting = form.setting(key)
    val problem = form.problems[key]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = stringResource(key.label())
        when (key) {
            SettingKey.LLM_MODEL -> SettingSelect(label, form.text(key), form.saved.llmModels.map { it to it }) { presenter.edit(key, it) }
            SettingKey.AI_BUDGET_ALERT_PERCENT ->
                SettingSelect(label, form.text(key), AI_BUDGET_ALERT_PERCENTS.map { "$it" to "$it%" }) { presenter.edit(key, it) }
            SettingKey.AI_BUDGET_MODE -> SettingSelect(
                label, form.text(key),
                listOf(
                    AiBudgetMode.CACHE_ONLY.name to stringResource(Res.string.settings_mode_cache_only),
                    AiBudgetMode.KEEP_GOING.name to stringResource(Res.string.settings_mode_keep_going),
                ),
            ) { presenter.edit(key, it) }
            else -> OutlinedTextField(
                value = form.text(key),
                onValueChange = { text ->
                    val allowed = if (key == SettingKey.AI_MONTHLY_BUDGET_USD) text.filter { it.isDigit() || it == '.' || it == ',' } else text.filter(Char::isDigit)
                    presenter.edit(key, allowed.take(12))
                },
                label = { Text(label) },
                singleLine = true,
                isError = problem != null,
                keyboardOptions = KeyboardOptions(keyboardType = if (key == SettingKey.AI_MONTHLY_BUDGET_USD) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            problem != null -> Text(problemText(key, problem), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            form.isOverride(key) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_source_override, shown(key, setting.envValue)),
                    style = MaterialTheme.typography.bodySmall, color = scheme.primary, modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = { presenter.reset(key) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(Res.string.settings_reset), style = MaterialTheme.typography.labelMedium)
                }
            }
            else -> Text(stringResource(Res.string.settings_source_env), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun problemText(key: SettingKey, code: String): String = when (code) {
    "NOT_A_NUMBER" -> stringResource(Res.string.settings_problem_number)
    "OUT_OF_RANGE" -> {
        val range = SETTING_RANGES[key]
        if (range != null) stringResource(Res.string.settings_problem_range, "${range.first}", "${range.last}")
        else stringResource(Res.string.settings_problem_range, "0", "$MAX_AI_MONTHLY_BUDGET_USD")
    }
    else -> stringResource(Res.string.settings_problem_list)
}

/** A field that picks one of [options] (value to label). */
@Composable
private fun SettingSelect(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { open = true }) { Symbol(Symbols.expandMore, contentDescription = label) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}
