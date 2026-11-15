package app.ransel.feature.documents.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/** Zoom limits of the photo viewer (spec "Viewer foto layar penuh"): pinch 1×–5×, double tap toggles 1× and 2.5×. */
const val MIN_ZOOM = 1f
const val MAX_ZOOM = 5f
const val DOUBLE_TAP_ZOOM = 2.5f

/** The pure maths of the viewer, so a test can check it without a screen. [offset] is the translation from the centred, fitted photo. */
object Zoom {
    /** The size the photo takes inside [box] with ContentScale.Fit. */
    fun fitted(box: Size, photo: Size): Size {
        if (box.width <= 0f || box.height <= 0f || photo.width <= 0f || photo.height <= 0f) return box
        val scale = minOf(box.width / photo.width, box.height / photo.height)
        return Size(photo.width * scale, photo.height * scale)
    }

    /** The photo cannot be panned past its edges: at most half of what sticks out of [box] on each side. */
    fun clampOffset(offset: Offset, scale: Float, box: Size, fitted: Size): Offset {
        val maxX = max(0f, (fitted.width * scale - box.width) / 2f)
        val maxY = max(0f, (fitted.height * scale - box.height) / 2f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /** A pinch of factor [zoom] around [centroid] (relative to the box centre) plus a pan: the point under the fingers stays put. */
    fun pinch(scale: Float, offset: Offset, zoom: Float, centroid: Offset, pan: Offset, box: Size, fitted: Size): Pair<Float, Offset> {
        // the scale may dip below 1× while pinching (the spec snaps it back on release) but never to nothing
        val next = (scale * zoom).coerceIn(MIN_ZOOM * 0.8f, MAX_ZOOM)
        val factor = next / scale
        val moved = (offset * factor) + centroid * (1f - factor) + pan
        return next to clampOffset(moved, next.coerceAtLeast(MIN_ZOOM), box, fitted)
    }

    /** Double tap at [tap] (relative to the box centre): from 1× to 2.5× around the tap, from any zoom above 1× back to 1×. */
    fun doubleTap(scale: Float, tap: Offset, box: Size, fitted: Size): Pair<Float, Offset> =
        if (scale > MIN_ZOOM + 0.01f) MIN_ZOOM to Offset.Zero
        else DOUBLE_TAP_ZOOM to clampOffset(tap * (1f - DOUBLE_TAP_ZOOM), DOUBLE_TAP_ZOOM, box, fitted)

    /** The label of the zoom badge: "2,5×" in Indonesian style needs the locale, so the caller passes the decimal separator. */
    fun label(scale: Float, decimal: Char): String {
        val tenths = (scale * 10f + 0.5f).toInt()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) "$whole×" else "$whole$decimal$frac×"
    }
}
