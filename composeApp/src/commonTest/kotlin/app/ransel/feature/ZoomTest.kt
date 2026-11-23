package app.ransel.feature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.ransel.feature.documents.viewer.DOUBLE_TAP_ZOOM
import app.ransel.feature.documents.viewer.MAX_ZOOM
import app.ransel.feature.documents.viewer.Zoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The maths of the photo viewer (spec "Viewer foto layar penuh"). */
class ZoomTest {
    private val box = Size(1000f, 2000f)
    // a landscape photo fitted into a portrait box: full width, 500 high
    private val fitted = Zoom.fitted(box, Size(4000f, 2000f))

    @Test
    fun theFittedPhotoKeepsItsRatioInsideTheBox() {
        assertEquals(Size(1000f, 500f), fitted)
        assertEquals(Size(1000f, 2000f), Zoom.fitted(box, Size(500f, 1000f)))
    }

    @Test
    fun aPinchStaysBetweenOneAndFiveTimes() {
        val (s, _) = Zoom.pinch(1f, Offset.Zero, 20f, Offset.Zero, Offset.Zero, box, fitted)
        assertEquals(MAX_ZOOM, s)
        val (low, _) = Zoom.pinch(1f, Offset.Zero, 0.1f, Offset.Zero, Offset.Zero, box, fitted)
        assertTrue(low < 1f && low > 0f, "it may dip under 1x while the fingers are down; the viewer snaps back on release")
    }

    @Test
    fun theTapPointStaysUnderTheFingerWhileZooming() {
        val tap = Offset(300f, 100f)
        val (s, o) = Zoom.doubleTap(1f, tap, Size(1000f, 2000f), Size(1000f, 2000f))
        assertEquals(DOUBLE_TAP_ZOOM, s)
        // screen position of the tapped photo point = centre-relative point * scale + offset
        assertEquals(tap.x, tap.x * s + o.x, 0.001f)
        assertEquals(tap.y, tap.y * s + o.y, 0.001f)
    }

    @Test
    fun doubleTapTogglesBackToOneTimes() {
        val (s, o) = Zoom.doubleTap(2.5f, Offset(10f, 10f), box, fitted)
        assertEquals(1f, s)
        assertEquals(Offset.Zero, o)
    }

    @Test
    fun panningStopsAtTheEdgesOfThePhoto() {
        // 2x of a 1000x500 photo in a 1000x2000 box: it overflows sideways by 500 in total, not at all vertically
        val clamped = Zoom.clampOffset(Offset(900f, 900f), 2f, box, fitted)
        assertEquals(Offset(500f, 0f), clamped)
        assertEquals(Offset.Zero, Zoom.clampOffset(Offset(50f, 50f), 1f, box, Size(1000f, 2000f)))
    }

    @Test
    fun theBadgeShowsTheZoomWithTheLocalDecimalSeparator() {
        assertEquals("2,5×", Zoom.label(2.5f, ','))
        assertEquals("2.5×", Zoom.label(2.5f, '.'))
        assertEquals("3×", Zoom.label(3f, ','))
    }
}
