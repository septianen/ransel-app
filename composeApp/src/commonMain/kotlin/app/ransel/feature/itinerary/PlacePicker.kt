package app.ransel.feature.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.stringResource
import app.ransel.feature.destination.LocationState
import app.ransel.platform.rememberLocationProvider
import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.PlaceCategory
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.ui.adaptive.rememberWindowWidth
import app.ransel.ui.components.SkeletonList
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.newId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.activity_day_unscheduled
import ransel.composeapp.generated.resources.destination_around
import ransel.composeapp.generated.resources.destination_attribution
import ransel.composeapp.generated.resources.destination_clear
import ransel.composeapp.generated.resources.destination_denied_settings
import ransel.composeapp.generated.resources.destination_location_failed
import ransel.composeapp.generated.resources.destination_locating
import ransel.composeapp.generated.resources.itinerary_day_chip
import ransel.composeapp.generated.resources.place_category_attraction
import ransel.composeapp.generated.resources.place_category_food
import ransel.composeapp.generated.resources.place_category_lodging
import ransel.composeapp.generated.resources.place_category_other
import ransel.composeapp.generated.resources.place_category_shopping
import ransel.composeapp.generated.resources.place_category_transport
import ransel.composeapp.generated.resources.place_picker_denied_body
import ransel.composeapp.generated.resources.place_picker_denied_title
import ransel.composeapp.generated.resources.place_picker_hint
import ransel.composeapp.generated.resources.place_picker_location
import ransel.composeapp.generated.resources.place_picker_location_hint
import ransel.composeapp.generated.resources.place_picker_no_results_body
import ransel.composeapp.generated.resources.place_picker_no_results_title
import ransel.composeapp.generated.resources.place_picker_offline_body
import ransel.composeapp.generated.resources.place_picker_offline_title
import ransel.composeapp.generated.resources.place_picker_unavailable_body
import ransel.composeapp.generated.resources.place_picker_unavailable_title
import ransel.composeapp.generated.resources.place_picker_use_text
import ransel.composeapp.generated.resources.place_picker_use_text_note
import ransel.composeapp.generated.resources.place_picker_used

/**
 * On windows at least this wide (the activity form is a 720 dp column from here) the picker is a centred card; below,
 * it covers the screen like the destination picker. Chosen by the frontend agent, 12 Nov 2026.
 */
private val CardFromWidth: Dp = 720.dp
private val CardWidth: Dp = 560.dp
private val CardMaxHeight: Dp = 640.dp

/**
 * The place picker opened by the "Tempat" field. [onPicked] gets a place with or without a point (free text);
 * Guests use it like signed-in users. Every opening is a fresh search session.
 */
@Composable
fun PlacePicker(
    tripId: String,
    initialQuery: String,
    onPicked: (PlaceChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val key = remember { newId() }
    val viewModel: PlacePickerViewModel = koinViewModel(key = "place-picker-$key") { parametersOf(tripId) }
    val state by viewModel.state.collectAsState()
    val location = rememberLocationProvider()
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewModel) {
        if (initialQuery.isNotBlank() && viewModel.state.value.query.isEmpty()) viewModel.onQuery(initialQuery)
        viewModel.picked.collect { onPicked(it) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val around = stringResource(Res.string.destination_around, "%s")
    val card = rememberWindowWidth() >= CardFromWidth

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val content = @Composable {
            PlacePickerContent(
                state = state,
                card = card,
                focus = focus,
                onBack = onDismiss,
                onQuery = viewModel::onQuery,
                onUsed = viewModel::pickUsed,
                onSuggestion = viewModel::pickSuggestion,
                onUseTyped = viewModel::useTyped,
                onUseLocation = { viewModel.useLocation(around, location.request) },
                onOpenSettings = location.openSettings,
                onMoveHighlight = viewModel::moveHighlight,
                onEnter = viewModel::enter,
            )
        }
        if (card) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.width(CardWidth).fillMaxHeight(0.85f).heightIn(max = CardMaxHeight),
                    shape = IrisShape.xl,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) { content() }
            }
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
        }
    }
}

