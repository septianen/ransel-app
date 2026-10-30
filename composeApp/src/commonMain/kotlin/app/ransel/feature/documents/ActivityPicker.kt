package app.ransel.feature.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.stringResource
import app.ransel.navigation.SheetBackHandler
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.Category
import app.ransel.ui.components.CategoryIcon
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.SectionHeader
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlin.time.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_back
import ransel.composeapp.generated.resources.document_no_activity
import ransel.composeapp.generated.resources.document_no_activity_found
import ransel.composeapp.generated.resources.document_pick_activity
import ransel.composeapp.generated.resources.document_search_activity
import ransel.composeapp.generated.resources.itinerary_day_chip
import ransel.composeapp.generated.resources.itinerary_unscheduled

/** One day of the picker (or the unscheduled places when [day] is null). */
private data class ActivityDay(val day: LocalDate?, val items: List<ItineraryItemDto>)

/**
 * "Pilih aktivitas" for a document (owner, 27 Oct 2026): a full screen over the add-document sheet, so a long
 * itinerary stays readable. "Tanpa aktivitas" first, a search, then the activities per day by time, unscheduled ones
 * last. A tap chooses and closes; back closes without changing anything.
 */
@Composable
fun ActivityPicker(
    activities: List<ItineraryItemDto>,
    tripStart: LocalDate?,
    selectedId: String?,
    onPick: (itemId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetBackHandler(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            ActivityPickerContent(activities, tripStart, selectedId, onPick, onDismiss)
        }
    }
}

@Composable
private fun ActivityPickerContent(
    activities: List<ItineraryItemDto>,
    tripStart: LocalDate?,
    selectedId: String?,
    onPick: (itemId: String?) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dates = rememberDateFormatter()
    var query by remember { mutableStateOf("") }
    val days = remember(activities, query) {
        val q = query.trim()
        val shown = activities.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
        val scheduled = shown.filter { it.dayDate != null }.groupBy { it.dayDate!! }.entries.sortedBy { it.key }
            .map { (day, items) -> ActivityDay(day, items.sortedWith(compareBy<ItineraryItemDto>({ it.startTime == null }, { it.startTime }, { it.sortOrder }))) }
        val unscheduled = shown.filter { it.dayDate == null }.sortedBy { it.sortOrder }
        scheduled + listOfNotNull(unscheduled.takeIf { it.isNotEmpty() }?.let { ActivityDay(null, it) })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Text(stringResource(Res.string.document_pick_activity), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        IrisTextField(
            label = "",
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.document_search_activity),
            leadingSymbol = Symbols.search,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                PickRow(selected = selectedId == null, onClick = { onPick(null) }) {
                    Box(Modifier.size(40.dp).clip(IrisShape.md).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, tint = scheme.onSurfaceVariant)
                    }
                    Text(stringResource(Res.string.document_no_activity), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (days.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        stringResource(Res.string.document_no_activity_found),
                        Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
            }
            days.forEach { group ->
                item(key = "day-${group.day}") {
                    val heading = group.day?.let { day ->
                        val number = tripStart?.daysUntil(day)?.plus(1)
                        listOfNotNull(number?.let { stringResource(Res.string.itinerary_day_chip, it) }, dates.weekdayDayMonth(day)).joinToString(" · ")
                    } ?: stringResource(Res.string.itinerary_unscheduled)
                    SectionHeader(heading, Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(group.items, key = { it.id }) { item ->
                    PickRow(selected = item.id == selectedId, onClick = { onPick(item.id) }) {
                        CategoryIcon(item.category)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.startTime?.let { t ->
                                Text(
                                    "${t.hour.toString().padStart(2, '0')}.${t.minute.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(IrisShape.lg)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        if (selected) Symbol(Symbols.check, tint = scheme.primary)
    }
}

@Preview
@Composable
private fun ActivityPickerPreview() = IrisPreview {
    val stamp = Instant.parse("2026-11-01T00:00:00Z")
    fun item(id: String, title: String, day: LocalDate?, time: LocalTime?, category: Category) = ItineraryItemDto(
        id = id, tripId = "t", title = title, category = category, dayDate = day, startTime = time, sortOrder = 1.0,
        createdAt = stamp, updatedAt = stamp,
    )
    ActivityPickerContent(
        activities = listOf(
            item("1", "Penerbangan CGK–DPS", LocalDate(2026, 11, 20), LocalTime(6, 10), Category.TRANSPORT),
            item("2", "Check-in hotel", LocalDate(2026, 11, 20), LocalTime(14, 0), Category.LODGING),
            item("3", "Uluwatu", LocalDate(2026, 11, 21), LocalTime(16, 0), Category.ATTRACTION),
            item("4", "Snorkeling Amed", null, null, Category.ATTRACTION),
        ),
        tripStart = LocalDate(2026, 11, 20),
        selectedId = "2",
        onPick = {},
        onBack = {},
    )
}
