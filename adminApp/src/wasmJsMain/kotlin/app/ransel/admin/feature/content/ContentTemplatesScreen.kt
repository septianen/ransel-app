package app.ransel.admin.feature.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.templates_add_item
import app.ransel.admin.generated.resources.templates_cancel
import app.ransel.admin.generated.resources.templates_count
import app.ransel.admin.generated.resources.templates_delete
import app.ransel.admin.generated.resources.templates_delete_body
import app.ransel.admin.generated.resources.templates_delete_title
import app.ransel.admin.generated.resources.templates_deleted
import app.ransel.admin.generated.resources.templates_item_name
import app.ransel.admin.generated.resources.templates_item_quantity
import app.ransel.admin.generated.resources.templates_name
import app.ransel.admin.generated.resources.templates_new
import app.ransel.admin.generated.resources.templates_note
import app.ransel.admin.generated.resources.templates_pick
import app.ransel.admin.generated.resources.templates_remove_item
import app.ransel.admin.generated.resources.templates_restore
import app.ransel.admin.generated.resources.templates_save
import app.ransel.admin.generated.resources.templates_saved
import app.ransel.admin.generated.resources.templates_stats
import app.ransel.admin.generated.resources.templates_subtitle
import app.ransel.admin.generated.resources.templates_title
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.Pill
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.domain.MAX_TEMPLATE_ITEM_NAME
import app.ransel.shared.domain.MAX_TEMPLATE_NAME
import app.ransel.shared.domain.MAX_TEMPLATE_QUANTITY
import app.ransel.shared.domain.validateBuiltinTemplate
import app.ransel.shared.dto.AdminPackingTemplateDto
import app.ransel.shared.dto.AdminPackingTemplatesDto
import app.ransel.shared.dto.SaveBuiltinTemplateRequest
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.model.CategoryKind
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The template being edited; [id] null = a new one, not saved yet. */
data class TemplateEdit(val id: String?, val name: String, val items: List<TemplateItem>) {
    val problems: Map<String, String> get() = validateBuiltinTemplate(name, items)
}

data class ContentTemplatesUiState(
    val loading: Boolean = true,
    val data: AdminPackingTemplatesDto? = null,
    val error: ApiFailure? = null,
    /** Packing category → its label as the apps show it (Karung's "Kategori" page, else the bundled label). */
    val labels: Map<PackingCategory, String> = emptyMap(),
    /** Packing categories in the order the apps show them. */
    val order: List<PackingCategory> = PackingCategory.entries,
    val edit: TemplateEdit? = null,
    val saving: Boolean = false,
    val saveError: ApiFailure? = null,
    val saved: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val selected: AdminPackingTemplateDto? get() = edit?.id?.let { id -> data?.templates?.firstOrNull { it.id == id } }
    val changed: Boolean get() = edit != null && (selected?.let { it.name != edit.name || it.items != edit.items } ?: true)
}

/**
 * "Template packing" (owner, 3 Nov 2026): the built-in templates, saved and live at once like "Kategori". User
 * templates only show as two counts.
 */
class ContentTemplatesPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val newId: () -> String,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(ContentTemplatesUiState())
    val state: StateFlow<ContentTemplatesUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = api.packingTemplates()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = r.value) }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
            // the packing labels and order of the "Kategori" page; without them the constants' own order and names
            (api.contentCategories() as? ApiResult.Success)?.value?.categories?.filter { it.category.kind == CategoryKind.PACKING }?.let { rows ->
                val byConstant = rows.associateBy { it.category.constant }
                _state.update { s ->
                    s.copy(
                        labels = PackingCategory.entries.associateWith { c -> byConstant[c.name]?.let { it.category.labelId ?: it.defaultLabelId } ?: c.name },
                        order = rows.sortedBy { it.category.sortOrder }.mapNotNull { r -> PackingCategory.entries.firstOrNull { it.name == r.category.constant } }
                            .ifEmpty { PackingCategory.entries },
                    )
                }
            }
        }
    }

    fun open(template: AdminPackingTemplateDto) =
        _state.update { it.copy(edit = TemplateEdit(template.id, template.name, template.items), saveError = null, saved = false) }

    fun newTemplate() = _state.update {
        it.copy(edit = TemplateEdit(null, "", listOf(TemplateItem(it.order.first(), ""))), saveError = null, saved = false)
    }

    fun close() = _state.update { if (it.saving) it else it.copy(edit = null, saveError = null, saved = false) }

    fun rename(name: String) = edit { it.copy(name = name.take(MAX_TEMPLATE_NAME + 10)) }

    fun editItem(index: Int, change: (TemplateItem) -> TemplateItem) =
        edit { e -> e.copy(items = e.items.mapIndexed { i, item -> if (i == index) change(item) else item }) }

    fun addItem(category: PackingCategory) = edit { e ->
        // after the last item of that category, so the list keeps its grouping
        val at = e.items.indexOfLast { it.category == category }.let { if (it < 0) e.items.size else it + 1 }
        e.copy(items = e.items.toMutableList().apply { add(at, TemplateItem(category, "")) })
    }

    fun removeItem(index: Int) = edit { e -> e.copy(items = e.items.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val edit = _state.value.edit ?: return
        if (_state.value.saving || edit.problems.isNotEmpty()) return
        val body = SaveBuiltinTemplateRequest(edit.id ?: newId(), edit.name, edit.items)
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            val result = if (edit.id == null) api.createPackingTemplate(body) else api.savePackingTemplate(edit.id, body)
            applyResult(result) { saved -> copy(edit = TemplateEdit(saved.id, saved.name, saved.items), saved = true) }
        }
    }

    fun askToDelete() = _state.update { if (it.selected?.deletedAt == null && it.selected != null) it.copy(confirmDelete = true, saveError = null) else it }
    fun dismissDelete() = _state.update { if (it.saving) it else it.copy(confirmDelete = false) }

    fun delete() {
        val id = _state.value.selected?.id ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch { applyResult(api.deletePackingTemplate(id)) { copy(confirmDelete = false) } }
    }

    fun restore() {
        val id = _state.value.selected?.id ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch { applyResult(api.restorePackingTemplate(id)) { this } }
    }

    private fun edit(change: (TemplateEdit) -> TemplateEdit) =
        _state.update { s -> s.edit?.let { s.copy(edit = change(it), saved = false) } ?: s }

    private suspend fun applyResult(
        result: ApiResult<AdminPackingTemplateDto>,
        then: ContentTemplatesUiState.(AdminPackingTemplateDto) -> ContentTemplatesUiState,
    ) {
        when (result) {
            is ApiResult.Success -> _state.update { s ->
                val saved = result.value
                val others = s.data?.templates.orEmpty().filter { it.id != saved.id }
                val templates = (others + saved).sortedWith(compareBy({ it.deletedAt != null }, { it.name.lowercase() }))
                s.copy(saving = false, data = s.data?.copy(templates = templates)).then(saved)
            }
            is ApiResult.Failure -> {
                onAccessLost(result.error)
                _state.update { it.copy(saving = false, saveError = result.error) }
            }
        }
    }
}

