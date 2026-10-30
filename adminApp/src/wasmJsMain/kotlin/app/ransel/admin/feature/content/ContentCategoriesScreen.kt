package app.ransel.admin.feature.content

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.admin.generated.resources.Res
import app.ransel.admin.generated.resources.categories_col_code
import app.ransel.admin.generated.resources.categories_col_color
import app.ransel.admin.generated.resources.categories_col_icon
import app.ransel.admin.generated.resources.categories_col_label_en
import app.ransel.admin.generated.resources.categories_col_label_id
import app.ransel.admin.generated.resources.categories_col_shown
import app.ransel.admin.generated.resources.categories_confirm_body
import app.ransel.admin.generated.resources.categories_confirm_title
import app.ransel.admin.generated.resources.categories_down
import app.ransel.admin.generated.resources.categories_note
import app.ransel.admin.generated.resources.categories_save
import app.ransel.admin.generated.resources.categories_saved
import app.ransel.admin.generated.resources.categories_subtitle
import app.ransel.admin.generated.resources.categories_tab_expense
import app.ransel.admin.generated.resources.categories_tab_itinerary
import app.ransel.admin.generated.resources.categories_tab_packing
import app.ransel.admin.generated.resources.categories_title
import app.ransel.admin.generated.resources.categories_up
import app.ransel.admin.generated.resources.color_blue
import app.ransel.admin.generated.resources.color_brown
import app.ransel.admin.generated.resources.color_green
import app.ransel.admin.generated.resources.color_magenta
import app.ransel.admin.generated.resources.color_orange
import app.ransel.admin.generated.resources.color_purple
import app.ransel.admin.generated.resources.color_red
import app.ransel.admin.generated.resources.color_teal
import app.ransel.admin.generated.resources.color_yellow
import app.ransel.admin.ui.AdminCard
import app.ransel.admin.ui.ConfirmDialog
import app.ransel.admin.ui.ErrorPanel
import app.ransel.admin.ui.LoadingPanel
import app.ransel.admin.ui.PageHeader
import app.ransel.admin.ui.failureText
import app.ransel.admin.ui.rememberPresenter
import app.ransel.shared.domain.MAX_CATEGORY_LABEL
import app.ransel.shared.dto.AdminContentCategoriesDto
import app.ransel.shared.dto.AdminContentCategoryDto
import app.ransel.shared.dto.UpdateContentCategoriesRequest
import app.ransel.shared.model.CategoryKind
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

data class ContentCategoriesUiState(
    val loading: Boolean = true,
    val data: AdminContentCategoriesDto? = null,
    val error: ApiFailure? = null,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    /** The rows of [kind] as edited, in order. */
    val rows: List<AdminContentCategoryDto> = emptyList(),
    val confirming: Boolean = false,
    val saving: Boolean = false,
    val saveError: ApiFailure? = null,
    val savedVersion: Int? = null,
) {
    val changed: Boolean get() = rows != data?.categories.orEmpty().filter { it.category.kind == kind }
}

/** "Kategori" (F9.6, design `KontenKategori`): saved and published at once ("Simpan dan terbitkan"). */
class ContentCategoriesPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(ContentCategoriesUiState())
    val state: StateFlow<ContentCategoriesUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = api.contentCategories()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = r.value, rows = r.value.of(it.kind)) }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
        }
    }

    fun tab(kind: CategoryKind) = _state.update { it.copy(kind = kind, rows = it.data?.of(kind).orEmpty(), savedVersion = null) }

    fun edit(constant: String, change: (AdminContentCategoryDto) -> AdminContentCategoryDto) =
        _state.update { s -> s.copy(rows = s.rows.map { if (it.category.constant == constant) change(it) else it }, savedVersion = null) }

    /** Up or down one place ([by] = −1 / +1); the design's drag handle, made into buttons. */
    fun move(constant: String, by: Int) = _state.update { s ->
        val i = s.rows.indexOfFirst { it.category.constant == constant }
        val j = i + by
        if (i < 0 || j !in s.rows.indices) return@update s
        val list = s.rows.toMutableList().apply { add(j, removeAt(i)) }
        s.copy(rows = list, savedVersion = null)
    }

    fun askToSave() = _state.update { if (it.changed) it.copy(confirming = true, saveError = null) else it }
    fun dismiss() = _state.update { if (it.saving) it else it.copy(confirming = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val body = UpdateContentCategoriesRequest(s.kind, s.rows.mapIndexed { i, r -> r.category.copy(sortOrder = i) })
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            when (val r = api.saveContentCategories(body)) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, confirming = false, data = r.value, rows = r.value.of(it.kind), savedVersion = r.value.version)
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(saving = false, saveError = r.error) }
                }
            }
        }
    }

    private fun AdminContentCategoriesDto.of(kind: CategoryKind) = categories.filter { it.category.kind == kind }
}

