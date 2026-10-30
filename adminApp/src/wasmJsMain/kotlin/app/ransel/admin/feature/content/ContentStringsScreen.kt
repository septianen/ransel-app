package app.ransel.admin.feature.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.content_close
import app.ransel.admin.generated.resources.content_discard_draft
import app.ransel.admin.generated.resources.content_empty
import app.ransel.admin.generated.resources.content_en
import app.ransel.admin.generated.resources.content_id
import app.ransel.admin.generated.resources.content_preview
import app.ransel.admin.generated.resources.content_problem_long
import app.ransel.admin.generated.resources.content_problem_no_placeholder
import app.ransel.admin.generated.resources.content_problem_placeholder
import app.ransel.admin.generated.resources.content_published
import app.ransel.admin.generated.resources.content_reset
import app.ransel.admin.generated.resources.content_save_draft
import app.ransel.admin.generated.resources.content_saved
import app.ransel.admin.generated.resources.content_show_more
import app.ransel.admin.generated.resources.content_version
import app.ransel.admin.generated.resources.strings_bundled
import app.ransel.admin.generated.resources.strings_col_key
import app.ransel.admin.generated.resources.strings_edit_title
import app.ransel.admin.generated.resources.strings_filter_all
import app.ransel.admin.generated.resources.strings_filter_changed
import app.ransel.admin.generated.resources.strings_filter_drafts
import app.ransel.admin.generated.resources.strings_pick
import app.ransel.admin.generated.resources.strings_rule
import app.ransel.admin.generated.resources.strings_rule_placeholders
import app.ransel.admin.generated.resources.strings_search
import app.ransel.admin.generated.resources.strings_subtitle
import app.ransel.admin.generated.resources.strings_title
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.domain.MAX_CONTENT_STRING
import app.ransel.shared.domain.placeholdersOf
import app.ransel.shared.dto.AdminContentStringDto
import app.ransel.shared.model.ContentStatus
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import org.jetbrains.compose.resources.stringResource

/** Design `KontenTeks` ("Teks & wording", F9.6): every app string, its draft and the live override. */
@Composable
fun ContentStringsScreen(api: AdminApi, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { ContentStringsPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    val data = state.data
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.strings_title), stringResource(Res.string.strings_subtitle)) {
            if (data != null) {
                Text(stringResource(Res.string.content_version, data.version, data.drafts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PublishButton(data.drafts, state.publishing, presenter::askToPublish)
            }
        }
        state.publishedVersion?.let { Text(stringResource(Res.string.content_published, it), color = MaterialTheme.colorScheme.primary) }
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = presenter::search,
                        placeholder = { Text(stringResource(Res.string.strings_search)) },
                        leadingIcon = { Symbol(Symbols.search, size = 20.dp) },
                        singleLine = true,
                        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
                    )
                    listOf(
                        StringFilter.ALL to Res.string.strings_filter_all,
                        StringFilter.DRAFTS to Res.string.strings_filter_drafts,
                        StringFilter.CHANGED to Res.string.strings_filter_changed,
                    ).forEach { (f, label) ->
                        FilterChip(selected = state.filter == f, onClick = { presenter.filter(f) }, label = { Text(stringResource(label)) })
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val wide = maxWidth >= 900.dp
                    val table: @Composable (Modifier) -> Unit = { m -> StringsTable(state, presenter, m) }
                    val editor: @Composable (Modifier) -> Unit = { m -> StringEditor(state, presenter, m) }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            table(Modifier.weight(3f))
                            editor(Modifier.weight(2f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            editor(Modifier.fillMaxWidth())
                            table(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    if (state.confirmPublish && data != null) {
        PublishDialog(data.drafts, state.publishing, state.publishError?.let { failureText(it) }, presenter::publish, presenter::dismissPublish)
    }
}

@Composable
private fun StringsTable(state: ContentStringsUiState, presenter: ContentStringsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val rows = state.rows
    AdminCard(modifier, padding = 0.dp) {
        Column {
            if (rows.isEmpty()) Text(stringResource(Res.string.content_empty), Modifier.padding(20.dp), color = scheme.onSurfaceVariant)
            rows.take(state.limit).forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
                val selected = state.edit?.key == row.key
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface)
                        .clickable(role = Role.Button) { presenter.open(row) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1.2f)) {
                        Text(row.key, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.area, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                    }
                    Text(row.shownId(), Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(row.shownEn(), Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    ContentStatusPill(row.status)
                }
            }
            if (rows.size > state.limit) {
                TextButton(onClick = presenter::showMore, modifier = Modifier.padding(8.dp)) { Text(stringResource(Res.string.content_show_more)) }
            }
        }
    }
}

@Composable
private fun StringEditor(state: ContentStringsUiState, presenter: ContentStringsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val edit = state.edit
    val row = state.selected
    AdminCard(modifier) {
        if (edit == null || row == null) {
            Text(stringResource(Res.string.strings_pick), color = scheme.onSurfaceVariant)
            return@AdminCard
        }
        val problems = edit.problems(row)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.strings_edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = presenter::close) { Text(stringResource(Res.string.content_close)) }
            }
            Column {
                Text(row.key, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.area, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                    ContentStatusPill(row.status)
                }
            }
            StringField(stringResource(Res.string.content_id), edit.id, problems["id"], row.defaults.id.orEmpty(), presenter::editId)
            StringField(stringResource(Res.string.content_en), edit.en, problems["en"], row.defaults.en.orEmpty(), presenter::editEn)
            val tokens = placeholdersOf(row.defaults.id.orEmpty())
            Text(
                if (tokens.isEmpty()) stringResource(Res.string.strings_rule, MAX_CONTENT_STRING)
                else stringResource(Res.string.strings_rule_placeholders, MAX_CONTENT_STRING, tokens.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.content_preview), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(edit.id.ifBlank { row.defaults.id.orEmpty() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(Res.string.strings_bundled, row.defaults.id.orEmpty()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            state.saveError?.let { ErrorLine(failureText(it)) }
            if (state.savedKey == row.key) Text(stringResource(Res.string.content_saved), color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.status == ContentStatus.DRAFT) {
                    TextButton(onClick = presenter::discardDraft, enabled = !state.saving, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.content_discard_draft)) }
                }
                TextButton(onClick = presenter::resetToDefault, enabled = !state.saving, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.content_reset)) }
                Button(onClick = presenter::saveDraft, enabled = !state.saving && problems.isEmpty(), modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.content_save_draft))
                }
            }
        }
    }
}

@Composable
private fun StringField(label: String, value: String, problem: String?, bundled: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(MAX_CONTENT_STRING + 20)) },
            label = { Text(label) },
            minLines = 2,
            isError = problem != null,
            modifier = Modifier.fillMaxWidth(),
        )
        when (problem) {
            null -> Unit
            "TOO_LONG" -> ErrorLine(stringResource(Res.string.content_problem_long, MAX_CONTENT_STRING))
            else -> {
                val tokens = placeholdersOf(bundled)
                ErrorLine(
                    if (tokens.isEmpty()) stringResource(Res.string.content_problem_no_placeholder)
                    else stringResource(Res.string.content_problem_placeholder, tokens.joinToString(", ")),
                )
            }
        }
    }
}
