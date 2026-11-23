package app.ransel.feature

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.ransel.feature.inspiration.CardDetailContent
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.ui.theme.IrisTheme
import app.ransel.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en")
class CardDetailContentTest {
    @get:Rule val rule = createComposeRule()

    private val card = InspirationCardDto(
        id = "idea-1",
        activity = "Park walk",
        region = "Khao Yai, Thailand",
        why = "Matches your interests",
    )

    @Test
    fun savedIdeaDetailOmitsSaveActionButKeepsCreateTrip() {
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                CardDetailContent(card, showSaveButton = false)
            }
        }

        rule.onNodeWithText("Save").assertDoesNotExist()
        rule.onNodeWithText("Create a trip from here").assertExists()
        rule.onNodeWithText("Add to existing trip").assertDoesNotExist()
    }

    @Test
    fun addToExistingTripShowsOnlyWhenAsked() {
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                CardDetailContent(card, showSaveButton = false, showAddToExistingTrip = true)
            }
        }
        rule.onNodeWithText("Add to existing trip").assertExists()
        rule.onNodeWithText("Create a trip from here").assertExists()
    }

    @Test
    fun unsavedIdeaDetailStillOffersSaveAction() {
        rule.setContent {
            IrisTheme(ThemeMode.LIGHT) {
                CardDetailContent(card)
            }
        }

        rule.onNodeWithText("Save").assertExists()
    }
}
