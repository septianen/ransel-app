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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import app.ransel.ui.components.AccentTile
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
import ransel.composeapp.generated.resources.activity_detail_edit
import ransel.composeapp.generated.resources.activity_detail_title
import ransel.composeapp.generated.resources.activity_link_copied
import ransel.composeapp.generated.resources.activity_link_copy
import ransel.composeapp.generated.resources.activity_link_open_browser
import ransel.composeapp.generated.resources.activity_link_open_failed
import ransel.composeapp.generated.resources.activity_link_row_description
import ransel.composeapp.generated.resources.activity_links_title
import ransel.composeapp.generated.resources.activity_notes

/** "Detail aktivitas": read-only; "Edit" opens the form ([onEdit]). Closes by itself when the activity is gone. */
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

    fun copy(url: String) {
        clipboard.setText(AnnotatedString(url))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(copiedText)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (item != null) {
            ActivityDetailContent(
                item = item,
                links = state.links,
                canEdit = state.canEdit,
                onBack = onBack,
                onEdit = onEdit,
                onOpenLink = { url ->
                    // outside the app: the browser or the app that handles the address (Instagram, Maps ...)
                    val opened = runCatching { uriHandler.openUri(url) }.isSuccess
                    if (!opened) scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        if (snackbar.showSnackbar(failedText, actionLabel = copyLabel) == SnackbarResult.ActionPerformed) copy(url)
                    }
                },
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
}

/** Stateless: the read screen of one activity. */
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
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    Column(modifier.fillMaxSize().background(scheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back), tint = scheme.onSurface)
            }
            Text(
                stringResource(Res.string.activity_detail_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canEdit) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.height(36.dp).padding(end = 8.dp)) {
                    Text(stringResource(Res.string.activity_detail_edit), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentTile(item.category.tileColor(), item.category.symbol, size = 56.dp, cornerRadius = 16.dp, iconSize = 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    val day = item.dayDate
                    Text(
                        whenLine(item, day?.let(dates::fullWeekdayDayMonth)) ?: stringResource(Res.string.activity_day_unscheduled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (!item.placeName.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Symbols.locationOn, size = 22.dp, tint = if (item.lat != null && item.lng != null) scheme.primary else scheme.onSurfaceVariant)
                    Text(item.placeName.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            if (!item.notes.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.activity_notes), style = MaterialTheme.typography.labelLarge)
                    Text(item.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium)
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
        }
    }
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
    Box(Modifier.height(520.dp)) {
        ActivityDetailContent(
            item, listOf(ActivityLinkRow("https://www.booking.com/x", "Booking.com", "booking.com"), ActivityLinkRow("https://www.instagram.com/jimbaran", "Instagram", "instagram.com")),
            canEdit = true, onBack = {}, onEdit = {}, onOpenLink = {}, onCopyLink = {},
        )
    }
}
