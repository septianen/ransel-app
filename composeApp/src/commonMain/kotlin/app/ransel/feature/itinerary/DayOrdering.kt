package app.ransel.feature.itinerary

import app.ransel.shared.domain.sortOrderBetween
import app.ransel.shared.dto.ItineraryItemDto
import kotlinx.datetime.LocalTime

/**
 * `sortOrder` for an activity that starts at [start], among the other activities of its day
 * ([siblings], the item itself excluded): right before the first sibling that starts later, so a new
 * 09:00 activity lands above 10:00 and 13:00 ones. Without a start time, or when nothing starts later,
 * it goes to the end. Untimed siblings and any order the user set by dragging are left where they are.
 */
fun sortOrderForStart(siblings: List<ItineraryItemDto>, start: LocalTime?): Double {
    val ordered = siblings.sortedBy { it.sortOrder }
    if (start != null) {
        val index = ordered.indexOfFirst { sibling -> sibling.startTime?.let { it > start } == true }
        if (index >= 0) {
            return sortOrderBetween(ordered.getOrNull(index - 1)?.sortOrder, ordered[index].sortOrder)
        }
    }
    return (ordered.lastOrNull()?.sortOrder ?: 0.0) + 1.0
}
