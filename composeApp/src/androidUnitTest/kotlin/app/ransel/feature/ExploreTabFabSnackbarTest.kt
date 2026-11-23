package app.ransel.feature

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.ransel.feature.inspiration.ExploreTabScaffold
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The Explore saved-ideas FAB used to share a Box with the snackbar host, so it stayed put over Undo.
 * Both slots belong to one Scaffold so the FAB lifts for the snackbar and settles after dismiss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class ExploreTabFabSnackbarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun askAiFabLiftsAboveUnsaveSnackbarAndReturns() {
        var showSnackbar by mutableStateOf(false)
        lateinit var snack: SnackbarHostState
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                snack = remember { SnackbarHostState() }
                LaunchedEffect(showSnackbar) {
                    if (showSnackbar) {
                        snack.showSnackbar(
                            message = "Removed from Saved ideas · Undo",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
                ExploreTabScaffold(snackbarHost = snack, showFab = true, onAskAi = {}) {
                    Text("saved ideas", Modifier.fillMaxSize())
                }
            }
        }

        val restTop = rule.onNodeWithContentDescription("Ask AI").getBoundsInRoot().top
        rule.runOnIdle { showSnackbar = true }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Removed from Saved ideas · Undo").fetchSemanticsNodes().isNotEmpty()
        }
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()

        val fab = rule.onNodeWithContentDescription("Ask AI").getBoundsInRoot()
        val snackbar = rule.onNodeWithText("Removed from Saved ideas · Undo").getBoundsInRoot()
        assertTrue(fab.top < restTop, "FAB should move up; rest=$restTop lifted=${fab.top}")
        assertTrue(
            fab.bottom <= snackbar.top,
            "FAB bottom=${fab.bottom} must sit above snackbar top=${snackbar.top}",
        )

        rule.runOnIdle { snack.currentSnackbarData?.dismiss() }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Removed from Saved ideas · Undo").fetchSemanticsNodes().isEmpty()
        }
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        val settled = rule.onNodeWithContentDescription("Ask AI").getBoundsInRoot().top
        assertTrue(abs((settled - restTop).value) < 1f, "FAB should settle; rest=$restTop settled=$settled")
    }
}
