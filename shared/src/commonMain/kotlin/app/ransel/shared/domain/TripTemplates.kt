package app.ransel.shared.domain

import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.dto.TripTemplateDto
import app.ransel.shared.dto.TripTemplateItem
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * A trip's itinerary as template lines: the day becomes its number from [startDate] (unscheduled stays unscheduled;
 * a trip without dates has only unscheduled lines).
 */
fun templateItemsOf(startDate: LocalDate?, items: List<ItineraryItemDto>): List<TripTemplateItem> =
    items.filter { it.deletedAt == null }
        .sortedWith(compareBy({ it.dayDate == null }, { it.dayDate }, { it.sortOrder }))
        .map {
            TripTemplateItem(
                dayIndex = it.dayDate?.let { day -> startDate?.daysUntil(day) }?.takeIf { index -> index >= 0 },
                startTime = it.startTime.takeIf { _ -> it.dayDate != null },
                endTime = it.endTime.takeIf { _ -> it.dayDate != null },
                title = it.title, category = it.category, placeName = it.placeName, placeId = it.placeId,
                lat = it.lat, lng = it.lng, durationMin = it.durationMin, notes = it.notes,
            )
        }

/** The shared packing list as template lines (no ticks, no bringer). */
fun templatePackingOf(items: List<PackingItemDto>): List<TemplateItem> =
    items.filter { it.deletedAt == null }.sortedBy { it.sortOrder }.map { TemplateItem(it.category, it.name, it.quantity) }

/** What a new trip from [template] starts with. */
data class TemplateStart(val items: List<ItineraryItemInput>, val packing: List<CreatePackingItemRequest>)

/**
 * A template laid on a new trip from [startDate] to [endDate]: each line goes to its day; lines past the last day go to
 * "Belum dijadwalkan" without their times (the user schedules them), and so does every line of a trip without dates.
 * [newId] makes the client-side ids.
 */
fun startFromTemplate(template: TripTemplateDto, startDate: LocalDate?, endDate: LocalDate?, newId: () -> String): TemplateStart {
    val lastIndex = if (startDate != null && endDate != null) startDate.daysUntil(endDate) else -1
    val items = template.items.mapIndexed { order, line ->
        val day = line.dayIndex?.takeIf { it in 0..lastIndex }?.let { startDate?.plus(DatePeriod(days = it)) }
        ItineraryItemInput(
            id = newId(),
            dayDate = day,
            startTime = line.startTime.takeIf { day != null },
            endTime = line.endTime.takeIf { day != null },
            title = line.title, category = line.category, placeName = line.placeName, placeId = line.placeId,
            lat = line.lat, lng = line.lng, durationMin = line.durationMin, notes = line.notes,
            sortOrder = (order + 1).toDouble(),
        )
    }
    val packing = template.packing.mapIndexed { order, line ->
        CreatePackingItemRequest(newId(), line.category, line.name, line.quantity ?: 1, (order + 1).toDouble())
    }
    return TemplateStart(items, packing)
}
