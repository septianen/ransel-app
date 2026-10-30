package app.ransel.admin.feature.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.content_discard_draft
import app.ransel.admin.generated.resources.content_empty
import app.ransel.admin.generated.resources.content_problem_error_placeholder
import app.ransel.admin.generated.resources.content_problem_long
import app.ransel.admin.generated.resources.content_problem_unknown
import app.ransel.admin.generated.resources.content_published
import app.ransel.admin.generated.resources.content_reset
import app.ransel.admin.generated.resources.content_save_draft
import app.ransel.admin.generated.resources.content_saved
import app.ransel.admin.generated.resources.content_version
import app.ransel.admin.generated.resources.errors_button_en
import app.ransel.admin.generated.resources.errors_button_id
import app.ransel.admin.generated.resources.errors_count_today
import app.ransel.admin.generated.resources.errors_default
import app.ransel.admin.generated.resources.errors_display
import app.ransel.admin.generated.resources.errors_display_dialog
import app.ransel.admin.generated.resources.errors_display_full
import app.ransel.admin.generated.resources.errors_display_snackbar
import app.ransel.admin.generated.resources.errors_hint
import app.ransel.admin.generated.resources.errors_illustration
import app.ransel.admin.generated.resources.errors_message_en
import app.ransel.admin.generated.resources.errors_message_id
import app.ransel.admin.generated.resources.errors_preview_app
import app.ransel.admin.generated.resources.errors_preview_note
import app.ransel.admin.generated.resources.errors_search
import app.ransel.admin.generated.resources.errors_subtitle
import app.ransel.admin.generated.resources.errors_title
import app.ransel.admin.generated.resources.errors_title_en
import app.ransel.admin.generated.resources.errors_title_id
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.domain.MAX_ERROR_BUTTON
import app.ransel.shared.domain.MAX_ERROR_MESSAGE
import app.ransel.shared.domain.MAX_ERROR_TITLE
import app.ransel.shared.domain.validateErrorText
import app.ransel.shared.dto.AdminContentErrorDto
import app.ransel.shared.dto.AdminContentErrorsDto
import app.ransel.shared.dto.ErrorTextDto
import app.ransel.shared.dto.UpdateContentErrorRequest
import app.ransel.shared.model.ContentStatus
import app.ransel.shared.model.ErrorDisplay
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class ContentErrorsUiState(
    val loading: Boolean = true,
    val data: AdminContentErrorsDto? = null,
    val error: ApiFailure? = null,
    val query: String = "",
    val code: String? = null,
    val text: ErrorTextDto = ErrorTextDto(),
    val saving: Boolean = false,
    val saveError: ApiFailure? = null,
    val saved: Boolean = false,
    val confirmPublish: Boolean = false,
    val publishing: Boolean = false,
    val publishError: ApiFailure? = null,
    val publishedVersion: Int? = null,
) {
    val selected: AdminContentErrorDto? get() = code?.let { c -> data?.errors?.firstOrNull { it.code == c } }
    val problems: Map<String, String> get() = validateErrorText(text, data?.illustrations.orEmpty())
}

/** What the editor starts from: the draft, else the live message, else empty (the app's own copy). */
fun AdminContentErrorDto.current(): ErrorTextDto = draft ?: published ?: ErrorTextDto()

