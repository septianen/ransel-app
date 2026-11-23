package app.ransel.feature.itinerary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.paneTitle
import app.ransel.feature.explore.Hero
import app.ransel.feature.explore.HoursBlock
import app.ransel.feature.explore.LoadingSkeleton
import app.ransel.feature.explore.LocationBlock
import app.ransel.feature.explore.PlaceDetailInfo
import app.ransel.feature.explore.RoundIcon
import app.ransel.feature.explore.Section
import app.ransel.feature.explore.costLabel
import app.ransel.shared.domain.googleMapsPlaceUrl
import app.ransel.ui.components.ConfirmDialog
import ransel.composeapp.generated.resources.action_cancel
import ransel.composeapp.generated.resources.activity_delete_body
import ransel.composeapp.generated.resources.activity_delete_confirm
import ransel.composeapp.generated.resources.activity_delete_title
import ransel.composeapp.generated.resources.activity_detail_edit_cd
import ransel.composeapp.generated.resources.activity_detail_more_cd
import ransel.composeapp.generated.resources.itinerary_schedule
import ransel.composeapp.generated.resources.place_detail_about
import ransel.composeapp.generated.resources.place_detail_best_time
import ransel.composeapp.generated.resources.place_detail_error
import ransel.composeapp.generated.resources.place_detail_highlights
import ransel.composeapp.generated.resources.state_retry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.Category
import app.ransel.ui.category.symbol
import app.ransel.ui.category.tileColor
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.format.formatDuration
import app.ransel.ui.format.formatTime
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.action_copy
import ransel.composeapp.generated.resources.activity_day_unscheduled
import ransel.composeapp.generated.resources.activity_link_copied
import ransel.composeapp.generated.resources.activity_link_copy
import ransel.composeapp.generated.resources.activity_link_open_browser
import ransel.composeapp.generated.resources.activity_link_open_failed
import ransel.composeapp.generated.resources.activity_link_row_description
import ransel.composeapp.generated.resources.activity_links_title
import ransel.composeapp.generated.resources.activity_notes

/**
 * Activity details (plan activity-detail-unify, owner 23 Nov 2026): one screen for every itinerary card, in the style of
 * the AI place detail. Ubah opens the form ([onEdit]); ⋮ → Hapus deletes after a confirm. Closes by itself when the
 * activity is gone. Opening it charges nothing and never starts an AI call for a hand-picked place.
 */
