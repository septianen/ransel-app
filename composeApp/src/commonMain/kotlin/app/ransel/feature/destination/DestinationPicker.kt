package app.ransel.feature.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.platform.rememberLocationProvider
import app.ransel.shared.dto.MatchRange
import app.ransel.shared.dto.NearbyAreaDto
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.PlaceSuggestionDto
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.newId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.destination_around
import ransel.composeapp.generated.resources.destination_attribution
import ransel.composeapp.generated.resources.destination_clear
import ransel.composeapp.generated.resources.destination_denied_body
import ransel.composeapp.generated.resources.destination_denied_settings
import ransel.composeapp.generated.resources.destination_denied_title
import ransel.composeapp.generated.resources.destination_fallback
import ransel.composeapp.generated.resources.destination_here_title
import ransel.composeapp.generated.resources.destination_here_use
import ransel.composeapp.generated.resources.destination_hint
import ransel.composeapp.generated.resources.destination_kind_attraction
import ransel.composeapp.generated.resources.destination_kind_city
import ransel.composeapp.generated.resources.destination_kind_country
import ransel.composeapp.generated.resources.destination_kind_province
import ransel.composeapp.generated.resources.destination_kind_regency
import ransel.composeapp.generated.resources.destination_location
import ransel.composeapp.generated.resources.destination_location_failed
import ransel.composeapp.generated.resources.destination_location_hint
import ransel.composeapp.generated.resources.destination_location_note
import ransel.composeapp.generated.resources.destination_locating
import ransel.composeapp.generated.resources.destination_popular
import ransel.composeapp.generated.resources.destination_recent
import ransel.composeapp.generated.resources.destination_suggestions_note
import ransel.composeapp.generated.resources.destination_use_typed

/**
 * Full-screen destination picker. [onPicked] gets the chosen name and, when the server could tell, its place.
 * Every opening is a fresh search session.
 */
