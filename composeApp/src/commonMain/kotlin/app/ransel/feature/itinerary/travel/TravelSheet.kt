package app.ransel.feature.itinerary.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.ui.components.UndoSnackbar
import kotlinx.coroutines.launch
import ransel.composeapp.generated.resources.travel_main_changed
import ransel.composeapp.generated.resources.travel_undo
import app.ransel.shared.domain.LegKind
import app.ransel.shared.domain.MapsPoint
import app.ransel.shared.domain.mapsDirectionsUrl
import app.ransel.shared.domain.mapsMode
import app.ransel.shared.domain.travelMinutesFor
import app.ransel.shared.model.MainTravelMode
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.SelectedChoice
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.travel_auto_badge
import ransel.composeapp.generated.resources.travel_auto_hint
import ransel.composeapp.generated.resources.travel_estimate_note
import ransel.composeapp.generated.resources.travel_far_note
import ransel.composeapp.generated.resources.travel_far_warning_note
import ransel.composeapp.generated.resources.travel_main_car
import ransel.composeapp.generated.resources.travel_main_mode
import ransel.composeapp.generated.resources.travel_main_mode_change
import ransel.composeapp.generated.resources.travel_main_motorcycle
import ransel.composeapp.generated.resources.travel_open_maps
import ransel.composeapp.generated.resources.travel_see_in_maps
import ransel.composeapp.generated.resources.travel_sheet_overlap
import ransel.composeapp.generated.resources.travel_sheet_subtitle
import ransel.composeapp.generated.resources.travel_sheet_title
import ransel.composeapp.generated.resources.travel_sheet_warning
import ransel.composeapp.generated.resources.travel_transit_note_body
import ransel.composeapp.generated.resources.travel_transit_note_title

/**
 * The sheet that opens from a capsule (spec "Perjalanan antar tempat V1", section 2): the modes to look at, the trip's main
 * mode (changeable by an owner or editor) and "Buka rute di Google Maps". Choosing a mode here only changes the Maps link:
 * nothing is saved and the capsule stays as it was. Selected cards are purple like the interview's ([SelectedChoice], owner,
 * 16 Nov 2026).
 */