/** "Pesan error" (F9.6, design `KontenError`). */
class ContentErrorsPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(ContentErrorsUiState())
    val state: StateFlow<ContentErrorsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = api.contentErrors()) {
                is ApiResult.Success -> _state.update { s ->
                    val first = s.code ?: r.value.errors.firstOrNull()?.code
                    s.copy(loading = false, data = r.value, code = first, text = r.value.errors.firstOrNull { it.code == first }?.current() ?: ErrorTextDto())
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
        }
    }

    fun search(query: String) = _state.update { it.copy(query = query) }

    fun select(row: AdminContentErrorDto) = _state.update { it.copy(code = row.code, text = row.current(), saveError = null, saved = false) }

    fun edit(change: (ErrorTextDto) -> ErrorTextDto) = _state.update { it.copy(text = change(it.text), saved = false) }

    fun resetToDefault() = _state.update { it.copy(text = ErrorTextDto(), saved = false) }

    fun saveDraft() = send(discard = false)
    fun discardDraft() = send(discard = true)

    private fun send(discard: Boolean) {
        val s = _state.value
        val code = s.code ?: return
        if (s.saving || (!discard && s.problems.isNotEmpty())) return
        val before = s.selected ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            when (val r = api.saveContentError(UpdateContentErrorRequest(code, s.text, discard))) {
                is ApiResult.Success -> _state.update { st ->
                    val data = st.data ?: return@update st.copy(saving = false)
                    val delta = (if (r.value.status == ContentStatus.DRAFT) 1 else 0) - (if (before.status == ContentStatus.DRAFT) 1 else 0)
                    st.copy(
                        saving = false, saved = true, text = r.value.current(),
                        data = data.copy(errors = data.errors.map { if (it.code == code) r.value else it }, drafts = data.drafts + delta),
                    )
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(saving = false, saveError = r.error) }
                }
            }
        }
    }

    fun askToPublish() = _state.update { if ((it.data?.drafts ?: 0) > 0) it.copy(confirmPublish = true, publishError = null) else it }
    fun dismissPublish() = _state.update { if (it.publishing) it else it.copy(confirmPublish = false) }

    fun publish() {
        if (_state.value.publishing) return
        _state.update { it.copy(publishing = true, publishError = null) }
        scope.launch {
            when (val r = api.publishContent()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(publishing = false, confirmPublish = false, publishedVersion = r.value.version) }
                    reload()
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(publishing = false, publishError = r.error) }
                }
            }
        }
    }
}

@Composable
fun ContentErrorsScreen(api: AdminApi, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { ContentErrorsPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    val data = state.data
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.errors_title), data?.let { stringResource(Res.string.errors_subtitle, it.errors.size) }) {
            if (data != null) {
                Text(stringResource(Res.string.content_version, data.version, data.drafts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PublishButton(data.drafts, state.publishing, presenter::askToPublish)
            }
        }
        state.publishedVersion?.let { Text(stringResource(Res.string.content_published, it), color = MaterialTheme.colorScheme.primary) }
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 1100.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        CodeList(state, presenter, Modifier.weight(1f))
                        ErrorEditor(state, presenter, Modifier.weight(2f))
                        PhonePreview(state.text, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CodeList(state, presenter, Modifier.fillMaxWidth())
                        ErrorEditor(state, presenter, Modifier.fillMaxWidth())
                        PhonePreview(state.text, Modifier.fillMaxWidth())
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
private fun CodeList(state: ContentErrorsUiState, presenter: ContentErrorsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val q = state.query.trim().uppercase()
    val rows = state.data?.errors.orEmpty().filter { q.isEmpty() || q in it.code }
    AdminCard(modifier, padding = 8.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedTextField(
                value = state.query, onValueChange = presenter::search, singleLine = true,
                placeholder = { Text(stringResource(Res.string.errors_search)) },
                leadingIcon = { Symbol(Symbols.search, size = 18.dp) },
                modifier = Modifier.fillMaxWidth().padding(4.dp),
            )
            if (rows.isEmpty()) Text(stringResource(Res.string.content_empty), Modifier.padding(12.dp), color = scheme.onSurfaceVariant)
            rows.forEach { row ->
                val selected = row.code == state.code
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) scheme.primaryContainer else scheme.surface, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { presenter.select(row) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.code, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        row.current().titleId?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    }
                    if (row.status != ContentStatus.DEFAULT) ContentStatusPill(row.status)
                }
            }
        }
    }
}