@Composable
fun PlacePickerContent(
    state: PlacePickerUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onUsed: (UsedPlace) -> Unit,
    onSuggestion: (PlaceSuggestionDto) -> Unit,
    onUseTyped: () -> Unit,
    onUseLocation: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    card: Boolean = false,
    focus: FocusRequester = remember { FocusRequester() },
    onMoveHighlight: (Int) -> Unit = {},
    onEnter: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val rows = state.rows
    Column(modifier.fillMaxSize().let { if (card) it else it.statusBarsPadding() }) {
        // header: back + search field (spec: 64 dp, field 48 dp radius 12, 2 dp primary border while focused)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(IrisShape.md)
                    .border(2.dp, scheme.primary, IrisShape.md)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.search, size = 22.dp, tint = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text(stringResource(Res.string.place_picker_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        // Enter (also on the web): the highlighted row, else the text. Nothing is picked on its own.
                        keyboardActions = KeyboardActions(onDone = { onEnter() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> { onMoveHighlight(1); true }
                                    Key.DirectionUp -> { onMoveHighlight(-1); true }
                                    Key.Escape -> { onBack(); true }
                                    else -> false
                                }
                            },
                    )
                }
                if (state.query.isNotEmpty()) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(scheme.surfaceContainer).clickable(role = Role.Button) { onQuery("") }, contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, size = 18.dp, contentDescription = stringResource(Res.string.destination_clear))
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        if (state.resolving) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (state.mode) {
                PlaceSearchMode.OFFLINE -> InfoBanner(
                    Symbols.wifiOff,
                    stringResource(Res.string.place_picker_offline_title),
                    stringResource(Res.string.place_picker_offline_body),
                )
                PlaceSearchMode.UNAVAILABLE -> InfoBanner(
                    Symbols.warning,
                    stringResource(Res.string.place_picker_unavailable_title),
                    stringResource(Res.string.place_picker_unavailable_body),
                )
                PlaceSearchMode.READY -> {}
            }

            // "Gunakan lokasi saat ini" only before typing
            if (!state.typing && state.query.isEmpty()) LocationRow(state.location, onUseLocation, onOpenSettings)

            if (state.used.isNotEmpty()) {
                SectionTitle(Res.string.place_picker_used)
                state.used.forEachIndexed { index, used ->
                    PickerRow(
                        symbol = Symbols.locationOn,
                        title = highlighted(used.choice.name, emptyList()),
                        subtitle = used.day?.let { stringResource(Res.string.itinerary_day_chip, it) } ?: stringResource(Res.string.activity_day_unscheduled),
                        highlighted = state.highlight == index,
                        onClick = { onUsed(used) },
                    )
                }
            }

            if (state.typing) {
                if (state.searching) SkeletonList(rows = 3, padding = 16.dp)
                state.googleRows.forEachIndexed { index, s ->
                    PickerRow(
                        symbol = categorySymbol(s.category),
                        title = highlighted(s.name, s.matchRanges),
                        subtitle = suggestionSubtitle(s),
                        highlighted = state.highlight == state.used.size + index,
                        onClick = { onSuggestion(s) },
                    )
                }
                if (state.searched && !state.searching && state.mode == PlaceSearchMode.READY && state.googleRows.isEmpty() && state.used.isEmpty()) {
                    NoResults(state.query.trim())
                }
            }
        }

        if (state.query.isNotBlank()) {
            val noResults = state.typing && state.searched && !state.searching && state.mode == PlaceSearchMode.READY &&
                state.googleRows.isEmpty() && state.used.isEmpty()
            HorizontalDivider(color = scheme.outlineVariant)
            if (state.mode == PlaceSearchMode.READY && state.typing && state.googleRows.isNotEmpty()) {
                Text(
                    stringResource(Res.string.destination_attribution),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            PickerRow(
                symbol = Symbols.editNote,
                title = AnnotatedString(stringResource(Res.string.place_picker_use_text, state.query.trim())),
                subtitle = stringResource(Res.string.place_picker_use_text_note),
                accent = noResults,
                onClick = onUseTyped,
                bold = true,
            )
        }
    }
}

@Composable
private fun LocationRow(location: LocationState, onUseLocation: () -> Unit, onOpenSettings: (() -> Unit)?) {
    when (location) {
        LocationState.Denied -> InfoBanner(
            Symbols.warning,
            stringResource(Res.string.place_picker_denied_title),
            stringResource(Res.string.place_picker_denied_body),
            action = onOpenSettings?.let { stringResource(Res.string.destination_denied_settings) to it },
        )
        LocationState.Locating -> Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(Res.string.destination_locating), style = MaterialTheme.typography.bodyLarge)
        }
        else -> PickerRow(
            symbol = Symbols.explore,
            title = AnnotatedString(stringResource(Res.string.place_picker_location)),
            subtitle = if (location is LocationState.Failed) stringResource(Res.string.destination_location_failed) else stringResource(Res.string.place_picker_location_hint),
            accent = true,
            bold = true,
            onClick = onUseLocation,
        )
    }
}

