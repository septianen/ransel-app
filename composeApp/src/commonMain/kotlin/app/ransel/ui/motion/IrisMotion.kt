package app.ransel.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens used by the app (M3 spatial / duration names). IrisMotion is not in the iris module on
 * develop yet, so these values live here and match DefaultSpatial, Short3, Short4 EmphasizedDecelerate
 * and Medium2 Emphasized.
 */
object IrisMotion {
    const val Short3Ms = 150
    const val Short4Ms = 200
    const val Medium2Ms = 300

    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> short3(): TweenSpec<T> = tween(Short3Ms)

    fun <T> short4Decelerate(): TweenSpec<T> = tween(Short4Ms, easing = EmphasizedDecelerate)

    fun <T> medium2(): TweenSpec<T> = tween(Medium2Ms, easing = Emphasized)
}
