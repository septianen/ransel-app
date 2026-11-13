package app.ransel.feature

import app.ransel.feature.itinerary.map.MapPin
import app.ransel.feature.itinerary.map.firstUnlocated
import app.ransel.feature.itinerary.map.googleMapsUrl
import app.ransel.feature.itinerary.map.hasMapPoint
import app.ransel.feature.itinerary.map.mapPinsOf
import app.ransel.feature.itinerary.map.pinToOpen
import app.ransel.feature.itinerary.map.unlocatedCount
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val stamp = Instant.parse("2026-11-13T09:00:00Z")

private fun item(id: String, lat: Double? = null, lng: Double? = null, category: Category = Category.ATTRACTION) = ItineraryItemDto(
    id = id, tripId = "t1", title = "Aktivitas $id", category = category, lat = lat, lng = lng,
    sortOrder = 1.0, createdAt = stamp, updatedAt = stamp,
)

private val colors = { c: Category -> (c.ordinal + 1) to (c.ordinal + 100) }

class MiniMapModelTest {
    @Test
    fun pinsAreNumberedAmongTheActivitiesThatHaveAPointInTimelineOrder() {
        val day = listOf(
            item("a", -8.5, 115.2, Category.FOOD),
            item("b"),                                  // no place: no pin, and no gap in the numbers
            item("c", -8.6, 115.3, Category.LODGING),
            item("d", -8.7, 115.1, Category.SHOPPING),
        )
        val pins = mapPinsOf(day, colors)
        assertEquals(listOf("a", "c", "d"), pins.map { it.itemId })
        assertEquals(listOf(1, 2, 3), pins.map { it.number })
        assertEquals(MapPin("c", 2, -8.6, 115.3, Category.LODGING, Category.LODGING.ordinal + 1, Category.LODGING.ordinal + 100), pins[1])
    }

    @Test
    fun anActivityIsOnTheMapOnlyWithBothCoordinates() {
        assertTrue(item("a", 1.0, 2.0).hasMapPoint())
        assertFalse(item("b", 1.0, null).hasMapPoint())
        assertFalse(item("c", null, 2.0).hasMapPoint())
        assertFalse(item("d").hasMapPoint())
        assertEquals(emptyList(), mapPinsOf(listOf(item("b", 1.0, null)), colors))
    }

    @Test
    fun theUnlocatedOnesAreCountedAndTheFirstOneIsTheOneToFill() {
        val day = listOf(item("a", 1.0, 2.0), item("b"), item("c", 3.0, 4.0), item("d"))
        assertEquals(2, unlocatedCount(day))
        assertEquals("b", firstUnlocated(day)?.id)
        assertEquals(0, unlocatedCount(listOf(item("a", 1.0, 2.0))))
        assertNull(firstUnlocated(listOf(item("a", 1.0, 2.0))))
        assertNull(firstUnlocated(emptyList()))
        assertEquals(1, unlocatedCount(listOf(item("x"))), "a day where nothing has a place: all are unlocated")
    }

    @Test
    fun openInGoogleMapsOpensTheHighlightedPinElseTheFirstAsAPlaceNotARoute() {
        val pins = mapPinsOf(listOf(item("a", -8.5, 115.2), item("b", -8.6, 115.3)), colors)
        assertEquals("b", pinToOpen(pins, "b")?.itemId)
        assertEquals("a", pinToOpen(pins, null)?.itemId)
        assertEquals("a", pinToOpen(pins, "gone")?.itemId, "a highlight that is no longer on the day falls back to the first")
        assertNull(pinToOpen(emptyList(), "a"))

        val url = googleMapsUrl(pins[1])
        assertEquals("https://www.google.com/maps/search/?api=1&query=-8.6,115.3", url)
        assertFalse("dir" in url, "a place, never directions (navigation is a PRD non-goal)")
    }
}