/** Material Symbols names the categories may use, to the glyphs of the bundled subset. */
private val ICON_GLYPHS = mapOf(
    "attractions" to Symbols.attractions, "local_activity" to Symbols.localActivity, "restaurant" to Symbols.restaurant,
    "directions_car" to Symbols.directionsCar, "directions_boat" to Symbols.directionsBoat, "flight" to Symbols.flight,
    "hotel" to Symbols.hotel, "shopping_bag" to Symbols.shoppingBag, "event" to Symbols.event, "hiking" to Symbols.hiking,
    "beach_access" to Symbols.beachAccess, "forest" to Symbols.forest, "landscape" to Symbols.landscape,
    "temple_hindu" to Symbols.templeHindu, "theater_comedy" to Symbols.theaterComedy, "location_city" to Symbols.locationCity,
    "apparel" to Symbols.apparel, "badge" to Symbols.badge, "power" to Symbols.power, "soap" to Symbols.soap,
    "medical_services" to Symbols.medicalServices, "category" to Symbols.category, "luggage" to Symbols.luggage,
    "payments" to Symbols.payments, "more_horiz" to Symbols.moreHoriz,
)

@Composable
private fun accent(name: String): Color {
    val a = IrisTheme.accents
    return when (name) {
        "blue" -> a.blue
        "orange" -> a.orange
        "teal" -> a.teal
        "purple" -> a.purple
        "yellow" -> a.yellow
        "brown" -> a.brown
        "red" -> a.red
        "green" -> a.green
        else -> a.magenta
    }
}

@Composable
private fun colorName(name: String): String = stringResource(
    when (name) {
        "blue" -> Res.string.color_blue
        "orange" -> Res.string.color_orange
        "teal" -> Res.string.color_teal
        "purple" -> Res.string.color_purple
        "yellow" -> Res.string.color_yellow
        "brown" -> Res.string.color_brown
        "red" -> Res.string.color_red
        "green" -> Res.string.color_green
        else -> Res.string.color_magenta
    },
)

@Composable
fun ContentCategoriesScreen(api: AdminApi, onAccessLost: suspend (ApiFailure) -> Unit) {
    val presenter = rememberPresenter { ContentCategoriesPresenter(api, it, onAccessLost) }
    val state by presenter.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val kinds = listOf(
        CategoryKind.EXPENSE to stringResource(Res.string.categories_tab_expense),
        CategoryKind.ITINERARY to stringResource(Res.string.categories_tab_itinerary),
        CategoryKind.PACKING to stringResource(Res.string.categories_tab_packing),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeader(stringResource(Res.string.categories_title), stringResource(Res.string.categories_subtitle)) {
            Button(onClick = presenter::askToSave, enabled = state.changed && !state.saving, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(Res.string.categories_save))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(IrisTheme.accents.blue.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.info, size = 22.dp, tint = scheme.onSurface)
            Text(stringResource(Res.string.categories_note), style = MaterialTheme.typography.bodyMedium)
        }
        state.savedVersion?.let { Text(stringResource(Res.string.categories_saved, it), color = scheme.primary) }
        val data = state.data
        when {
            data == null && state.error != null -> AdminCard { ErrorPanel(state.error!!, presenter::reload) }
            data == null -> LoadingPanel()
            else -> AdminCard(padding = 0.dp) {
                Column {
                    PrimaryTabRow(selectedTabIndex = kinds.indexOfFirst { it.first == state.kind }) {
                        kinds.forEach { (kind, label) -> Tab(selected = state.kind == kind, onClick = { presenter.tab(kind) }, text = { Text(label) }) }
                    }
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(
                                96.dp to "", 120.dp to stringResource(Res.string.categories_col_code), 180.dp to stringResource(Res.string.categories_col_label_id),
                                180.dp to stringResource(Res.string.categories_col_label_en), 200.dp to stringResource(Res.string.categories_col_icon),
                                170.dp to stringResource(Res.string.categories_col_color), 80.dp to stringResource(Res.string.categories_col_shown),
                            ).forEach { (w, h) -> Text(h, Modifier.width(w), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
                        }
                        state.rows.forEachIndexed { i, row ->
                            HorizontalDivider(color = scheme.outlineVariant)
                            CategoryRow(row, first = i == 0, last = i == state.rows.lastIndex, icons = data.icons, colors = data.colors, presenter = presenter)
                        }
                    }
                }
            }
        }
    }
    if (state.confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.categories_confirm_title, kinds.first { it.first == state.kind }.second),
            body = stringResource(Res.string.categories_confirm_body),
            confirmLabel = stringResource(Res.string.categories_save),
            destructive = false,
            busy = state.saving,
            error = state.saveError?.let { failureText(it) },
            onConfirm = presenter::save,
            onDismiss = presenter::dismiss,
        )
    }
}