@Composable
fun ActivityDetailScreen(
    tripId: String,
    itemId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityDetailViewModel = koinViewModel(key = "activity-detail-$tripId-$itemId") { parametersOf(tripId, itemId) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val item = state.item
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(Res.string.activity_link_copied)
    val failedText = stringResource(Res.string.activity_link_open_failed)
    val copyLabel = stringResource(Res.string.action_copy)
    var confirmDelete by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }

    fun copy(url: String) {
        clipboard.setText(AnnotatedString(url))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(copiedText)
        }
    }

    fun open(url: String) {
        // outside the app: the browser or the app that handles the address (Instagram, Maps ...)
        val opened = runCatching { uriHandler.openUri(url) }.isSuccess
        if (!opened) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(failedText, actionLabel = copyLabel) == SnackbarResult.ActionPerformed) copy(url)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (item != null) {
            ActivityDetailContent(
                item = item,
                links = state.links,
                canEdit = state.canEdit,
                place = state.place,
                onBack = onBack,
                onEdit = onEdit,
                onDelete = { confirmDelete = true },
                onSchedule = if (state.canSchedule) ({ scheduling = true }) else null,
                onRetryPlace = viewModel::retryPlace,
                onOpenMaps = { url -> open(url) },
                onOpenLink = ::open,
                onCopyLink = ::copy,
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            snackbar = { data ->
                UndoSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel,
                    onAction = { data.performAction() },
                )
            },
        )
    }

    if (confirmDelete && item != null) {
        ConfirmDialog(
            title = stringResource(Res.string.activity_delete_title, item.title),
            body = stringResource(Res.string.activity_delete_body),
            confirmLabel = stringResource(Res.string.activity_delete_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
    val trip = state.trip
    if (scheduling && item != null && trip != null && state.canSchedule) {
        // the itinerary's own schedule sheet and rules (end of the chosen day, 09:00 on an empty day)
        val itinerary: ItineraryViewModel = koinViewModel(key = "itinerary-detail-${trip.id}-${trip.startDate}-${trip.endDate}") { parametersOf(trip) }
        val itineraryState by itinerary.state.collectAsState()
        if (itineraryState.days.isNotEmpty()) {
            SchedulePlaceSheet(
                item = item,
                state = itineraryState,
                preview = { d -> itinerary.previewSchedule(item, d) },
                onSchedule = { d, start, end -> itinerary.schedule(item, d, start, end); scheduling = false },
                onDelete = { scheduling = false; confirmDelete = true },
                onDismiss = { scheduling = false },
            )
        }
    }
}

/**
 * Stateless: the photo (or the category colour), title, when, place / cost / best time, notes, links, then the place's
 * About, Good to know, Opening hours and Location when there is text. Empty parts are left out, with no empty headings.
 */
@Composable
fun ActivityDetailContent(
    item: ItineraryItemDto,
    links: List<ActivityLinkRow>,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenLink: (url: String) -> Unit,
    onCopyLink: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    place: PlaceText = PlaceText.None,
    onDelete: () -> Unit = {},
    /** "Jadwalkan" next to "Belum dijadwalkan"; null hides it. */
    onSchedule: (() -> Unit)? = null,
    onRetryPlace: () -> Unit = {},
    onOpenMaps: (url: String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    val info = (place as? PlaceText.Loaded)?.info
    val mapsUrl = activityMapsUrl(item)
    Column(
        modifier.fillMaxSize().background(scheme.surface).semantics { paneTitle = item.title },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Hero(
                info = info,
                onBack = onBack,
                height = 300.dp,
                rounded = false,
                category = item.category,
                actions = {
                    if (canEdit) {
                        RoundIcon(Symbols.edit, stringResource(Res.string.activity_detail_edit_cd), onEdit)
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            RoundIcon(Symbols.moreVert, stringResource(Res.string.activity_detail_more_cd), { menu = true })
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.activity_delete_confirm), color = scheme.error) },
                                    onClick = { menu = false; onDelete() },
                                )
                            }
                        }
                    }
                },
            )
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WhenRow(item, item.dayDate?.let(dates::fullWeekdayDayMonth), onSchedule)
                    InfoRows(item, info, mapsUrl, onOpenMaps)
                }
                if (!item.notes.isNullOrBlank()) {
                    Section(stringResource(Res.string.activity_notes)) {
                        Text(
                            item.notes.orEmpty(),
                            Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (links.isNotEmpty()) {
                    Column {
                        Text(stringResource(Res.string.activity_links_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                        links.forEachIndexed { index, link ->
                            if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                            LinkRowItem(link, onOpen = { onOpenLink(link.url) }, onCopy = { onCopyLink(link.url) })
                        }
                    }
                }
                PlaceSections(place, onRetryPlace, onOpenMaps)
            }
        }
    }
}

/** "Jumat, 30 Okt · 09:00–10:30", or "Belum dijadwalkan" with "Jadwalkan" for an editor. */
@Composable
private fun WhenRow(item: ItineraryItemDto, dayText: String?, onSchedule: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val line = whenLine(item, dayText)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(Symbols.schedule, size = 18.dp, tint = scheme.onSurfaceVariant)
        if (dayText != null && line != null) {
            Text(line, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        } else {
            Text(stringResource(Res.string.activity_day_unscheduled), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            if (onSchedule != null) {
                TextButton(onClick = onSchedule, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.itinerary_schedule)) }
            }
        }
    }
}

/** Place (tap → Maps; left out when it only repeats the title), cost estimate, best time: each only when known. */
@Composable
private fun InfoRows(item: ItineraryItemDto, info: PlaceDetailInfo?, mapsUrl: String?, onOpenMaps: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val placeName = item.placeName?.takeIf { it.isNotBlank() && !repeatsTitle(it, item.title) }
    val cost = info?.takeIf { it.estimatedCostMinor != null && it.costCurrency != null }?.let { costLabel(null, it) }
    val best = info?.bestTime?.let { stringResource(Res.string.place_detail_best_time, it) }
    if (placeName == null && cost == null && best == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (placeName != null) {
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .then(if (mapsUrl != null) Modifier.clickable(role = Role.Button) { onOpenMaps(mapsUrl) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.locationOn, size = 18.dp, tint = if (mapsUrl != null) scheme.primary else scheme.onSurfaceVariant)
                Text(placeName, style = MaterialTheme.typography.bodyMedium, color = if (mapsUrl != null) scheme.primary else scheme.onSurfaceVariant)
            }
        }
        cost?.let { InfoRow(Symbols.payments, it) }
        best?.let { InfoRow(Symbols.schedule, it) }
    }
}