@Composable
private fun ErrorEditor(state: ContentErrorsUiState, presenter: ContentErrorsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val row = state.selected ?: return
    val t = state.text
    val problems = state.problems
    AdminCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.code, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(stringResource(Res.string.errors_count_today, row.countToday), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                ContentStatusPill(row.status)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val field: @Composable (StringResource, String?, String, Int, Boolean, (String?) -> ErrorTextDto) -> Unit = { label, value, key, max, multi, set ->
                    ErrorField(stringResource(label), value, problems[key], max, multi) { presenter.edit { _ -> set(it) } }
                }
                field(Res.string.errors_title_id, t.titleId, "titleId", MAX_ERROR_TITLE, false) { t.copy(titleId = it) }
                field(Res.string.errors_title_en, t.titleEn, "titleEn", MAX_ERROR_TITLE, false) { t.copy(titleEn = it) }
                field(Res.string.errors_message_id, t.messageId, "messageId", MAX_ERROR_MESSAGE, true) { t.copy(messageId = it) }
                field(Res.string.errors_message_en, t.messageEn, "messageEn", MAX_ERROR_MESSAGE, true) { t.copy(messageEn = it) }
                field(Res.string.errors_button_id, t.buttonId, "buttonId", MAX_ERROR_BUTTON, false) { t.copy(buttonId = it) }
                field(Res.string.errors_button_en, t.buttonEn, "buttonEn", MAX_ERROR_BUTTON, false) { t.copy(buttonEn = it) }
                val default = stringResource(Res.string.errors_default)
                Picker(
                    stringResource(Res.string.errors_illustration), t.illustration ?: default,
                    listOf<Pair<String?, String>>(null to default) + state.data?.illustrations.orEmpty().map { it to it },
                ) { v -> presenter.edit { it.copy(illustration = v) } }
                Picker(
                    stringResource(Res.string.errors_display), t.display?.let { displayLabel(it) } ?: default,
                    listOf<Pair<ErrorDisplay?, String>>(null to default) + ErrorDisplay.entries.map { it to displayLabel(it) },
                ) { v -> presenter.edit { it.copy(display = v) } }
            }
            Text(stringResource(Res.string.errors_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            state.saveError?.let { ErrorLine(failureText(it)) }
            if (state.saved) Text(stringResource(Res.string.content_saved), color = scheme.primary)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
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
private fun displayLabel(display: ErrorDisplay): String = stringResource(
    when (display) {
        ErrorDisplay.FULL_SCREEN -> Res.string.errors_display_full
        ErrorDisplay.SNACKBAR -> Res.string.errors_display_snackbar
        ErrorDisplay.DIALOG -> Res.string.errors_display_dialog
    },
)

@Composable
private fun ErrorField(label: String, value: String?, problem: String?, max: Int, multiline: Boolean, onChange: (String?) -> Unit) {
    Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = { onChange(it.take(max + 20).ifEmpty { null }) },
            label = { Text(label) },
            singleLine = !multiline,
            minLines = if (multiline) 2 else 1,
            isError = problem != null,
            modifier = Modifier.fillMaxWidth(),
        )
        when (problem) {
            null -> Unit
            "TOO_LONG" -> ErrorLine(stringResource(Res.string.content_problem_long, max))
            else -> ErrorLine(stringResource(Res.string.content_problem_error_placeholder))
        }
    }
}

@Composable
private fun <T> Picker(label: String, shown: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(260.dp)) {
        OutlinedTextField(
            value = shown, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            trailingIcon = { IconButton(onClick = { open = true }) { Symbol(Symbols.expandMore, contentDescription = label) } },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

/** The design's phone frame: the message as the app would show it full screen, in ID or EN. */
@Composable
private fun PhonePreview(text: ErrorTextDto, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    var english by remember { mutableStateOf(false) }
    val app = stringResource(Res.string.errors_preview_app)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow {
            listOf(false to "ID", true to "EN").forEachIndexed { i, (en, label) ->
                SegmentedButton(selected = english == en, onClick = { english = en }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        Column(
            Modifier.width(260.dp).heightIn(min = 460.dp)
                .border(8.dp, scheme.onSurface, RoundedCornerShape(36.dp))
                .background(scheme.surface, RoundedCornerShape(36.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(120.dp).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Symbol(Symbols.error, size = 56.dp, tint = scheme.onTertiaryContainer)
            }
            Text((if (english) text.titleEn else text.titleId) ?: app, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text((if (english) text.messageEn else text.messageId) ?: app, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            (if (english) text.buttonEn else text.buttonId)?.let {
                Text(
                    it, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary,
                    modifier = Modifier.background(scheme.primary, CircleShape).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        Text(stringResource(Res.string.errors_preview_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
