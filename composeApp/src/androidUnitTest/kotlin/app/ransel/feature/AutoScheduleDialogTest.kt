package app.ransel.feature

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ransel.feature.itinerary.AutoPlan
import app.ransel.feature.itinerary.AutoScheduleDialog
import app.ransel.shared.domain.AutoScheduleResult
import app.ransel.shared.domain.AutoScheduled
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class AutoScheduleDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun scheduleIsDisabledWhenNoPlaceFitsAndCancelStillWorks() {
        var confirmed = false
        var dismissed = false
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                AutoScheduleDialog(
                    plan = AutoPlan(AutoScheduleResult(emptyList(), listOf("a")), emptyList()),
                    running = false,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        rule.onNodeWithText("No place fits in the days that are left. Add days or remove activities first.").assertExists()
        rule.onNodeWithText("Schedule").assertIsNotEnabled()
        rule.onNodeWithText("Schedule").performClick()
        assertFalse(confirmed)

        rule.onNodeWithText("Cancel").assertIsEnabled()
        rule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
    }

    @Test
    fun scheduleStaysEnabledWhenAPlaceFits() {
        var confirmed = false
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                AutoScheduleDialog(
                    plan = AutoPlan(
                        AutoScheduleResult(
                            scheduled = listOf(
                                AutoScheduled("a", LocalDate(2099, 11, 20), LocalTime(9, 0), LocalTime(10, 30), 1.0),
                            ),
                            leftOver = emptyList(),
                        ),
                        dayNumbers = listOf(1),
                    ),
                    running = false,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Schedule").assertIsEnabled()
        rule.onNodeWithText("Schedule").performClick()
        assertEquals(true, confirmed)
    }
}
