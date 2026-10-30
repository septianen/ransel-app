package app.ransel.admin.feature.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.content_empty
import app.ransel.admin.generated.resources.interests_add
import app.ransel.admin.generated.resources.interests_add_hint
import app.ransel.admin.generated.resources.interests_col_chips
import app.ransel.admin.generated.resources.interests_col_destination
import app.ransel.admin.generated.resources.interests_col_language
import app.ransel.admin.generated.resources.interests_col_made
import app.ransel.admin.generated.resources.interests_col_month
import app.ransel.admin.generated.resources.interests_edit_title
import app.ransel.admin.generated.resources.interests_hide
import app.ransel.admin.generated.resources.interests_kind_ai
import app.ransel.admin.generated.resources.interests_kind_hidden
import app.ransel.admin.generated.resources.interests_kind_manual
import app.ransel.admin.generated.resources.interests_kind_pinned
import app.ransel.admin.generated.resources.interests_meta_ai
import app.ransel.admin.generated.resources.interests_meta_curated
import app.ransel.admin.generated.resources.interests_meta_none
import app.ransel.admin.generated.resources.interests_note
import app.ransel.admin.generated.resources.interests_pick
import app.ransel.admin.generated.resources.interests_pin
import app.ransel.admin.generated.resources.interests_regenerate
import app.ransel.admin.generated.resources.interests_remove
import app.ransel.admin.generated.resources.interests_save
import app.ransel.admin.generated.resources.interests_saved
import app.ransel.admin.generated.resources.interests_search
import app.ransel.admin.generated.resources.interests_status_ai
import app.ransel.admin.generated.resources.interests_status_curated
import app.ransel.admin.generated.resources.interests_status_expired
import app.ransel.admin.generated.resources.interests_subtitle
import app.ransel.admin.generated.resources.interests_title
import app.ransel.admin.generated.resources.interests_too_many
import app.ransel.admin.generated.resources.interests_unhide
import app.ransel.admin.generated.resources.interests_unpin
import app.ransel.admin.generated.resources.strings_col_status
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ErrorLine
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.LocalAdminFormat
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.Pill
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.domain.MAX_INTEREST_CHIPS
import app.ransel.shared.domain.MAX_INTEREST_LABEL
import app.ransel.shared.dto.AdminInterestCurationDto
import app.ransel.shared.dto.AdminInterestSummaryDto
import app.ransel.shared.dto.InterestChipDto
import app.ransel.shared.model.InterestChipKind
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

data class ContentInterestsUiState(
    val loading: Boolean = true,
    val rows: List<AdminInterestSummaryDto>? = null,
    val error: ApiFailure? = null,
    val query: String = "",
    val curation: AdminInterestCurationDto? = null,
    val chips: List<InterestChipDto> = emptyList(),
    val newChip: String = "",
    val busy: Boolean = false,
    val actionError: ApiFailure? = null,
    val saved: Boolean = false,
) {
    val tooMany: Boolean get() = chips.size > MAX_INTEREST_CHIPS || chips.any { it.label.length > MAX_INTEREST_LABEL }
    val changed: Boolean get() = curation != null && chips != curation.chips
}

/** "Minat khas" (F9.6, design `KontenMinatKhas`): curate the AI's chips; a save is live for Explore at once. */
class ContentInterestsPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(ContentInterestsUiState())
    val state: StateFlow<ContentInterestsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        load()
    }

    private fun load() {
        searchJob?.cancel()
        val query = _state.value.query
        searchJob = scope.launch {
            if (query.isNotEmpty()) delay(300)
            when (val r = api.contentInterests(query)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, rows = r.value, error = null) }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
        }
    }

    fun reload() {
        _state.update { it.copy(loading = true) }
        load()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        load()
    }

    fun open(row: AdminInterestSummaryDto) = act { api.contentInterest(row.destination, row.month, row.language) }

    fun regenerate() {
        val c = _state.value.curation ?: return
        act { api.regenerateContentInterest(c.destination, c.month, c.language) }
    }

    fun save() {
        val s = _state.value
        val c = s.curation ?: return
        if (s.tooMany) return
        act(saved = true) { api.saveContentInterest(c.copy(chips = s.chips)) }
    }

    private fun act(saved: Boolean = false, call: suspend () -> ApiResult<AdminInterestCurationDto>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null, saved = false) }
        scope.launch {
            when (val r = call()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, curation = r.value, chips = r.value.chips, saved = saved) }
                    if (saved) load()
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(busy = false, actionError = r.error) }
                }
            }
        }
    }

    /** Pin an AI chip, unpin it, hide it or show it again. */
    fun setKind(label: String, kind: InterestChipKind) =
        _state.update { s -> s.copy(chips = s.chips.map { if (it.label == label) it.copy(kind = kind) else it }, saved = false) }

    fun remove(label: String) = _state.update { s -> s.copy(chips = s.chips.filterNot { it.label == label }, saved = false) }

    fun typeChip(text: String) = _state.update { it.copy(newChip = text.take(MAX_INTEREST_LABEL)) }

    fun addChip() = _state.update { s ->
        val label = s.newChip.trim()
        if (label.isEmpty() || s.chips.any { it.label.equals(label, ignoreCase = true) }) return@update s.copy(newChip = "")
        // a hand-added chip goes after the pinned and manual ones, before the AI ones
        val at = s.chips.indexOfFirst { it.kind == InterestChipKind.AI || it.kind == InterestChipKind.HIDDEN }.takeIf { it >= 0 } ?: s.chips.size
        s.copy(chips = s.chips.toMutableList().apply { add(at, InterestChipDto(label, InterestChipKind.MANUAL)) }, newChip = "", saved = false)
    }
}

