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
 * List auto-scroll during a drag is applied as [onListScrolled]: row bounds stay frozen and the
 * dragged card's translation grows by the consumed scroll so it stays under the finger. [targetIndex]
 * uses that translation, so the drop slot keeps moving as the list scrolls.
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
    /** Graphics-layer Y of the dragged row: pointer movement plus list auto-scroll. */
    var deltaY by mutableFloatStateOf(0f)
        private set
    var hoveredChip by mutableStateOf<Int?>(null)
        private set
    /** Pointer is up; banner and border animate out before [finish]. */
    var settling by mutableStateOf(false)
        private set

    private var pointerDeltaX = 0f
    private var pointerDeltaY = 0f
    private var scrollCompensationY = 0f
    private var touch = Offset.Zero
    private var orderedIds: List<String> = emptyList()
    var settleToken: Int = 0
        private set

    val rowBounds = HashMap<String, Rect>()
    val chipBounds = HashMap<Int, Rect>()
    /** Height of the travel gap under each row; 0 when the next activity has no capsule. */
    val gapHeightAfter = HashMap<String, Float>()
    var viewport: Rect = Rect.Zero

    val isDragging: Boolean get() = draggingId != null

    /** Banner/border still at full strength (not the settle fade). */
    val showChrome: Boolean get() = isDragging && !settling

    /** The list must not scroll while a drag runs (the scroll would steal the gesture). */
    val blocksScroll: Boolean get() = isDragging

    fun start(id: String, touchInRow: Offset, ids: List<String>) {
        draggingId = id
        orderedIds = ids
        touch = touchInRow
        pointerDeltaX = 0f
        pointerDeltaY = 0f
        scrollCompensationY = 0f
        deltaY = 0f
        hoveredChip = null
        settling = false
        settleToken++
    }

    fun dragBy(dx: Float, dy: Float) {
        if (settling) return
        pointerDeltaX += dx
        pointerDeltaY += dy
        publishDelta()
        val finger = finger() ?: return
        hoveredChip = chipBounds.entries.firstOrNull { it.value.contains(finger) }?.key
    }

    /** Pointer moved: [root] is the finger in root coordinates (scroll-stable). */
    fun dragTo(root: Offset) {
        if (settling) return
        val bounds = rowBounds[draggingId ?: return] ?: return
        pointerDeltaX = root.x - bounds.left - touch.x
        pointerDeltaY = root.y - bounds.top - touch.y
        publishDelta()
        hoveredChip = chipBounds.entries.firstOrNull { it.value.contains(root) }?.key
    }

    /**
     * The scrollable consumed [consumedPx] of programmatic auto-scroll (Compose: positive moves
     * content up). The dragged card's translation grows by the same amount so it stays under the finger.
     */
    fun onListScrolled(consumedPx: Float) {
        if (settling || draggingId == null || consumedPx == 0f) return
        scrollCompensationY += consumedPx
        publishDelta()
    }

    /** Finger is up: keep [draggingId] so chrome can fade, freeze the delta. */
    fun beginSettle() {
        if (draggingId == null || settling) return
        settling = true
        settleToken++
    }

    fun finish() {
        draggingId = null
        pointerDeltaX = 0f
        pointerDeltaY = 0f
        scrollCompensationY = 0f
        deltaY = 0f
        hoveredChip = null
        settling = false
        orderedIds = emptyList()
        settleToken++
    }

    fun clearRowBounds() {
        rowBounds.clear()
        gapHeightAfter.clear()
    }

    fun fingerY(): Float? = finger()?.y

    /** Row plus the travel gap under it: the slot other rows shift by while this one is held. */
    fun slotHeight(id: String): Float {
        val row = rowBounds[id] ?: return 0f
        return row.height + (gapHeightAfter[id] ?: 0f)
    }

    private fun publishDelta() {
        deltaY = pointerDeltaY + scrollCompensationY
    }

    private fun finger(): Offset? {
        val bounds = rowBounds[draggingId ?: return null] ?: return null
        return Offset(bounds.left + touch.x + pointerDeltaX, bounds.top + touch.y + pointerDeltaY)
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
