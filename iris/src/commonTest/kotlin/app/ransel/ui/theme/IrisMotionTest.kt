package app.ransel.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IrisMotionTest {

    @Test
    fun durationTokensMatchMaterial3() {
        assertEquals(50, IrisMotion.Duration.Short1)
        assertEquals(100, IrisMotion.Duration.Short2)
        assertEquals(150, IrisMotion.Duration.Short3)
        assertEquals(200, IrisMotion.Duration.Short4)
        assertEquals(250, IrisMotion.Duration.Medium1)
        assertEquals(300, IrisMotion.Duration.Medium2)
        assertEquals(350, IrisMotion.Duration.Medium3)
        assertEquals(400, IrisMotion.Duration.Medium4)
        assertEquals(450, IrisMotion.Duration.Long1)
        assertEquals(500, IrisMotion.Duration.Long2)
        assertEquals(550, IrisMotion.Duration.Long3)
        assertEquals(600, IrisMotion.Duration.Long4)
        assertEquals(1200, IrisMotion.Duration.Shimmer)
    }

    @Test
    fun easingCurvesMatchMaterial3ControlPoints() {
        assertEasing(IrisMotion.Easing.Emphasized, 0.2f, 0f, 0f, 1f)
        assertEasing(IrisMotion.Easing.EmphasizedAccelerate, 0.3f, 0f, 0.8f, 0.15f)
        assertEasing(IrisMotion.Easing.EmphasizedDecelerate, 0.05f, 0.7f, 0.1f, 1f)
        assertEasing(IrisMotion.Easing.Standard, 0.2f, 0f, 0f, 1f)
        assertEasing(IrisMotion.Easing.StandardAccelerate, 0.3f, 0f, 1f, 1f)
        assertEasing(IrisMotion.Easing.StandardDecelerate, 0f, 0f, 0f, 1f)
    }

    @Test
    fun springTokensMatchSpec() {
        assertEquals(0.6f, IrisMotion.Spring.FastSpatial.dampingRatio)
        assertEquals(800f, IrisMotion.Spring.FastSpatial.stiffness)
        assertEquals(0.8f, IrisMotion.Spring.DefaultSpatial.dampingRatio)
        assertEquals(380f, IrisMotion.Spring.DefaultSpatial.stiffness)
        assertEquals(0.8f, IrisMotion.Spring.SlowSpatial.dampingRatio)
        assertEquals(200f, IrisMotion.Spring.SlowSpatial.stiffness)
        assertEquals(1.0f, IrisMotion.Spring.Effects.dampingRatio)
        assertEquals(1600f, IrisMotion.Spring.Effects.stiffness)
    }

    @Test
    fun reduceMotionFallsBackToInstantTweenStaticShimmerAndCrossfade() {
        assertEquals(0, IrisMotion.durationMillis(IrisMotion.Duration.Medium2, reduceMotion = true))
        assertEquals(IrisMotion.Duration.Medium2, IrisMotion.durationMillis(IrisMotion.Duration.Medium2, reduceMotion = false))
        assertEquals(0, IrisMotion.shimmerDurationMillis(reduceMotion = true))
        assertEquals(IrisMotion.Duration.Shimmer, IrisMotion.shimmerDurationMillis(reduceMotion = false))
        assertTrue(IrisMotion.usesCrossfadeOnly(reduceMotion = true))
        assertFalse(IrisMotion.usesCrossfadeOnly(reduceMotion = false))

        val reducedTween = IrisMotion.tweenSpec<Float>(IrisMotion.Duration.Long4, reduceMotion = true)
        assertEquals(0, reducedTween.durationMillis)
        val fullTween = IrisMotion.tweenSpec<Float>(IrisMotion.Duration.Medium2, reduceMotion = false)
        assertEquals(IrisMotion.Duration.Medium2, fullTween.durationMillis)

        val reducedFade = IrisMotion.crossfadeSpec<Float>(reduceMotion = true)
        assertEquals(IrisMotion.Duration.Short2, reducedFade.durationMillis)
        val fullFade = IrisMotion.crossfadeSpec<Float>(reduceMotion = false)
        assertEquals(IrisMotion.Duration.Medium2, fullFade.durationMillis)

        assertTrue(IrisMotion.springSpec<Float>(IrisMotion.Spring.DefaultSpatial, reduceMotion = true).isReducedMotionSnap())
        assertTrue(IrisMotion.springSpec<Float>(IrisMotion.Spring.FastSpatial, reduceMotion = false).isSpatialSpring())
    }

    private fun assertEasing(
        easing: androidx.compose.animation.core.Easing,
        a: Float,
        b: Float,
        c: Float,
        d: Float,
    ) {
        val expected = CubicBezierEasing(a, b, c, d)
        assertEquals(0f, easing.transform(0f))
        assertEquals(1f, easing.transform(1f))
        listOf(0.25f, 0.5f, 0.75f).forEach { t ->
            assertEquals(expected.transform(t), easing.transform(t), absoluteTolerance = 0.0001f)
        }
    }
}
