package app.ransel.feature

import app.ransel.feature.trips.TripCardData
import app.ransel.feature.trips.TripListUiState
import app.ransel.feature.trips.matchesSearch
import app.ransel.feature.trips.searched
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripSummaryDto
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun trip(id: String, title: String, destination: String, start: LocalDate? = null, end: LocalDate? = null) = TripDto(
    id = id, ownerId = "u1", title = title, destination = destination, startDate = start, endDate = end, baseCurrency = "IDR",
    createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)

private fun card(trip: TripDto) = TripCardData(trip, TripSummaryDto(0, 0, 0, 0, 0, 0, 0, 0))

class TripSearchTest {
    private val bali = trip("t1", "Bali 4 hari", "Bali", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23))
    private val bogota = trip("t2", "Liburan keluarga", "Bogotá, Kolombia", LocalDate(2026, 6, 1), LocalDate(2026, 6, 7))
    private val undated = trip("t3", "Reuni kampus", "Yogyakarta")

    private val content = TripListUiState.Content(
        ongoing = listOf(card(bali)),
        upcoming = listOf(card(undated)),
        finished = listOf(card(bogota)),
        today = LocalDate(2026, 11, 21),
    )

    @Test
    fun matchesTitleOrDestinationIgnoringCase() {
        assertTrue(bali.matchesSearch("bali"))
        assertTrue(bali.matchesSearch("HARI"))
        assertTrue(undated.matchesSearch("yogya"))
        assertFalse(bali.matchesSearch("lombok"))
    }

    @Test
    fun accentsAreIgnoredBothWays() {
        assertTrue(bogota.matchesSearch("bogota"))
        assertTrue(bogota.matchesSearch("BOGOTÁ"))
        assertTrue(trip("x", "Café trip", "Paris").matchesSearch("cafe"))
        assertTrue(trip("x", "Cafe trip", "Paris").matchesSearch("café"))
    }

    @Test
    fun everyWordMustMatchInAnyOrder() {
        assertTrue(bogota.matchesSearch("  kolombia   keluarga "))
        assertFalse(bogota.matchesSearch("kolombia bali"))
    }

    @Test
    fun aBlankQueryKeepsEverything() {
        assertEquals(content, content.searched("   "))
        assertFalse(content.searched("").isSearching)
    }

    @Test
    fun filtersEveryGroupIncludingTripsWithoutDates() {
        val result = content.searched("reuni")
        assertEquals(emptyList(), result.ongoing)
        assertEquals(listOf("t3"), result.upcoming.map { it.trip.id })
        assertEquals(emptyList(), result.finished)
        assertTrue(result.isSearching)
        assertFalse(result.noResults)

        assertEquals(listOf("t2"), content.searched("bogota").finished.map { it.trip.id })
    }

    @Test
    fun nothingFoundIsNoResults() {
        val result = content.searched(" Lombok ")
        assertTrue(result.noResults)
        assertEquals("Lombok", result.query)
    }
}