@Composable
fun ContentTemplatesScreen(api: AdminApi, newId: () -> String, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { ContentTemplatesPresenter(api, it, newId, onAccessLost) }
    val state by presenter.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.templates_title), stringResource(Res.string.templates_subtitle)) {
            Button(onClick = presenter::newTemplate, enabled = !state.saving, modifier = Modifier.heightIn(min = 44.dp)) {
                Symbol(Symbols.add, size = 18.dp)
                Text(stringResource(Res.string.templates_new), Modifier.padding(start = 8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(IrisTheme.accents.blue.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.info, size = 22.dp, tint = scheme.onSurface)
            Text(stringResource(Res.string.templates_note), style = MaterialTheme.typography.bodyMedium)
        }
        val data = state.data
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> {
                AdminCard {
                    Text(stringResource(Res.string.templates_stats, data.userTemplateCount, data.sharedTemplateCount), style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                    AdminCard(Modifier.widthIn(min = 260.dp).width(320.dp), padding = 0.dp) {
                        Column {
                            data.templates.forEachIndexed { i, t ->
                                if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
                                TemplateRow(t, selected = state.edit?.id == t.id, onClick = { presenter.open(t) })
                            }
                        }
                    }
                    AdminCard(Modifier.weight(1f)) {
                        val edit = state.edit
                        if (edit == null) Text(stringResource(Res.string.templates_pick), color = scheme.onSurfaceVariant)
                        else TemplateEditor(state, edit, presenter)
                    }
                }
            }
        }
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(Res.string.templates_delete_title, state.selected?.name.orEmpty()),
            body = stringResource(Res.string.templates_delete_body),
            confirmLabel = stringResource(Res.string.templates_delete),
            destructive = true,
            busy = state.saving,
            error = state.saveError?.let { failureText(it) },
            onConfirm = presenter::delete,
            onDismiss = presenter::dismissDelete,
        )
    }
}

@Composable
private fun TemplateRow(template: AdminPackingTemplateDto, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) scheme.secondaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(Symbols.luggage, size = 22.dp, tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.templates_count, template.items.size), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (template.deletedAt != null) Pill(stringResource(Res.string.templates_deleted), scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun TemplateEditor(state: ContentTemplatesUiState, edit: TemplateEdit, presenter: ContentTemplatesPresenter) {
    val scheme = MaterialTheme.colorScheme
    val deleted = state.selected?.deletedAt != null
    val problems = edit.problems
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = edit.name, onValueChange = presenter::rename, singleLine = true, enabled = !deleted,
            label = { Text(stringResource(Res.string.templates_name)) },
            isError = "name" in problems && edit.name.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        state.order.forEach { category ->
            val rows = edit.items.withIndex().filter { it.value.category == category }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.labels[category] ?: category.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                rows.forEach { (index, item) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = item.name, singleLine = true, enabled = !deleted,
                            onValueChange = { t -> presenter.editItem(index) { it.copy(name = t.take(MAX_TEMPLATE_ITEM_NAME + 10)) } },
                            placeholder = { Text(stringResource(Res.string.templates_item_name)) },
                            isError = "items.$index" in problems && item.name.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = item.quantity?.toString().orEmpty(), singleLine = true, enabled = !deleted,
                            onValueChange = { t ->
                                val n = t.filter(Char::isDigit).take(3).toIntOrNull()
                                presenter.editItem(index) { it.copy(quantity = n?.coerceAtMost(MAX_TEMPLATE_QUANTITY)) }
                            },
                            label = { Text(stringResource(Res.string.templates_item_quantity)) },
                            modifier = Modifier.width(110.dp),
                        )
                        IconButton(onClick = { presenter.removeItem(index) }, enabled = !deleted) {
                            Symbol(Symbols.delete, contentDescription = stringResource(Res.string.templates_remove_item, item.name.ifBlank { "—" }))
                        }
                    }
                }
                if (!deleted) {
                    TextButton(onClick = { presenter.addItem(category) }) {
                        Symbol(Symbols.add, size = 18.dp)
                        Text(stringResource(Res.string.templates_add_item), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        state.saveError?.takeIf { !state.confirmDelete }?.let { Text(failureText(it), color = scheme.error) }
        if (state.saved) Text(stringResource(Res.string.templates_saved), color = scheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (deleted) {
                Button(onClick = presenter::restore, enabled = !state.saving) { Text(stringResource(Res.string.templates_restore)) }
            } else {
                Button(onClick = presenter::save, enabled = state.changed && problems.isEmpty() && !state.saving) {
                    Text(stringResource(Res.string.templates_save))
                }
                if (edit.id != null) OutlinedButton(onClick = presenter::askToDelete, enabled = !state.saving) { Text(stringResource(Res.string.templates_delete)) }
            }
            TextButton(onClick = presenter::close, enabled = !state.saving) { Text(stringResource(Res.string.templates_cancel)) }
        }
    }
}
