package app.ransel.feature

import app.ransel.data.sync.DroppedChanges
import app.ransel.data.sync.DroppedKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DroppedChangesTest {
    @Test
    fun aSecondBatchInTheSameTripAddsUp() {
        val first = DroppedChanges(mapOf(DroppedKind.EXPENSE to 2), setOf("t1"), tripTitle = "Bali 4 hari")
        val second = DroppedChanges(mapOf(DroppedKind.EXPENSE to 1, DroppedKind.ACTIVITY to 1), setOf("t1"), tripTitle = "Bali 4 hari")
        val both = first + second
        assertEquals(mapOf(DroppedKind.EXPENSE to 3, DroppedKind.ACTIVITY to 1), both.counts)
        assertEquals("Bali 4 hari", both.tripTitle)
    }

    @Test
    fun anotherTripDropsTheTitleAndAnyOtherRefusalDropsNoAccess() {
        val first = DroppedChanges(mapOf(DroppedKind.PACKING to 1), setOf("t1"), tripTitle = "Bali 4 hari")
        val second = DroppedChanges(mapOf(DroppedKind.PACKING to 1), setOf("t2"), tripTitle = "Lombok", noAccess = false)
        val both = first + second
        assertNull(both.tripTitle)
        assertEquals(setOf("t1", "t2"), both.tripIds)
        assertFalse(both.noAccess)
    }
}
