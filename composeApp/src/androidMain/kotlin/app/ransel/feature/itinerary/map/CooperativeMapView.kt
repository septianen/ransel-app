package app.ransel.feature.itinerary.map

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import kotlin.math.hypot

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
    private var drawn: Boolean = true

    /**
     * Compose clip and alpha do not hide a SurfaceView. After collapse settles, keep this MapView in the tree (so
     * expand does not reload tiles) but stop it painting and taking touches.
     */
    fun setDrawn(drawn: Boolean) {
        this.drawn = drawn
        applyDrawn(this, drawn)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (!drawn) applyDrawn(this, drawn = false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!drawn) applyDrawn(this, drawn = false)
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

private fun applyDrawn(root: View, drawn: Boolean) {
    if (drawn) {
        root.visibility = View.VISIBLE
        root.isEnabled = true
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        visitPlatformLayers(root) { layer ->
            layer.visibility = View.VISIBLE
            layer.isEnabled = true
        }
        return
    }
    root.isEnabled = false
    root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    fun hide(view: View) {
        view.visibility = View.INVISIBLE
        view.isEnabled = false
        if (view is SurfaceView) view.setZOrderOnTop(false)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) hide(view.getChildAt(i))
        }
    }
    hide(root)
}

private fun visitPlatformLayers(view: View, action: (View) -> Unit) {
    if (view is SurfaceView || view is TextureView) action(view)
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) visitPlatformLayers(view.getChildAt(i), action)
    }
}
