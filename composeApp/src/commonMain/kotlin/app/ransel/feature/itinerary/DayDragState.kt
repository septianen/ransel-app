package app.ransel.feature.itinerary

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Drag state of one itinerary day. Rows and day chips report their bounds (root coordinates, measured
 * at rest); while dragging only graphics-layer translations change, so those bounds stay valid and the
 * finger position can be hit-tested against both.
 *
 * Drop targets: another row's slot (reorder within the day) or a day chip (move to that day).
 *
 * The instance must outlive a day change only as an object identity: [pointerInput] keys off it, so
 * `remember(selectedDay) { DayDragState() }` would cancel the gesture when the list recomposes.
 */
@Stable
class DayDragState {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var deltaY by mutableFloatStateOf(0f)
        private set
    var hoveredChip by mutableStateOf<Int?>(null)
        private set
    /** Pointer is up; banner and border animate out before [finish]. */
    var settling by mutableStateOf(false)
        private set

    private var deltaX = 0f
    private var touch = Offset.Zero
    private var orderedIds: List<String> = emptyList()
    var settleToken: Int = 0
        private set

    val rowBounds = HashMap<String, Rect>()
    val chipBounds = HashMap<Int, Rect>()

    val isDragging: Boolean get() = draggingId != null

    /** Banner/border still at full strength (not the settle fade). */
    val showChrome: Boolean get() = isDragging && !settling

    /** The list must not scroll while a drag runs (the scroll would steal the gesture). */
    val blocksScroll: Boolean get() = isDragging

    fun start(id: String, touchInRow: Offset, ids: List<String>) {
        draggingId = id
        orderedIds = ids
        touch = touchInRow
        deltaX = 0f
        deltaY = 0f
        hoveredChip = null
        settling = false
        settleToken++
    }

    fun dragBy(dx: Float, dy: Float) {
        if (settling) return
        deltaX += dx
        deltaY += dy
        val finger = finger() ?: return
        hoveredChip = chipBounds.entries.firstOrNull { it.value.contains(finger) }?.key
    }

    /** Finger is up: keep [draggingId] so chrome can fade, freeze the delta. */
    fun beginSettle() {
        if (draggingId == null || settling) return
        settling = true
        settleToken++
    }

    fun finish() {
        draggingId = null
        deltaY = 0f
        deltaX = 0f
        hoveredChip = null
        settling = false
        orderedIds = emptyList()
        settleToken++
    }

    fun clearRowBounds() {
        rowBounds.clear()
    }

    private fun finger(): Offset? {
        val bounds = rowBounds[draggingId ?: return null] ?: return null
        return Offset(bounds.left + touch.x + deltaX, bounds.top + touch.y + deltaY)
    }

    fun fromIndex(): Int {
        val id = draggingId ?: return -1
        return orderedIds.indexOf(id)
    }

    /** Index the dragged row would take: how many other rows have their center above the dragged center. */
    fun targetIndex(): Int {
        val id = draggingId ?: return -1
        val from = orderedIds.indexOf(id)
        if (from < 0) return -1
        val dragged = rowBounds[id] ?: return from
        val center = dragged.center.y + deltaY
        return orderedIds.indices.count { it != from && (rowBounds[orderedIds[it]]?.center?.y ?: Float.MAX_VALUE) < center }
    }
}
