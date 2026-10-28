package app.ransel.shared.domain

import app.ransel.shared.dto.TripTemplateDto
import app.ransel.shared.dto.TripTemplateItem
import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripWithoutDatesTest {
    private val nov20 = LocalDate(2026, 11, 20)
    private val nov23 = LocalDate(2026, 11, 23)

    @Test
    fun aTripHasBothDatesOrNeither() {
        assertEquals(emptyMap(), validateTrip("Bali", null, null, "IDR", null))
        assertEquals(emptyMap(), validateTrip("Bali", nov20, nov23, "IDR", null))
        assertEquals(mapOf("endDate" to "REQUIRED"), validateTrip("Bali", nov20, null, "IDR", null))
        assertEquals(mapOf("startDate" to "REQUIRED"), validateTrip("Bali", null, nov23, "IDR", null))
        assertEquals(mapOf("endDate" to "MUST_BE_AFTER_START"), validateTrip("Bali", nov23, nov20, "IDR", null))
    }

    @Test
    fun aTripWithoutDatesHasNoDaysToScheduleOn() {
        assertTrue(dayFitsTrip(null, null, null))
        assertFalse(dayFitsTrip(nov20, null, null))
        assertTrue(dayFitsTrip(nov20, nov20, nov23))
        assertFalse(dayFitsTrip(LocalDate(2026, 11, 24), nov20, nov23))
    }

    @Test
    fun noRecapWithoutDates() {
        assertFalse(recapAvailable(null, LocalDate(2027, 1, 1)))
        assertTrue(recapAvailable(nov23, LocalDate(2026, 11, 24)))
    }

    @Test
    fun aTemplateOnATripWithoutDatesIsAllUnscheduled() {
        val now = Instant.parse("2026-10-28T00:00:00Z")
        val template = TripTemplateDto(
            id = "t", name = "Bali", dayCount = 2, destination = "Bali",
            items = listOf(
                TripTemplateItem(dayIndex = 0, title = "Pantai", category = Category.ATTRACTION),
                TripTemplateItem(dayIndex = null, title = "Pura", category = Category.ATTRACTION),
            ),
            createdAt = now,
        )
        var n = 0
        val start = startFromTemplate(template, null, null) { "id${n++}" }
        assertTrue(start.items.all { it.dayDate == null && it.startTime == null })
        assertEquals(2, start.items.size)
    }
}