@Composable
private fun InfoRow(symbol: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Symbol(symbol, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** About, Good to know, Opening hours (AI estimate label) and Location: only what the place has. Loading and failure stay in this part. */
@Composable
private fun PlaceSections(place: PlaceText, onRetry: () -> Unit, onOpenMaps: (String) -> Unit) {
    when (place) {
        PlaceText.None -> Unit
        PlaceText.Loading -> LoadingSkeleton()
        PlaceText.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.place_detail_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.state_retry)) }
        }
        is PlaceText.Loaded -> {
            val info = place.info
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                info.about?.let { about -> Section(stringResource(Res.string.place_detail_about)) { Text(about, style = MaterialTheme.typography.bodyMedium) } }
                if (info.highlights.isNotEmpty()) {
                    Section(stringResource(Res.string.place_detail_highlights)) {
                        info.highlights.forEach { tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Symbol(Symbols.info, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(tip, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                HoursBlock(info)
                LocationBlock(info) { onOpenMaps(googleMapsPlaceUrl(it.name, it.placeId)) }
            }
        }
    }
}

/** The place in Google Maps: by place id when there is one, else by its point; null = nothing to open. */
internal fun activityMapsUrl(item: ItineraryItemDto): String? {
    val placeId = item.placeId
    if (placeId != null) return googleMapsPlaceUrl(item.placeName?.takeIf { it.isNotBlank() } ?: item.title, placeId)
    val lat = item.lat
    val lng = item.lng
    return if (lat != null && lng != null) "https://www.google.com/maps/search/?api=1&query=$lat,$lng" else null
}

/**
 * A place line that only repeats the title ("Museum D'Topeng Kingdom" under "Museum D'Topeng Kingdom", or with a city
 * after it): the card and the detail leave it out (Rockstar's spec, accepted 23 Nov 2026).
 */
internal fun repeatsTitle(place: String, title: String): Boolean {
    val p = place.trim().lowercase()
    val t = title.trim().lowercase()
    if (t.isEmpty()) return false
    return p == t || (p.startsWith(t) && p.getOrNull(t.length) == ',')
}

/** "Jumat, 20 Nov · 20:00–20:30"; just the clock part for an item without a day is not shown. */
@Composable
private fun whenLine(item: ItineraryItemDto, dayText: String?): String? {
    if (dayText == null) return item.durationMin?.takeIf { it > 0 }?.let { formatDuration(it) }
    val start: LocalTime? = item.startTime
    val end: LocalTime? = item.endTime
    val clock = when {
        start != null && end != null -> formatTime(start) + "–" + formatTime(end)
        start != null -> formatTime(start)
        else -> null
    }
    return if (clock == null) dayText else "$dayText · $clock"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRowItem(link: ActivityLinkRow, onOpen: () -> Unit, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val openBrowser = stringResource(Res.string.activity_link_open_browser)
    val copyLink = stringResource(Res.string.activity_link_copy)
    val description = stringResource(Res.string.activity_link_row_description, link.label, link.host)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menu = true })
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    // the long-press menu is a custom action too, for screen readers
                    customActions = listOf(
                        CustomAccessibilityAction(openBrowser) { onOpen(); true },
                        CustomAccessibilityAction(copyLink) { onCopy(); true },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Symbol(Symbols.link, size = 22.dp, tint = scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(link.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // the full domain always shows: a link from another member opens on this phone
                Text(link.host, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Symbol(Symbols.openInNew, size = 20.dp, tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(openBrowser) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text(copyLink) }, onClick = { menu = false; onCopy() })
        }
    }
}

@Preview
@Composable
private fun ActivityDetailPreview() = IrisPreview {
    val item = ItineraryItemDto(
        id = "1", tripId = "t", dayDate = LocalDate(2026, 11, 20), startTime = LocalTime(20, 0), endTime = LocalTime(20, 30),
        title = "Makan malam di Jimbaran", category = Category.FOOD, placeName = "Jimbaran Bay Seafood", notes = "Reservasi atas nama Budi, 4 orang.",
        links = listOf("https://www.booking.com/x", "https://www.instagram.com/jimbaran"),
        sortOrder = 1.0,
        createdAt = kotlin.time.Instant.fromEpochSeconds(0), updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
    )
    Box(Modifier.height(720.dp)) {
        ActivityDetailContent(
            item, listOf(ActivityLinkRow("https://www.booking.com/x", "Booking.com", "booking.com"), ActivityLinkRow("https://www.instagram.com/jimbaran", "Instagram", "instagram.com")),
            canEdit = true, onBack = {}, onEdit = {}, onOpenLink = {}, onCopyLink = {},
        )
    }
}

@Preview
@Composable
private fun ActivityDetailAiPlacePreview() = IrisPreview {
    val item = ItineraryItemDto(
        id = "2", tripId = "t", dayDate = null, title = "Air Terjun Tegenungan", category = Category.ATTRACTION,
        placeName = "Air Terjun Tegenungan", placeId = "p1", sortOrder = 1.0,
        createdAt = kotlin.time.Instant.fromEpochSeconds(0), updatedAt = kotlin.time.Instant.fromEpochSeconds(0),
    )
    val info = PlaceDetailInfo(
        placeId = "p1", name = "Air Terjun Tegenungan", about = "Air terjun setinggi sekitar 15 meter di dekat Ubud.",
        highlights = listOf("Paling sepi di pagi hari."), bestTime = "Pagi",
        openingHours = listOf(app.ransel.shared.dto.OpeningHoursLine("Setiap hari", "08:00 – 18:00")),
        estimatedCostMinor = 25_000, costCurrency = "IDR",
    )
    Box(Modifier.height(900.dp)) {
        ActivityDetailContent(item, emptyList(), canEdit = true, onBack = {}, onEdit = {}, onOpenLink = {}, onCopyLink = {}, place = PlaceText.Loaded(info), onSchedule = {})
    }
}
