package app.ransel.shared.domain

import app.ransel.shared.dto.TripTasteDto
import app.ransel.shared.model.Interest
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripTasteTest {
    @Test
    fun anEmptyTasteIsValid() {
        assertTrue(validateTripTaste(TripTasteDto()).isEmpty())
    }

    @Test
    fun khasChipsAndNotesHaveTheRequestLimits() {
        assertEquals("TOO_MANY", validateTripTaste(TripTasteDto(extraInterests = (1..6).map { "Chip $it" }))["extraInterests"])
        assertEquals("TOO_LONG", validateTripTaste(TripTasteDto(extraInterests = listOf("x".repeat(41))))["extraInterests"])
        assertEquals("TOO_LONG", validateTripTaste(TripTasteDto(notes = "x".repeat(501)))["notes"])
        // blanks and duplicates do not count
        assertTrue(validateTripTaste(TripTasteDto(extraInterests = listOf("Pantai", " Pantai ", "", "Snorkeling", "A", "B", "C"))).isEmpty())
    }

    @Test
    fun theSheetAndTheServerShapeRoundTrip() {
        val sheet = InterviewTaste(
            interests = setOf(Interest.ADVENTURE, Interest.SHOPPING),
            extraInterests = setOf("upscale mall hopping"),
            style = TravelStyle.SANTAI,
            priceLevel = PriceLevel.MEDIUM,
            notes = "  dekat stasiun  ",
        )
        val dto = sheet.toTripTaste()
        assertEquals("dekat stasiun", dto.notes)
        assertEquals(sheet.copy(notes = "dekat stasiun"), dto.toInterviewTaste())
        assertEquals(null, InterviewTaste(notes = "   ").toTripTaste().notes)
    }
}
