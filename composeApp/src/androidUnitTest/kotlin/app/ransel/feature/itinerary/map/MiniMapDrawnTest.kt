package app.ransel.feature.itinerary.map

import android.app.Application
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR #42 hid the MapView tree with [View.INVISIBLE] after collapse. Expand must restore the MapView and every
 * child (holders included, not only Surface/Texture leaves), alpha 1, and a11y, on every cycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MiniMapDrawnTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun expandRestoresEveryChildAfterCollapse() {
        val tree = mapTree()
        applyMiniMapDrawn(tree.root, drawn = false)
        assertHidden(tree)

        applyMiniMapDrawn(tree.root, drawn = true)
        assertShown(tree)
    }

    @Test
    fun severalCollapseExpandCyclesKeepTheMapVisible() {
        val tree = mapTree()
        repeat(4) {
            applyMiniMapDrawn(tree.root, drawn = false)
            assertHidden(tree)
            applyMiniMapDrawn(tree.root, drawn = true)
            assertShown(tree)
        }
    }

    @Test
    fun aQuickHideThenShowMidTreeStillRestoresNewChildren() {
        val tree = mapTree()
        applyMiniMapDrawn(tree.root, drawn = false)
        val late = SurfaceView(context)
        tree.holder.addView(late)
        applyMiniMapDrawn(tree.root, drawn = false)
        assertEquals(View.INVISIBLE, late.visibility)

        applyMiniMapDrawn(tree.root, drawn = true)
        assertShown(tree)
        assertEquals(View.VISIBLE, late.visibility)
        assertEquals(1f, late.alpha)
        assertTrue(late.isEnabled)
    }

    private fun mapTree(): MapTree {
        val root = FrameLayout(context)
        val holder = FrameLayout(context)
        val surface = SurfaceView(context)
        val texture = TextureView(context)
        holder.addView(surface)
        holder.addView(texture)
        root.addView(holder)
        return MapTree(root, holder, surface, texture)
    }

    private fun assertHidden(tree: MapTree) {
        listOf(tree.root, tree.holder, tree.surface, tree.texture).forEach { view ->
            assertEquals(View.INVISIBLE, view.visibility)
            assertEquals(0f, view.alpha)
            assertFalse(view.isEnabled)
        }
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, tree.root.importantForAccessibility)
    }

    private fun assertShown(tree: MapTree) {
        listOf(tree.root, tree.holder, tree.surface, tree.texture).forEach { view ->
            assertEquals(View.VISIBLE, view.visibility)
            assertEquals(1f, view.alpha)
            assertTrue(view.isEnabled)
        }
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, tree.root.importantForAccessibility)
    }

    private data class MapTree(
        val root: FrameLayout,
        val holder: FrameLayout,
        val surface: SurfaceView,
        val texture: TextureView,
    )
}
