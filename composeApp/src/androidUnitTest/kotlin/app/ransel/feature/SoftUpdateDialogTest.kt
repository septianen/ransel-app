package app.ransel.feature

import android.app.Application
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ransel.feature.update.UpdateSnooze
import app.ransel.feature.update.visibleUpdate
import app.ransel.shared.dto.AppUpdateDto
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.ui.components.SoftUpdateDialog
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Nanti, the scrim and Back write [UpdateSnooze]. That value is snapshot state, so the dialog leaves immediately.
 * Setting the content again is a new composition in the same process, and the dialog stays gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class SoftUpdateDialogTest {
    @get:Rule val rule = createComposeRule()

    private val offer = AppUpdateDto(
        status = AppUpdateStatus.OPTIONAL,
        latestVersion = "1.2.0",
        storeUrl = "https://play.google.com/store/apps/details?id=app.ransel",
        minVersion = "1.0.0",
    )

    private lateinit var host: ComposeView

    @Before fun show() {
        UpdateSnooze.latestVersion = null
        host = rule.composeHost()
        compose()
        assertOpen()
    }

    @After fun resetSnooze() {
        UpdateSnooze.latestVersion = null
    }

    @Test
    fun nantiClosesTheDialogAndARecreatedScreenStaysClosed() {
        rule.onNode(hasText("Later") or hasText("Nanti")).performClick()
        assertClosed()
        compose()
        assertClosed()
    }

    @Test
    fun tapOutsideClosesTheDialogAndARecreatedScreenStaysClosed() {
        rule.runOnIdle { tapOutside(latestDialog()) }
        assertClosed()
        compose()
        assertClosed()
    }

    @Test
    fun backClosesTheDialogAndARecreatedScreenStaysClosed() {
        rule.runOnIdle { latestDialog().onBackPressedDispatcher.onBackPressed() }
        assertClosed()
        compose()
        assertClosed()
    }

    /** A new composition of the same offer. Reads [UpdateSnooze] the way [app.ransel.feature.update.AppUpdatePrompt] does. */
    private fun compose() {
        rule.runOnIdle {
            host.setContent {
                IrisTheme(mode = ThemeMode.LIGHT) {
                    val status = visibleUpdate(offer, UpdateSnooze.latestVersion)
                    if (status == AppUpdateStatus.OPTIONAL) {
                        SoftUpdateDialog(
                            versionName = "1.2.0",
                            currentVersion = "1.0.0",
                            message = null,
                            opening = false,
                            storeError = false,
                            onUpdate = {},
                            onLater = { UpdateSnooze.latestVersion = "1.2.0" },
                            onReturned = {},
                        )
                    }
                }
            }
        }
    }

    private fun assertOpen() {
        rule.onNodeWithText("Update available").assertExists()
    }

    private fun assertClosed() {
        rule.onNodeWithText("Update available").assertDoesNotExist()
        rule.onNodeWithText("Update tersedia").assertDoesNotExist()
    }

    private fun latestDialog(): ComponentDialog =
        ShadowDialog.getLatestDialog() as ComponentDialog

    /** A press in the window, outside the dialog content. [ComponentDialog.onTouchEvent] treats that as the scrim. */
    private fun tapOutside(dialog: ComponentDialog) {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, -1f, -1f, 0)
        val up = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_UP, -1f, -1f, 0)
        try {
            dialog.onTouchEvent(down)
            dialog.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }
}

/** The activity the compose rule launched already holds a [ComposeView]; use that, or add one. */
@Suppress("UNCHECKED_CAST")
private fun ComposeContentTestRule.composeHost(): ComposeView {
    val activity = (this as AndroidComposeTestRule<*, ComponentActivity>).activity
    val content = activity.findViewById<ViewGroup>(android.R.id.content)
    val existing = (0 until content.childCount).firstNotNullOfOrNull { content.getChildAt(it) as? ComposeView }
    if (existing != null) return existing
    return ComposeView(activity).also { activity.setContentView(it) }
}
