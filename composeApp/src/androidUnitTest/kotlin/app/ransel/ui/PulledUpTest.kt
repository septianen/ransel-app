package app.ransel.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertTrue

/**
 * `pulledUp` is what a negative top padding was meant to be: Compose throws "Padding must be non-negative" for one, and the
 * mini map caption did, so the Itinerary tab crashed (16 Nov 2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class PulledUpTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun theElementKeepsItsSizeButTakesLessRoomBelowWhatIsAboveIt() {
        rule.setContent {
            Column(Modifier.testTag("column")) {
                Box(Modifier.size(100.dp, 20.dp))
                Box(Modifier.pulledUp(6.dp).size(100.dp, 20.dp)) // outermost: a size modifier outside it would put the room back
            }
        }
        rule.onNodeWithTag("column").assertHeightIsEqualTo(34.dp) // 20 + 20 - 6
    }

    @Test
    fun itCannotBePulledAboveItsOwnHeightAndNothingNegativeIsMeasured() {
        rule.setContent {
            Column(Modifier.testTag("column")) {
                Box(Modifier.size(100.dp, 20.dp))
                Box(Modifier.pulledUp(50.dp).size(100.dp, 4.dp))
            }
        }
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
    }

    /** The crash came from a literal negative padding: none may be written again in the app's own code. */
    @Test
    fun noScreenWritesANegativePadding() {
        val root = listOf(File("src"), File("composeApp/src")).first { it.exists() }
        val negative = Regex("""(?:padding|PaddingValues)[A-Za-z]*\s*\([^\n]*?(?:=|\(|,)\s*\(?\s*-\s*[0-9]""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/build/" !in it.path }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.trimStart()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) null
                    else if (negative.containsMatchIn(line)) "${file.path}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(offenders.isEmpty(), "negative padding crashes Compose; use Modifier.pulledUp or offset:\n" + offenders.joinToString("\n"))
    }
}