@Composable
fun DestinationPicker(
    onPicked: (DestinationChoice) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "",
) {
    val key = remember { newId() }
    val viewModel: DestinationPickerViewModel = koinViewModel(key = "destination-$key")
    val state by viewModel.state.collectAsState()
    val location = rememberLocationProvider()
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewModel) {
        if (initialQuery.isNotBlank() && state.query.isEmpty()) viewModel.onQuery(initialQuery)
        viewModel.picked.collect { onPicked(it) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val around = stringResource(Res.string.destination_around, "%s")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            DestinationPickerContent(
                state = state,
                focus = focus,
                onBack = onDismiss,
                onQuery = viewModel::onQuery,
                onPick = viewModel::pick,
                onUseTyped = viewModel::useTyped,
                onRecent = viewModel::pickRecent,
                onPopular = viewModel::pickPopular,
                onArea = { viewModel.pickArea(it, around) },
                onUseLocation = { viewModel.useLocation(location.request) },
                onOpenSettings = location.openSettings,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinationPickerContent(
    state: DestinationPickerUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onPick: (PlaceSuggestionDto) -> Unit,
    onUseTyped: () -> Unit,
    onRecent: (RecentDestination) -> Unit,
    onPopular: (app.ransel.shared.dto.PopularDestinationDto) -> Unit,
    onArea: (NearbyAreaDto) -> Unit,
    onUseLocation: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.search, size = 20.dp, tint = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) Text(stringResource(Res.string.destination_hint), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { onQuery("") }, contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, size = 18.dp, contentDescription = stringResource(Res.string.destination_clear))
                    }
                }
            }
        }
        if (state.resolving || state.searching) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.typing) {
                Column {
                    state.suggestions.forEach { s ->
                        PlaceRow(kindSymbol(s.kind), highlighted(s.name, s.matchRanges), secondaryWithKind(s.secondary, s.kind)) { onPick(s) }
                    }
                    PlaceRow(Symbols.editNote, highlighted(stringResource(Res.string.destination_use_typed, state.query.trim()), emptyList()), null, onClick = onUseTyped)
                }
                Note(
                    Symbols.info,
                    stringResource(if (state.fallback) Res.string.destination_fallback else Res.string.destination_suggestions_note),
                )
                if (!state.fallback && state.suggestions.isNotEmpty()) {
                    Text(stringResource(Res.string.destination_attribution), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                return@Column
            }

            if (state.online) LocationSection(state.location, onUseLocation, onArea, onOpenSettings)

            if (state.recents.isNotEmpty()) {
                Column {
                    SectionTitle(Res.string.destination_recent)
                    state.recents.forEach { r ->
                        PlaceRow(Symbols.schedule, highlighted(r.name, emptyList()), r.secondary) { onRecent(r) }
                    }
                }
            }
            if (state.popular.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(Res.string.destination_popular)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.popular.forEach { p ->
                            Text(
                                p.name,
                                Modifier
                                    .clip(CircleShape)
                                    .border(1.dp, scheme.outlineVariant, CircleShape)
                                    .clickable(role = Role.Button) { onPopular(p) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSection(
    location: LocationState,
    onUseLocation: () -> Unit,
    onArea: (NearbyAreaDto) -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    when (location) {
        LocationState.Idle, is LocationState.Failed -> Column {
            PlaceRow(
                Symbols.locationOn,
                highlighted(stringResource(Res.string.destination_location), emptyList()),
                if (location is LocationState.Failed) stringResource(Res.string.destination_location_failed) else stringResource(Res.string.destination_location_hint),
                accent = true,
                onClick = onUseLocation,
            )
        }
        LocationState.Locating -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(Res.string.destination_locating), style = MaterialTheme.typography.bodyLarge)
        }
        is LocationState.Areas -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val first = location.areas.first()
            Column(
                Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.primaryContainer)
                    .clickable(role = Role.Button) { onArea(first) }.padding(14.dp),
            ) {
                Text(stringResource(Res.string.destination_here_title), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
                Text(listOfNotNull(first.name, first.secondary).joinToString(", "), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                Text(stringResource(Res.string.destination_here_use), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            val around = stringResource(Res.string.destination_around, "%s")
            location.areas.drop(1).forEach { a ->
                PlaceRow(
                    if (a.around) Symbols.explore else Symbols.locationCity,
                    highlighted(if (a.around) around.replace("%s", a.name) else a.name, emptyList()),
                    a.secondary,
                ) { onArea(a) }
            }
            Note(Symbols.lock, stringResource(Res.string.destination_location_note))
        }
        LocationState.Denied -> Column(
            Modifier.fillMaxWidth().clip(IrisShape.lg).background(scheme.surfaceContainerLow).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Symbols.warning, size = 20.dp, tint = scheme.error)
                Text(stringResource(Res.string.destination_denied_title), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(Res.string.destination_denied_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (onOpenSettings != null) {
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(Res.string.destination_denied_settings)) }
            }
        }
    }
}

@Composable
private fun PlaceRow(symbol: String, title: androidx.compose.ui.text.AnnotatedString, secondary: String?, accent: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.md).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(IrisShape.md).background(if (accent) scheme.primaryContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, size = 22.dp, tint = if (accent) scheme.onPrimaryContainer else scheme.onSurfaceVariant) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (secondary != null) Text(secondary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: StringResource) =
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Note(symbol: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(symbol, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun highlighted(text: String, ranges: List<MatchRange>) = buildAnnotatedString {
    var at = 0
    ranges.sortedBy { it.start }.forEach { r ->
        val start = r.start.coerceIn(at, text.length)
        val end = r.end.coerceIn(start, text.length)
        append(text.substring(at, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start, end)) }
        at = end
    }
    append(text.substring(at))
}

private fun kindSymbol(kind: PlaceKind) = when (kind) {
    PlaceKind.ATTRACTION -> Symbols.landscape
    PlaceKind.COUNTRY -> Symbols.language
    PlaceKind.AREA -> Symbols.locationOn
    else -> Symbols.locationCity
}

@Composable
private fun secondaryWithKind(secondary: String?, kind: PlaceKind): String? {
    val label = when (kind) {
        PlaceKind.CITY -> Res.string.destination_kind_city
        PlaceKind.REGENCY -> Res.string.destination_kind_regency
        PlaceKind.PROVINCE -> Res.string.destination_kind_province
        PlaceKind.COUNTRY -> Res.string.destination_kind_country
        PlaceKind.ATTRACTION -> Res.string.destination_kind_attraction
        PlaceKind.AREA -> null
    }?.takeIf { DestinationPickerViewModel.showsKind(kind) }?.let { stringResource(it) }
    return listOfNotNull(secondary, label).joinToString(" · ").ifEmpty { null }
}
