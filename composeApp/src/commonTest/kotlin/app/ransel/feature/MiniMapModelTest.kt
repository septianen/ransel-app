package app.ransel.feature

import app.ransel.feature.itinerary.map.MapPin
import app.ransel.feature.itinerary.map.firstUnlocated
import app.ransel.feature.itinerary.map.googleMapsUrl
import app.ransel.feature.itinerary.map.hasMapPoint
import app.ransel.feature.itinerary.map.isMiniMapOpen
import app.ransel.feature.itinerary.map.mapPinsOf
import app.ransel.feature.itinerary.map.pinToOpen
import app.ransel.feature.itinerary.map.storedMiniMapOpen
import app.ransel.feature.itinerary.map.unlocatedCount
import app.ransel.feature.itinerary.map.GeoPoint
import app.ransel.feature.itinerary.map.clampToRect
import app.ransel.feature.itinerary.map.clampZoom
import app.ransel.feature.itinerary.map.dragBounds
import app.ransel.feature.itinerary.map.fitCamera
import app.ransel.feature.itinerary.map.isMiniMapTwoFingerHintShown
import app.ransel.feature.itinerary.map.minZoomPreference
import app.ransel.feature.itinerary.map.MINI_MAP_MAX_ZOOM
import app.ransel.feature.itinerary.map.MINI_MAP_MIN_ZOOM_FLOOR
import app.ransel.feature.itinerary.map.MINI_MAP_SINGLE_FIT_ZOOM
import kotlin.math.abs
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

    @Test
    fun theMapIsClosedUntilTheUserOpensItAndTheChoiceIsRemembered() {
        assertFalse(isMiniMapOpen(null), "never set: closed, so no map is built")
        assertFalse(isMiniMapOpen(""))
        assertFalse(isMiniMapOpen("0"))
        assertTrue(isMiniMapOpen("1"))
        assertEquals("1", storedMiniMapOpen(true))
        assertNull(storedMiniMapOpen(false), "closing again goes back to the default")
        assertTrue(isMiniMapOpen(storedMiniMapOpen(true)))
        assertFalse(isMiniMapOpen(storedMiniMapOpen(false)))
    }

    @Test
    fun theTwoFingerHintIsShownOnceThenRemembered() {
        assertFalse(isMiniMapTwoFingerHintShown(null))
        assertFalse(isMiniMapTwoFingerHintShown("0"))
        assertTrue(isMiniMapTwoFingerHintShown("1"))
    }

    @Test
    fun minZoomIsOneLevelWiderThanTheFitAndNeverWiderThanTen() {
        assertEquals(14f, minZoomPreference(15f))
        assertEquals(MINI_MAP_MIN_ZOOM_FLOOR, minZoomPreference(10f))
        assertEquals(MINI_MAP_MIN_ZOOM_FLOOR, minZoomPreference(8f), "a very wide fit still stops at zoom 10")
        assertEquals(MINI_MAP_MAX_ZOOM, clampZoom(21f, 15f))
        assertEquals(14f, clampZoom(12f, 15f))
    }

    @Test
    fun oneStopFitsAtZoom15AndDragsInsideAboutTwoKilometres() {
        val pin = GeoPoint(-8.5, 115.2)
        val fit = fitCamera(listOf(pin), 1080, 480, 0, 0, 0, 0, 2.75f)!!
        assertEquals(-8.5, fit.centerLat)
        assertEquals(115.2, fit.centerLng)
        assertEquals(MINI_MAP_SINGLE_FIT_ZOOM, fit.zoom)
        val box = dragBounds(listOf(pin))!!
        val kmNorth = (box.north - box.south) * 110.574
        assertTrue(abs(kmNorth - 2.0) < 0.05, "the drag box is ~2 km tall, was $kmNorth")
        val inside = clampToRect(box.centerLat, box.centerLng, box)
        assertEquals(box.centerLat, inside.lat)
        val outside = clampToRect(box.north + 1, box.east + 1, box)
        assertEquals(box.north, outside.lat)
        assertEquals(box.east, outside.lng)
    }

    @Test
    fun severalStopsPadTheDragBoxByHalfAndFitInsideTheViewport() {
        val pins = listOf(GeoPoint(-8.50, 115.20), GeoPoint(-8.60, 115.30))
        val box = dragBounds(pins)!!
        val rawLat = 0.10
        assertEquals(-8.60 - rawLat * 0.25, box.south, 1e-9)
        assertEquals(-8.50 + rawLat * 0.25, box.north, 1e-9)
        val fit = fitCamera(pins, 1000, 500, 40, 40, 40, 40, 2f)!!
        assertTrue(fit.zoom in MINI_MAP_MIN_ZOOM_FLOOR..MINI_MAP_MAX_ZOOM)
        assertTrue(fit.zoom < 18f, "two Bali stops in a 1000 px view are not at max zoom")
        assertEquals((-8.50 + -8.60) / 2, fit.centerLat, 1e-9)
    }
}
