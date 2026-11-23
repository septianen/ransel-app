package app.ransel.feature.inspiration

import app.ransel.shared.domain.ideaActivityTitle
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource

/**
 * An unscheduled itinerary row from an Explore Inspirasi card: activity as the title (region stripped when the model
 * duplicated it), region as an area place, no day. The user schedules it later from Belum dijadwalkan.
 */
fun unscheduledItemFromIdea(card: InspirationCardDto, itemId: String, sortOrder: Double): ItineraryItemInput {
    val region = card.region.trim()
    return ItineraryItemInput(
        id = itemId,
        dayDate = null,
        title = ideaActivityTitle(card.activity, region),
        category = Category.ATTRACTION,
        placeName = region.ifEmpty { null },
        placeKind = PlaceKind.AREA,
        placeCity = region.substringBefore(',').trim().ifEmpty { null },
        source = ItemSource.AI,
        sortOrder = sortOrder,
    )
}
