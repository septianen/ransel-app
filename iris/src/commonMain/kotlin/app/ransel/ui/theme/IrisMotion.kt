package app.ransel.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.animation.core.Easing as ComposeEasing

/**
 * IRIS motion tokens. Names match Material 3 (`Short1`–`Long4`) plus a shimmer loop.
 * Spatial movement uses springs; fades and timed tweens use the duration + easing pairs.
 *
 * Reduce motion (system animator scale 0 / accessibility, or a debug override) drops spatial
 * animation: tweens become instant, springs snap, shimmer does not loop, and content changes
 * crossfade only.
 */
object IrisMotion {

    /** Milliseconds. M3 duration tokens 50–600, plus the skeleton shimmer loop. */
    object Duration {
        const val Short1 = 50
        const val Short2 = 100
        const val Short3 = 150
        const val Short4 = 200
        const val Medium1 = 250
        const val Medium2 = 300
        const val Medium3 = 350
        const val Medium4 = 400
        const val Long1 = 450
        const val Long2 = 500
        const val Long3 = 550
        const val Long4 = 600
        /** Skeleton shine sweep; still (no loop) when motion is reduced. */
        const val Shimmer = 1200
    }

    /** Material 3 emphasized and standard curves (and their accelerate / decelerate variants). */
    object Easing {
        val Emphasized: ComposeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val EmphasizedAccelerate: ComposeEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val EmphasizedDecelerate: ComposeEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val Standard: ComposeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val StandardAccelerate: ComposeEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
        val StandardDecelerate: ComposeEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
    }

    object Spring {
        val FastSpatial = IrisSpring(dampingRatio = 0.6f, stiffness = 800f)
        val DefaultSpatial = IrisSpring(dampingRatio = 0.8f, stiffness = 380f)
        val SlowSpatial = IrisSpring(dampingRatio = 0.8f, stiffness = 200f)
        val Effects = IrisSpring(dampingRatio = 1.0f, stiffness = 1600f)
    }

    /** Timed tween length; 0 when motion is reduced so the change is instant. */
    fun durationMillis(tokenMs: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else tokenMs

    /** Shimmer loop length; 0 means a static placeholder (no shine). */
    fun shimmerDurationMillis(reduceMotion: Boolean): Int = if (reduceMotion) 0 else Duration.Shimmer

    /** Shared-element and other spatial moves become a crossfade only. */
    fun usesCrossfadeOnly(reduceMotion: Boolean): Boolean = reduceMotion

    fun <T> tweenSpec(
        durationMs: Int,
        easing: ComposeEasing = IrisMotion.Easing.Standard,
        reduceMotion: Boolean,
    ): TweenSpec<T> = tween(
        durationMillis = durationMillis(durationMs, reduceMotion),
        easing = if (reduceMotion) LinearEasing else easing,
    )

    /**
     * Fade used when [usesCrossfadeOnly] is true (or as the non-spatial part of a content swap).
     * Reduced motion still fades briefly so the swap is not a hard cut.
     */
    fun <T> crossfadeSpec(reduceMotion: Boolean): TweenSpec<T> = tween(
        durationMillis = if (reduceMotion) Duration.Short2 else Duration.Medium2,
        easing = if (reduceMotion) LinearEasing else Easing.Emphasized,
    )

    fun <T> springSpec(token: IrisSpring, reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = token.dampingRatio, stiffness = token.stiffness)
}

@Immutable
data class IrisSpring(
    val dampingRatio: Float,
    val stiffness: Float,
)

/** True when [spec] is a snap (reduced-motion spring fallback), not a spatial spring. */
fun FiniteAnimationSpec<*>.isReducedMotionSnap(): Boolean = this is SnapSpec<*>

/** True when [spec] is a spatial spring (not reduced). */
fun FiniteAnimationSpec<*>.isSpatialSpring(): Boolean = this is SpringSpec<*>
