package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.TemplateItem
import app.ransel.shared.dto.TripTemplateDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.PackingCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val at = Instant.parse("2026-11-21T03:00:00Z")
private val start = LocalDate(2026, 11, 20)
private val end = LocalDate(2026, 11, 23)

private fun item(id: String, day: LocalDate?, place: String?, source: ItemSource = ItemSource.MANUAL, time: String = "09:00") = ItineraryItemDto(
    id = id, tripId = "t", dayDate = day, startTime = LocalTime.parse(time), title = place ?: "Istirahat", placeName = place,
    source = source, createdAt = at, updatedAt = at,
)

private fun expense(id: String, amount: Long, splits: List<String>, estimate: Boolean = false) = ExpenseDto(
    id = id, tripId = "t", amountMinor = amount, currency = "IDR", category = Category.FOOD, spentAt = at, paidByMemberId = splits.first(),
    splits = splits.map { ExpenseSplitDto(it, amount / splits.size) }, isEstimate = estimate, createdAt = at, updatedAt = at,
)

class RecapTest {
    @Test
    fun theCardCountsPlacesDaysMoneyAndFavourites() {
        val items = listOf(
            item("1", start, "Pantai Melasti"),
            item("2", start, "Tari Kecak", ItemSource.AI, "18:00"),
            item("3", LocalDate(2026, 11, 21), "pantai melasti"), // the same place again
            item("4", LocalDate(2026, 11, 22), "Seafood Jimbaran", ItemSource.AI),
            item("5", LocalDate(2026, 11, 22), null), // no place
            item("6", null, "Bedugul"), // never scheduled
        )
        val expenses = listOf(
            expense("a", 300_000, listOf("s", "d", "r")),
            expense("b", 90_000, listOf("d", "m")),
            expense("c", 1_000_000, listOf("s"), estimate = true),
        )
        val recap = tripRecap(start, end, "IDR", items, expenses, TimeZone.of("Asia/Makassar"))
        assertEquals(3, recap.placesVisited)
        assertEquals(4, recap.days)
        assertEquals(390_000, recap.totalMinor, "estimates are not money spent")
        assertEquals(4, recap.people)
        assertEquals(97_500, recap.perPersonMinor)
        assertEquals(listOf("Tari Kecak", "Seafood Jimbaran", "Pantai Melasti"), recap.favourites, "Jelajah picks first")
    }

    @Test
    fun aSoloTripWithoutCostsIsOnePerson() {
        val recap = tripRecap(start, start, "IDR", emptyList(), emptyList(), TimeZone.UTC)
        assertEquals(1, recap.days)
        assertEquals(1, recap.people)
        assertEquals(0, recap.perPersonMinor)
        assertEquals(false, recapAvailable(end, end))
        assertEquals(true, recapAvailable(end, LocalDate(2026, 11, 24)))
    }

    @Test
    fun aTemplateKeepsDayNumbersAndALongerItineraryOverflowsToUnscheduled() {
        val items = listOf(item("1", start, "Pantai Melasti"), item("2", LocalDate(2026, 11, 23), "Uluwatu"), item("3", null, "Bedugul"))
        val lines = templateItemsOf(start, items)
        assertEquals(listOf(0, 3, null), lines.map { it.dayIndex })
        val packing = templatePackingOf(listOf(PackingItemDto("p", "t", PackingCategory.OTHER, "Kompor", quantity = 2, isPacked = true, createdAt = at, updatedAt = at)))
        assertEquals(listOf(TemplateItem(PackingCategory.OTHER, "Kompor", 2)), packing)

        val template = TripTemplateDto("tt", "Bali", 4, items = lines, packing = packing, createdAt = at)
        var n = 0
        val newTrip = startFromTemplate(template, LocalDate(2027, 3, 1), LocalDate(2027, 3, 2)) { "id-${n++}" }
        assertEquals(listOf(LocalDate(2027, 3, 1), null, null), newTrip.items.map { it.dayDate })
        assertNull(newTrip.items[1].startTime, "an overflowing line loses its time")
        assertEquals(2, newTrip.packing.single().quantity)
        assertEquals(4, newTrip.items.map { it.id }.toSet().size + newTrip.packing.size)
    }
}
