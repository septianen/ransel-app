package app.ransel.feature.itinerary.map

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.hypot

internal const val MINI_MAP_LOG = "RanselMiniMap"

/**
 * Google Maps has no "two fingers on the map, one finger on the page" setting. This view lets the parent
 * scroll on one pointer and reports two-pointer moves so the Compose camera can pan and zoom.
 */
internal class CooperativeMapView(
    context: Context,
    options: GoogleMapOptions,
) : MapView(context, options) {
    /** When true, one finger is for the page; when false, one-finger drag pans the map (full screen). */
    var twoFingerOnly: Boolean = true

    var onTwoFingerDelta: (
        (
        midX: Float,
        midY: Float,
        prevMidX: Float,
        prevMidY: Float,
        span: Float,
        prevSpan: Float,
    ) -> Unit
    )? = null

    private var prevMidX = 0f
    private var prevMidY = 0f
    private var prevSpan = 0f
    private var tracking = false
    /** Start visible so the first layout can attach a surface. Hide only after collapse settles. */
    private var drawn: Boolean = true
    private val hiddenByUs: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    val isDrawn: Boolean get() = drawn

    /**
     * Compose clip and alpha do not hide a SurfaceView. After collapse settles, keep this MapView in the tree (so
     * expand does not reload tiles) but stop it painting and taking touches.
     *
     * Only views this call hid are restored. Walking the tree and forcing every child VISIBLE (PR #43) also un-hides
     * the Maps SDK's GONE grey-'!' placeholder, so the strip looks like a failed map even when tiles are fine.
     */
    fun setDrawn(drawn: Boolean) {
        val changed = this.drawn != drawn
        this.drawn = drawn
        applyMiniMapDrawn(this, drawn, hiddenByUs)
        Log.i(
            MINI_MAP_LOG,
            "setDrawn($drawn) changed=$changed size=${width}x$height children=$childCount hidden=${hiddenByUs.size}",
        )
        if (drawn) {
            // MapView may add Surface/Texture children after this call; show again on the next frame.
            post {
                if (this.drawn) {
                    applyMiniMapDrawn(this, drawn = true, hiddenByUs)
                    invalidate()
                    requestLayout()
                }
            }
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (!drawn) applyMiniMapDrawn(this, drawn = false, hiddenByUs)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Re-hide only. A show pass here would stomp SDK GONE/INVISIBLE children on every layout (the first
        // composition expanded path).
        if (!drawn) applyMiniMapDrawn(this, drawn = false, hiddenByUs)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!drawn) return false
        if (!twoFingerOnly) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return super.dispatchTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    rememberTwo(ev)
                    tracking = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && ev.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val midX = (ev.getX(0) + ev.getX(1)) / 2f
                    val midY = (ev.getY(0) + ev.getY(1)) / 2f
                    val span = hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1))
                    if (prevSpan > 1f && span > 1f) {
                        onTwoFingerDelta?.invoke(midX, midY, prevMidX, prevMidY, span, prevSpan)
                    }
                    prevMidX = midX
                    prevMidY = midY
                    prevSpan = span
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // pointerCount still includes the pointer that is leaving
                if (ev.pointerCount <= 2) {
                    tracking = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    rememberTwo(ev)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        if (ev.pointerCount < 2) parent?.requestDisallowInterceptTouchEvent(false)
        return super.dispatchTouchEvent(ev)
    }

    private fun rememberTwo(ev: MotionEvent) {
        if (ev.pointerCount < 2) return
        prevMidX = (ev.getX(0) + ev.getX(1)) / 2f
        prevMidY = (ev.getY(0) + ev.getY(1)) / 2f
        prevSpan = hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1))
    }
}

/**
 * Hide or show [root] for the mini map. Collapse records which descendants were VISIBLE and sets them INVISIBLE
 * (SurfaceView must be INVISIBLE or it paints through the 48 dp bar). Expand restores only those views, so a GONE
 * Maps SDK placeholder stays GONE.
 */
internal fun applyMiniMapDrawn(root: View, drawn: Boolean, hiddenByUs: MutableSet<View>) {
    if (drawn) {
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        fun show(view: View) {
            if (hiddenByUs.remove(view)) {
                view.visibility = View.VISIBLE
                view.alpha = 1f
                view.isEnabled = true
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) show(view.getChildAt(i))
            }
        }
        show(root)
        if (root.visibility != View.VISIBLE) {
            hiddenByUs.remove(root)
            root.visibility = View.VISIBLE
            root.alpha = 1f
            root.isEnabled = true
        }
        return
    }
    root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    fun hide(view: View) {
        if (view.visibility == View.VISIBLE) {
            hiddenByUs.add(view)
            view.visibility = View.INVISIBLE
            view.alpha = 0f
            view.isEnabled = false
            if (view is SurfaceView) view.setZOrderOnTop(false)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) hide(view.getChildAt(i))
        }
    }
    hide(root)
}