@Composable
private fun NoResults(query: String) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Symbol(Symbols.search, size = 28.dp, tint = scheme.onSurfaceVariant)
        }
        Text(
            stringResource(Res.string.place_picker_no_results_title, query),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            stringResource(Res.string.place_picker_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun InfoBanner(symbol: String, title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(IrisShape.lg)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(symbol, size = 22.dp, tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (action != null) {
                OutlinedButton(onClick = action.second, modifier = Modifier.heightIn(min = 48.dp)) { Text(action.first) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: StringResource) = Text(
    stringResource(text),
    Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A 64 dp row: a 40 dp tile, the title and a grey subtitle. [accent] = primary-container tile; [highlighted] = the arrow-key row. */
@Composable
private fun PickerRow(
    symbol: String,
    title: AnnotatedString,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    highlighted: Boolean = false,
    bold: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (highlighted) scheme.primaryContainer else scheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(if (accent) scheme.primaryContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, size = 22.dp, tint = if (accent) scheme.onPrimaryContainer else scheme.onSurfaceVariant) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

private fun highlighted(text: String, ranges: List<MatchRange>) = buildAnnotatedString {
    var at = 0
    ranges.sortedBy { it.start }.forEach { r ->
        val start = r.start.coerceIn(at, text.length)
        val end = r.end.coerceIn(start, text.length)
        append(text.substring(at, start))
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text.substring(start, end)) }
        at = end
    }
    append(text.substring(at))
}

/** Icon of the place type. The bundled symbol subset has no transit icon yet, so Transportasi uses the car. */
private fun categorySymbol(category: PlaceCategory?) = when (category) {
    PlaceCategory.FOOD -> Symbols.restaurant
    PlaceCategory.LODGING -> Symbols.hotel
    PlaceCategory.SHOPPING -> Symbols.shoppingBag
    PlaceCategory.TRANSPORT -> Symbols.directionsCar
    PlaceCategory.ATTRACTION -> Symbols.landscape
    PlaceCategory.OTHER, null -> Symbols.locationOn
}

@Composable
private fun suggestionSubtitle(s: PlaceSuggestionDto): String? {
    val label = s.category?.let { c ->
        stringResource(
            when (c) {
                PlaceCategory.FOOD -> Res.string.place_category_food
                PlaceCategory.LODGING -> Res.string.place_category_lodging
                PlaceCategory.SHOPPING -> Res.string.place_category_shopping
                PlaceCategory.TRANSPORT -> Res.string.place_category_transport
                PlaceCategory.ATTRACTION -> Res.string.place_category_attraction
                PlaceCategory.OTHER -> Res.string.place_category_other
            },
        )
    }
    return listOfNotNull(s.secondary, label).joinToString(" · ").ifEmpty { null }
}

private val previewSuggestions = listOf(
    PlaceSuggestionDto("p1", "Warung Bajo", "Labuan Bajo", PlaceKind.AREA, listOf(MatchRange(0, 3)), PlaceCategory.FOOD),
    PlaceSuggestionDto("p2", "Hotel Bajo Beach", "Labuan Bajo, Manggarai Barat", PlaceKind.AREA, listOf(MatchRange(6, 9)), PlaceCategory.LODGING),
    PlaceSuggestionDto("p3", "Bandara Komodo", "Labuan Bajo", PlaceKind.AREA, emptyList(), PlaceCategory.TRANSPORT),
)

private val previewUsed = listOf(
    UsedPlace(PlaceChoice("Pantai Kuta", null, "pk", -8.7, 115.17), 1),
    UsedPlace(PlaceChoice("Rumah Budi"), null),
)

@Preview
@Composable
private fun PlacePickerBeforeTypingPreview() = IrisPreview {
    PlacePickerContent(PlacePickerUiState(usedAll = previewUsed), {}, {}, {}, {}, {}, {}, null)
}

@Preview
@Composable
private fun PlacePickerTypingPreview() = IrisPreview {
    PlacePickerContent(
        PlacePickerUiState(query = "baj", suggestions = previewSuggestions, searched = true, usedAll = previewUsed, highlight = 1),
        {}, {}, {}, {}, {}, {}, null,
    )
}

@Preview
@Composable
private fun PlacePickerNoResultsPreview() = IrisPreview {
    PlacePickerContent(PlacePickerUiState(query = "xqzv", searched = true), {}, {}, {}, {}, {}, {}, null)
}

@Preview
@Composable
private fun PlacePickerOfflinePreview() = IrisPreview {
    PlacePickerContent(
        PlacePickerUiState(query = "pantai", searched = true, mode = PlaceSearchMode.OFFLINE, usedAll = previewUsed),
        {}, {}, {}, {}, {}, {}, null,
    )
}

