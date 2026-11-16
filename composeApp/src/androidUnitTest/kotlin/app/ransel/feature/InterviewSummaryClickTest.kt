package app.ransel.feature

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.ransel.feature.interview.DepartureSummaryRow
import app.ransel.feature.interview.InterviewUiState
import app.ransel.shared.domain.InterviewAnswers
import app.ransel.shared.domain.InterviewCityFacts
import app.ransel.shared.domain.InterviewDestination
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.InterviewStep
import app.ransel.shared.domain.withDeparture
import app.ransel.shared.domain.withDestination
import app.ransel.shared.domain.withOrigin
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.model.DepartureMode
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The summary's "Leaving" card used to put click only on the origin and mode texts, so the label, chevron and
 * padding did nothing while Destination / Dates / Taste were whole-row buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class InterviewSummaryClickTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun tappingTheLeavingCardOpensHowToGetThere() {
        val facts = InterviewCityFacts()
        val answers = InterviewAnswers()
            .withDestination(InterviewDestination("Bandung", "p-bdg", -6.9175, 107.6191, "ID"), facts)
            .withOrigin(TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID"), facts)
            .withDeparture(DepartureMode.OWN_MOTORCYCLE, facts)
        val state = InterviewUiState(
            door = InterviewDoor.MY_TRIPS,
            answers = answers,
            screen = app.ransel.feature.interview.InterviewScreen.Summary,
        )
        var edited: InterviewStep? = null
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                DepartureSummaryRow(
                    state = state,
                    filled = true,
                    onEdit = { edited = it },
                    modifier = Modifier.testTag("interview_summary_leaving"),
                )
            }
        }
        rule.onNodeWithTag("interview_summary_leaving").assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(InterviewStep.DEPARTURE, edited) }
    }
}
