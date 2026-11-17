package app.ransel.feature

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.ransel.feature.itinerary.TravelGap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** While a card is dragged the capsules are hidden, but they keep their room: the rows must not jump. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class TravelGapDragTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aHiddenGapTakesTheSameRoomAsAVisibleOne() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column {
                Box(Modifier.testTag("shown")) { TravelGap(hidden = false) { Box(Modifier.size(120.dp, 32.dp)) } }
                Box(Modifier.testTag("hidden")) { TravelGap(hidden = true) { Box(Modifier.size(120.dp, 32.dp)) } }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val shown = rule.onNodeWithTag("shown").fetchSemanticsNode().size
        val hidden = rule.onNodeWithTag("hidden").fetchSemanticsNode().size
        assertEquals(shown, hidden)
    }
}
