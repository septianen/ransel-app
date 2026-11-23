package app.ransel.feature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.ransel.feature.itinerary.DayDragState
import app.ransel.feature.itinerary.edgeScrollVelocityPxPerSec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayDragStateTest {
    @Test
    fun edgeScrollStopsOutsideTheViewportAndOutsideTheZone() {
        assertEquals(0f, edgeScrollVelocityPxPerSec(fingerY = -1f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f))
        assertEquals(0f, edgeScrollVelocityPxPerSec(fingerY = 401f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f))
        assertEquals(0f, edgeScrollVelocityPxPerSec(fingerY = 200f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f))
        assertEquals(0f, edgeScrollVelocityPxPerSec(fingerY = 50f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f))
        assertEquals(0f, edgeScrollVelocityPxPerSec(fingerY = 350f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f))
    }

    @Test
    fun edgeScrollRampsTowardTheTopAndBottom() {
        val slowTop = edgeScrollVelocityPxPerSec(fingerY = 40f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        val fastTop = edgeScrollVelocityPxPerSec(fingerY = 10f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        val atTop = edgeScrollVelocityPxPerSec(fingerY = 0f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        assertTrue(slowTop < 0f && fastTop < slowTop && atTop == -100f)

        val slowBottom = edgeScrollVelocityPxPerSec(fingerY = 360f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        val fastBottom = edgeScrollVelocityPxPerSec(fingerY = 390f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        val atBottom = edgeScrollVelocityPxPerSec(fingerY = 400f, viewportTop = 0f, viewportBottom = 400f, zonePx = 50f, maxPxPerSec = 100f)
        assertTrue(slowBottom > 0f && fastBottom > slowBottom && atBottom == 100f)
    }

    @Test
    fun targetIndexMovesWhenTheListAutoScrollsUnderAHeldCard() {
        val drag = DayDragState()
        drag.rowBounds["a"] = Rect(0f, 0f, 100f, 100f)
        drag.rowBounds["b"] = Rect(0f, 100f, 100f, 200f)
        drag.rowBounds["c"] = Rect(0f, 200f, 100f, 300f)
        drag.start("a", Offset(50f, 50f), listOf("a", "b", "c"))
        assertEquals(0, drag.targetIndex())

        drag.onListScrolled(150f)
        assertEquals(150f, drag.deltaY)
        assertEquals(1, drag.targetIndex())

        drag.onListScrolled(100f)
        assertEquals(2, drag.targetIndex())
    }

    @Test
    fun slotHeightIsTheRowPlusTheTravelGapUnderItClosedUpToTheHeldSpacing() {
        val drag = DayDragState()
        drag.compactSpacingPx = 16f
        drag.rowBounds["a"] = Rect(0f, 0f, 100f, 80f)
        drag.gapHeightAfter["a"] = 90f
        drag.rowBounds["b"] = Rect(0f, 170f, 100f, 250f)
        drag.rowBounds["c"] = Rect(0f, 250f, 100f, 330f)
        drag.gapHeightAfter["c"] = 10f
        assertEquals(96f, drag.slotHeight("a"), "a 90 px capsule gap closes up to 16 px")
        assertEquals(80f, drag.slotHeight("b"), "no capsule, nothing to add")
        assertEquals(90f, drag.slotHeight("c"), "a gap smaller than the spacing is kept as it is")
    }

    @Test
    fun theRowsCloseUpAroundTheHeldCardAndTheHeldCardStaysPut() {
        val drag = DayDragState()
        drag.compactSpacingPx = 16f
        // a, b, c, d: a capsule gap of 90 px after a and after c; b has none
        listOf("a" to 0f, "b" to 170f, "c" to 250f, "d" to 430f).forEach { (id, top) ->
            drag.rowBounds[id] = Rect(0f, top, 100f, top + 80f)
        }
        drag.gapHeightAfter["a"] = 90f
        drag.gapHeightAfter["c"] = 90f
        drag.start("b", Offset(50f, 40f), listOf("a", "b", "c", "d"))

        assertEquals(0f, drag.compaction("b"), "the held card is not moved")
        assertEquals(74f, drag.compaction("a"), "above: moves down by the 74 px its gap loses")
        assertEquals(0f, drag.compaction("c"), "below: b has no capsule under it, so c does not move")
        assertEquals(-74f, drag.compaction("d"), "below: the 74 px that c's capsule gap loses")
    }

    @Test
    fun noRowMovesWhenThereIsNoCardHeldOrNoCapsule() {
        val drag = DayDragState()
        drag.compactSpacingPx = 16f
        drag.rowBounds["a"] = Rect(0f, 0f, 100f, 80f)
        drag.rowBounds["b"] = Rect(0f, 96f, 100f, 176f)
        assertEquals(0f, drag.compaction("a"), "nothing held")
        drag.start("a", Offset(50f, 40f), listOf("a", "b"))
        assertEquals(0f, drag.compaction("b"), "no capsule gap anywhere")
    }

    @Test
    fun theDropIndexIsFoundAmongTheClosedUpRows() {
        val drag = DayDragState()
        drag.compactSpacingPx = 16f
        drag.rowBounds["a"] = Rect(0f, 0f, 100f, 80f)
        drag.rowBounds["b"] = Rect(0f, 170f, 100f, 250f)
        drag.rowBounds["c"] = Rect(0f, 340f, 100f, 420f)
        drag.gapHeightAfter["a"] = 90f
        drag.gapHeightAfter["b"] = 90f
        // holding a: b closes up to center 210 - 74 = 136 and c to 380 - 148 = 232
        drag.start("a", Offset(50f, 40f), listOf("a", "b", "c"))
        assertEquals(0, drag.targetIndex())
        drag.onListScrolled(100f) // a's center is now 140: past b's closed-up center
        assertEquals(1, drag.targetIndex())
        drag.onListScrolled(100f) // 240: past c as well
        assertEquals(2, drag.targetIndex())
    }

    @Test
    fun aPointerMoveDoesNotCountListScrollAsFingerMotionOnChips() {
        val drag = DayDragState()
        drag.rowBounds["a"] = Rect(0f, 200f, 100f, 280f)
        drag.chipBounds[1] = Rect(0f, 0f, 80f, 56f)
        drag.start("a", Offset(10f, 10f), listOf("a"))
        drag.dragTo(Offset(40f, 20f))
        assertEquals(1, drag.hoveredChip)
        drag.onListScrolled(400f)
        assertEquals(1, drag.hoveredChip, "chips sit outside the list; scroll must not move the finger hit")
        assertEquals(210f, drag.deltaY)
    }
}
