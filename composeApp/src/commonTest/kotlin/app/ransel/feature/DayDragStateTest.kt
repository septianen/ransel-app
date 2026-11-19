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
    fun slotHeightIsTheRowPlusTheTravelGapUnderIt() {
        val drag = DayDragState()
        drag.rowBounds["a"] = Rect(0f, 0f, 100f, 80f)
        drag.gapHeightAfter["a"] = 24f
        drag.rowBounds["b"] = Rect(0f, 104f, 100f, 184f)
        assertEquals(104f, drag.slotHeight("a"))
        assertEquals(80f, drag.slotHeight("b"))
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