@Composable
fun TravelSheet(
    model: TravelCapsuleModel,
    mainMode: MainTravelMode,
    canChangeMainMode: Boolean,
    onChangeMainMode: (MainTravelMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val leg = model.leg
    val far = leg.kind == LegKind.FAR
    val auto = leg.kind
    var chosen by rememberSaveable(model.from.id, model.to.id) { mutableStateOf(auto) }
    var changing by rememberSaveable { mutableStateOf(false) }
    // "Moda utama diubah ke Mobil." + "Urungkan" shows inside the sheet, which stays open
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun changeMainMode(mode: MainTravelMode) {
        val previous = mainMode
        onChangeMainMode(mode)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = getString(Res.string.travel_main_changed, getString(mainModeStringRes(mode))),
                actionLabel = getString(Res.string.travel_undo),
            )
            // back to what the capsules used (an explicit value: a stored mode cannot be cleared in V1)
            if (result == SnackbarResult.ActionPerformed) onChangeMainMode(previous)
        }
    }
    val uriHandler = LocalUriHandler.current
    val colors = IrisTheme.warning

    AdaptiveSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.travel_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(Res.string.travel_sheet_subtitle, model.from.title, model.to.title, travelDistanceText(leg.roadKm)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            model.warning?.let { warning ->
                val needed = travelMinutesText(warning.needMinutes)
                val sentence = if (warning.overlaps) {
                    stringResource(Res.string.travel_sheet_overlap, needed)
                } else {
                    stringResource(Res.string.travel_sheet_warning, needed, travelGapText(warning.gapMinutes))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(IrisShape.lg)
                        .background(colors.warningContainer)
                        .border(BorderStroke(1.5.dp, colors.warning), IrisShape.lg)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(Symbols.warning, size = 16.dp, tint = colors.onWarningContainer)
                    Text(
                        sentence,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onWarningContainer,
                    )
                }
            }

            if (far) {
                InfoCard(
                    title = stringResource(Res.string.travel_far_note),
                    body = leg.warningMinutes?.takeIf { model.warning != null }
                        ?.let { stringResource(Res.string.travel_far_warning_note, travelMinutesText(it)) },
                )
            } else {
                val modes = listOf(LegKind.WALK, LegKind.MOTORCYCLE, LegKind.CAR, LegKind.PUBLIC_TRANSPORT)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { kind ->
                                ModeCard(
                                    kind = kind,
                                    roadKm = leg.roadKm,
                                    selected = chosen == kind,
                                    isAuto = auto == kind,
                                    onClick = { chosen = kind },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(Res.string.travel_auto_hint, mainModeLabel(mainMode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MainModeRow(
                    mainMode = mainMode,
                    canChange = canChangeMainMode,
                    changing = changing,
                    onToggle = { changing = !changing },
                    onPick = {
                        changing = false
                        if (it != mainMode) changeMainMode(it)
                    },
                )
                if (chosen == LegKind.PUBLIC_TRANSPORT) {
                    InfoCard(
                        title = stringResource(Res.string.travel_transit_note_title),
                        body = stringResource(Res.string.travel_transit_note_body),
                    )
                }
            }

            val mapsMode = if (far) null else chosen.mapsMode(mainMode)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button) {
                        val url = mapsDirectionsUrl(
                            listOf(MapsPoint(model.from.lat!!, model.from.lng!!), MapsPoint(model.to.lat!!, model.to.lng!!)),
                            mapsMode,
                        )
                        if (url != null) runCatching { uriHandler.openUri(url) }
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Symbol(Symbols.map, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
                Text(
                    stringResource(Res.string.travel_open_maps),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Symbol(Symbols.openInNew, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (!far) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(Res.string.travel_estimate_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            snackbar = { data -> UndoSnackbar(data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() }) },
        )
    }
}

/** The neutral info card ("Waktu transportasi umum tidak diperkirakan di sini.", the long-distance note). */
@Composable
private fun InfoCard(title: String, body: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(Symbols.info, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One of the four mode cards: icon, name, the time it would take (or "Lihat di Maps" for public transport), "Otomatis" on the picked one. */
@Composable
private fun ModeCard(
    kind: LegKind,
    roadKm: Double,
    selected: Boolean,
    isAuto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = legModeLabel(kind)
    val minutes = travelMinutesFor(roadKm, kind)
    val caption = minutes?.let { "±" + travelMinutesText(it) } ?: stringResource(Res.string.travel_see_in_maps)
    val auto = stringResource(Res.string.travel_auto_badge)
    val description = listOfNotNull(name, caption, auto.takeIf { isAuto }).joinToString(", ")
    Box(
        modifier
            .heightIn(min = 72.dp)
            .clip(IrisShape.lg)
            .background(if (selected) SelectedChoice.fill else MaterialTheme.colorScheme.surface)
            .border(
                if (selected) BorderStroke(SelectedChoice.BorderWidth, SelectedChoice.border)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                IrisShape.lg,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                legSymbol(kind)?.let { Symbol(it, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface) }
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isAuto) {
            Text(
                auto,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** "Moda utama trip: Motor · Ubah", and the two small cards (Motor / Mobil) when "Ubah" is open. Viewers see the text only. */
@Composable
private fun MainModeRow(
    mainMode: MainTravelMode,
    canChange: Boolean,
    changing: Boolean,
    onToggle: () -> Unit,
    onPick: (MainTravelMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.travel_main_mode, mainModeLabel(mainMode)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (canChange) {
                Text(
                    stringResource(Res.string.travel_main_mode_change),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onToggle),
                )
            }
        }
        if (canChange && changing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    Triple(MainTravelMode.MOTORCYCLE, Symbols.twoWheeler, Res.string.travel_main_motorcycle),
                    Triple(MainTravelMode.CAR, Symbols.directionsCar, Res.string.travel_main_car),
                ).forEach { (mode, symbol, label) ->
                    val selected = mode == mainMode
                    val name = stringResource(label)
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(IrisShape.lg)
                            .background(if (selected) SelectedChoice.fill else MaterialTheme.colorScheme.surface)
                            .border(
                                if (selected) BorderStroke(SelectedChoice.BorderWidth, SelectedChoice.border)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                IrisShape.lg,
                            )
                            .selectable(selected = selected, role = Role.RadioButton) { onPick(mode) }
                            .clearAndSetSemantics {
                                contentDescription = name
                                role = Role.RadioButton
                                this.selected = selected
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Symbol(symbol, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
