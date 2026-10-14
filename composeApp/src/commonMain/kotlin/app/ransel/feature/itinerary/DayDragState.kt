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
 */
@Stable
class DayDragState {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var deltaY by mutableFloatStateOf(0f)
        private set
    var hoveredChip by mutableStateOf<Int?>(null)
        private set

    private var deltaX = 0f
    private var touch = Offset.Zero

    val rowBounds = HashMap<String, Rect>()
    val chipBounds = HashMap<Int, Rect>()

    /** True from the moment a handle is touched, before any movement: the parent scroll must let go. */
    var armed by mutableStateOf(false)

    val isDragging: Boolean get() = draggingId != null

    /** The list must not scroll while a drag is possible or running (the scroll would steal the gesture). */
    val blocksScroll: Boolean get() = armed || isDragging

    fun start(id: String, touchInRow: Offset) {
        draggingId = id
        touch = touchInRow
        deltaX = 0f
        deltaY = 0f
        hoveredChip = null
    }

    fun dragBy(dx: Float, dy: Float) {
        deltaX += dx
        deltaY += dy
        val finger = finger() ?: return
        hoveredChip = chipBounds.entries.firstOrNull { it.value.contains(finger) }?.key
    }

    fun finish() {
        armed = false
        draggingId = null
        deltaY = 0f
        deltaX = 0f
        hoveredChip = null
    }

    private fun finger(): Offset? {
        val bounds = rowBounds[draggingId ?: return null] ?: return null
        return Offset(bounds.left + touch.x + deltaX, bounds.top + touch.y + deltaY)
    }

    /** Index the dragged row would take: how many other rows have their center above the dragged center. */
    fun targetIndex(orderedIds: List<String>): Int {
        val id = draggingId ?: return -1
        val from = orderedIds.indexOf(id)
        if (from < 0) return -1
        val dragged = rowBounds[id] ?: return from
        val center = dragged.center.y + deltaY
        return orderedIds.indices.count { it != from && (rowBounds[orderedIds[it]]?.center?.y ?: Float.MAX_VALUE) < center }
    }
}
