package app.ransel.feature

import app.ransel.feature.itinerary.sortOrderForStart
import app.ransel.shared.dto.ItineraryItemDto
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DayOrderingTest {
    private val stamp = Instant.fromEpochMilliseconds(0)
    private fun item(id: String, start: String?, order: Double) = ItineraryItemDto(
        id = id, tripId = "t", title = id, startTime = start?.let(LocalTime::parse),
        sortOrder = order, createdAt = stamp, updatedAt = stamp,
    )

    private val day = listOf(item("a", "10:00", 1.0), item("b", "13:00", 2.0))

    @Test fun earlierStartGoesFirst() {
        assertTrue(sortOrderForStart(day, LocalTime(9, 0)) < 1.0)
    }

    @Test fun middleStartGoesBetween() {
        assertEquals(1.5, sortOrderForStart(day, LocalTime(11, 0)))
    }

    @Test fun laterStartGoesLast() {
        assertEquals(3.0, sortOrderForStart(day, LocalTime(15, 0)))
    }

    @Test fun noTimeGoesLast() {
        assertEquals(3.0, sortOrderForStart(day, null))
    }

    @Test fun emptyDayStartsAtOne() {
        assertEquals(1.0, sortOrderForStart(emptyList(), LocalTime(9, 0)))
    }

    @Test fun equalStartGoesAfterTheExistingOne() {
        assertEquals(1.5, sortOrderForStart(day, LocalTime(10, 0)))
    }
}