@Composable
fun ContentInterestsScreen(api: AdminApi, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { ContentInterestsPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.interests_title), stringResource(Res.string.interests_subtitle)) {
            OutlinedTextField(
                value = state.query, onValueChange = presenter::search, singleLine = true,
                placeholder = { Text(stringResource(Res.string.interests_search)) },
                leadingIcon = { Symbol(Symbols.search, size = 20.dp) },
                modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
            )
        }
        val rows = state.rows
        when {
            rows == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            rows == null -> LoadingPanel()
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 900.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        InterestTable(rows, state, presenter, Modifier.weight(3f))
                        CurationCard(state, presenter, Modifier.weight(2f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CurationCard(state, presenter, Modifier.fillMaxWidth())
                        InterestTable(rows, state, presenter, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun InterestTable(rows: List<AdminInterestSummaryDto>, state: ContentInterestsUiState, presenter: ContentInterestsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val now = Clock.System.now()
    AdminCard(modifier, padding = 0.dp) {
        Column {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    2f to Res.string.interests_col_destination, 1f to Res.string.interests_col_month, 1f to Res.string.interests_col_language,
                    0.8f to Res.string.interests_col_chips, 1.4f to Res.string.interests_col_made, 1.2f to Res.string.strings_col_status,
                ).forEach { (w, label) -> Text(stringResource(label), Modifier.weight(w), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
            }
            if (rows.isEmpty()) Text(stringResource(Res.string.content_empty), Modifier.padding(20.dp), color = scheme.onSurfaceVariant)
            rows.forEach { row ->
                HorizontalDivider(color = scheme.outlineVariant)
                val c = state.curation
                val selected = c != null && c.destination == row.destination && c.month == row.month && c.language == row.language
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface)
                        .clickable(role = Role.Button) { presenter.open(row) }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.destination.replaceFirstChar { it.uppercase() }, Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text(format.monthName(row.month), Modifier.weight(1f))
                    Text(row.language.uppercase(), Modifier.weight(1f), color = scheme.onSurfaceVariant)
                    Text(format.number(row.chips.toLong()), Modifier.weight(0.8f))
                    Text(row.aiCreatedAt?.let { format.date(it) } ?: "—", Modifier.weight(1.4f), color = scheme.onSurfaceVariant)
                    Row(Modifier.weight(1.2f)) {
                        val expired = row.aiExpiresAt?.let { it < now } == true
                        when {
                            row.curated -> Pill(stringResource(Res.string.interests_status_curated), scheme.primaryContainer, scheme.onPrimaryContainer)
                            expired -> Pill(stringResource(Res.string.interests_status_expired), scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
                            else -> Pill(stringResource(Res.string.interests_status_ai), scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurationCard(state: ContentInterestsUiState, presenter: ContentInterestsPresenter, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val format = LocalAdminFormat.current
    val c = state.curation
    AdminCard(modifier) {
        if (c == null) {
            Text(stringResource(Res.string.interests_pick), color = scheme.onSurfaceVariant)
            return@AdminCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.interests_edit_title, c.destination.replaceFirstChar { it.uppercase() }, format.monthName(c.month)) + " (${c.language.uppercase()})",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (c.aiCreatedAt != null && c.aiExpiresAt != null) stringResource(Res.string.interests_meta_ai, format.date(c.aiCreatedAt!!), format.date(c.aiExpiresAt!!))
                    else stringResource(Res.string.interests_meta_none),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
                if (c.curatedAt != null) {
                    Text(stringResource(Res.string.interests_meta_curated, format.dateTime(c.curatedAt!!), c.curatedByEmail ?: "?"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.chips.forEach { chip -> ChipRow(chip, presenter) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.newChip, onValueChange = presenter::typeChip, singleLine = true,
                    placeholder = { Text(stringResource(Res.string.interests_add_hint)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = presenter::addChip, enabled = state.newChip.isNotBlank(), modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.interests_add))
                }
            }
            if (state.tooMany) ErrorLine(stringResource(Res.string.interests_too_many, MAX_INTEREST_CHIPS, MAX_INTEREST_LABEL))
            state.actionError?.let { ErrorLine(failureText(it)) }
            if (state.saved) Text(stringResource(Res.string.interests_saved), color = scheme.primary)
            HorizontalDivider(color = scheme.outlineVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = presenter::regenerate, enabled = !state.busy, modifier = Modifier.heightIn(min = 44.dp)) {
                    Symbol(Symbols.refresh, size = 20.dp)
                    Text(stringResource(Res.string.interests_regenerate), Modifier.padding(start = 8.dp))
                }
                Button(onClick = presenter::save, enabled = !state.busy && !state.tooMany && (state.changed || c.curatedAt == null), modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.interests_save))
                }
            }
            Text(stringResource(Res.string.interests_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChipRow(chip: InterestChipDto, presenter: ContentInterestsPresenter) {
    val scheme = MaterialTheme.colorScheme
    val accents = IrisTheme.accents
    val (symbol, tint, note) = when (chip.kind) {
        InterestChipKind.PINNED -> Triple(Symbols.star, accents.green, Res.string.interests_kind_pinned)
        InterestChipKind.AI -> Triple(Symbols.autoAwesome, accents.purple, Res.string.interests_kind_ai)
        InterestChipKind.MANUAL -> Triple(Symbols.edit, accents.blue, Res.string.interests_kind_manual)
        InterestChipKind.HIDDEN -> Triple(Symbols.visibilityOff, scheme.error, Res.string.interests_kind_hidden)
    }
    val hidden = chip.kind == InterestChipKind.HIDDEN
    Row(
        Modifier.fillMaxWidth()
            .then(
                if (hidden) Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                else Modifier.background(if (chip.kind == InterestChipKind.PINNED) scheme.primaryContainer.copy(alpha = 0.4f) else scheme.surfaceContainerLow, RoundedCornerShape(12.dp)),
            )
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Symbol(symbol, size = 20.dp, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(
                chip.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                color = if (hidden) scheme.outline else scheme.onSurface,
            )
            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        when (chip.kind) {
            InterestChipKind.AI -> {
                IconButton(onClick = { presenter.setKind(chip.label, InterestChipKind.PINNED) }) {
                    Symbol(Symbols.star, size = 20.dp, contentDescription = stringResource(Res.string.interests_pin, chip.label))
                }
                IconButton(onClick = { presenter.setKind(chip.label, InterestChipKind.HIDDEN) }) {
                    Symbol(Symbols.visibilityOff, size = 20.dp, contentDescription = stringResource(Res.string.interests_hide, chip.label))
                }
            }
            InterestChipKind.PINNED -> {
                IconButton(onClick = { presenter.setKind(chip.label, InterestChipKind.AI) }) {
                    Symbol(Symbols.star, size = 20.dp, filled = true, tint = accents.green, contentDescription = stringResource(Res.string.interests_unpin, chip.label))
                }
                IconButton(onClick = { presenter.setKind(chip.label, InterestChipKind.HIDDEN) }) {
                    Symbol(Symbols.visibilityOff, size = 20.dp, contentDescription = stringResource(Res.string.interests_hide, chip.label))
                }
            }
            InterestChipKind.MANUAL -> IconButton(onClick = { presenter.remove(chip.label) }) {
                Symbol(Symbols.delete, size = 20.dp, contentDescription = stringResource(Res.string.interests_remove, chip.label))
            }
            InterestChipKind.HIDDEN -> IconButton(onClick = { presenter.setKind(chip.label, InterestChipKind.AI) }) {
                Symbol(Symbols.visibility, size = 20.dp, contentDescription = stringResource(Res.string.interests_unhide, chip.label))
            }
        }
    }
}