@Composable
private fun CategoryRow(row: AdminContentCategoryDto, first: Boolean, last: Boolean, icons: List<String>, colors: List<String>, presenter: ContentCategoriesPresenter) {
    val scheme = MaterialTheme.colorScheme
    val c = row.category
    val icon = c.icon ?: row.defaultIcon
    val color = c.color ?: row.defaultColor
    val label = c.labelId ?: row.defaultLabelId
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(96.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                IconButton(onClick = { presenter.move(c.constant, -1) }, enabled = !first, modifier = Modifier.size(32.dp)) {
                    // the subset has no expand_less: chevrons turned to point up and down
                    Symbol(Symbols.chevronLeft, size = 18.dp, contentDescription = stringResource(Res.string.categories_up, label), modifier = Modifier.rotate(90f))
                }
                IconButton(onClick = { presenter.move(c.constant, 1) }, enabled = !last, modifier = Modifier.size(32.dp)) {
                    Symbol(Symbols.chevronRight, size = 18.dp, contentDescription = stringResource(Res.string.categories_down, label), modifier = Modifier.rotate(90f))
                }
            }
            Box(Modifier.size(40.dp).background(accent(color).copy(alpha = 0.16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Symbol(ICON_GLYPHS[icon] ?: Symbols.category, size = 22.dp, tint = accent(color))
            }
        }
        Text(c.constant, Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        OutlinedTextField(
            value = c.labelId.orEmpty(), onValueChange = { t -> presenter.edit(c.constant) { it.copy(category = it.category.copy(labelId = t.take(MAX_CATEGORY_LABEL).ifEmpty { null })) } },
            placeholder = { Text(row.defaultLabelId) }, singleLine = true, modifier = Modifier.width(180.dp),
        )
        OutlinedTextField(
            value = c.labelEn.orEmpty(), onValueChange = { t -> presenter.edit(c.constant) { it.copy(category = it.category.copy(labelEn = t.take(MAX_CATEGORY_LABEL).ifEmpty { null })) } },
            placeholder = { Text(row.defaultLabelEn) }, singleLine = true, modifier = Modifier.width(180.dp),
        )
        Choice(icon, icons, Modifier.width(200.dp), shown = { it }, leading = { Symbol(ICON_GLYPHS[it] ?: Symbols.category, size = 18.dp) }) { v ->
            presenter.edit(c.constant) { it.copy(category = it.category.copy(icon = v.takeIf { x -> x != row.defaultIcon })) }
        }
        Choice(color, colors, Modifier.width(170.dp), shown = { colorName(it) }, leading = { Box(Modifier.size(18.dp).background(accent(it), RoundedCornerShape(6.dp))) }) { v ->
            presenter.edit(c.constant) { it.copy(category = it.category.copy(color = v.takeIf { x -> x != row.defaultColor })) }
        }
        Box(Modifier.width(80.dp)) {
            Switch(checked = !c.hidden, onCheckedChange = { on -> presenter.edit(c.constant) { it.copy(category = it.category.copy(hidden = !on)) } })
        }
    }
}

@Composable
private fun Choice(
    value: String,
    options: List<String>,
    modifier: Modifier,
    shown: @Composable (String) -> String,
    leading: @Composable (String) -> Unit,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = shown(value), onValueChange = {}, readOnly = true, singleLine = true,
            leadingIcon = { leading(value) },
            trailingIcon = { IconButton(onClick = { open = true }) { Symbol(Symbols.expandMore, contentDescription = shown(value)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { v ->
                DropdownMenuItem(text = { Text(shown(v)) }, leadingIcon = { leading(v) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}
