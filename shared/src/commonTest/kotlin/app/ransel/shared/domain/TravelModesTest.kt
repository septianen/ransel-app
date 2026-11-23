package app.ransel.shared.domain

import app.ransel.shared.dto.TravelModeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TravelModesTest {
    private val stamp = Instant.parse("2026-11-18T00:00:00Z")

    @Test
    fun aPairNeedsTwoDifferentItemsAndASavableMode() {
        assertEquals(emptyMap(), validateTravelMode("a", "b", LegKind.WALK))
        assertEquals("MUST_DIFFER", validateTravelMode("a", "a", LegKind.CAR)["toItemId"])
        assertEquals("REQUIRED", validateTravelMode("", "b", LegKind.CAR)["fromItemId"])
        assertEquals("INVALID", validateTravelMode("a", "b", LegKind.FAR)["mode"])
    }

    @Test
    fun liveRowsAreKeyedOnThePairAndDeletedOnesAreDropped() {
        val live = TravelModeDto("1", "t", "from", "to", LegKind.WALK, stamp, stamp)
        val gone = live.copy(id = "2", fromItemId = "x", toItemId = "y", deletedAt = stamp)
        assertEquals(mapOf(("from" to "to") to LegKind.WALK), travelModeOverrides(listOf(live, gone)))
        assertTrue(SAVED_LEG_KINDS.containsAll(listOf(LegKind.WALK, LegKind.MOTORCYCLE, LegKind.CAR, LegKind.PUBLIC_TRANSPORT)))
    }
}
