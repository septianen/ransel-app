package app.ransel.feature.debug

import app.ransel.data.repository.ExpenseRepository
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.TripRepository
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreateTripRequest
import app.ransel.shared.dto.ExpenseInput
import app.ransel.shared.dto.ItineraryItemInput
import app.ransel.shared.dto.PatchPackingItemRequest
import app.ransel.shared.model.Category
import app.ransel.shared.model.PackingCategory
import app.ransel.shared.model.PaymentMethod
import app.ransel.util.newId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours

/** Debug builds only: the "Bali 4 hari" trip from docs/specs/design-decisions-id.md ("Data contoh"). */
suspend fun seedSampleTrip(
    trips: TripRepository,
    itinerary: ItineraryRepository,
    packing: PackingRepository,
    expenses: ExpenseRepository,
) {
    val tripId = newId()
    val d1 = LocalDate(2026, 11, 20)
    trips.create(
        CreateTripRequest(
            id = tripId, title = "Bali 4 hari", destination = "Bali, Indonesia",
            startDate = d1, endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR", budgetMinor = 5_000_000,
        ),
    )

    fun t(h: Int, m: Int = 0) = LocalTime(h, m)
    suspend fun item(
        day: LocalDate?, start: LocalTime?, end: LocalTime?, title: String, category: Category,
        order: Double, duration: Int? = null, place: String? = null,
    ) = itinerary.save(
        tripId,
        ItineraryItemInput(
            id = newId(), dayDate = day, startTime = start, endTime = end, title = title, category = category,
            placeName = place, durationMin = duration, sortOrder = order,
        ),
    )

    item(d1, t(9), t(10, 30), "Pantai Melasti", Category.ATTRACTION, 1.0)
    item(d1, t(10, 30), t(12, 30), "Pura Uluwatu", Category.ATTRACTION, 2.0)
    item(d1, t(12, 30), t(13, 30), "Makan siang di warung lokal", Category.FOOD, 3.0)
    item(d1, t(18), t(19), "Tari Kecak", Category.ATTRACTION, 4.0)
    item(LocalDate(2026, 11, 21), t(8), t(9), "Sarapan di hotel", Category.FOOD, 1.0)
    item(LocalDate(2026, 11, 21), t(10), t(14), "Ubud dan Pasar Seni", Category.SHOPPING, 2.0)
    item(null, null, null, "Air Terjun Tegenungan", Category.ATTRACTION, 1.0, duration = 90)
    item(null, null, null, "Seafood Pantai Jimbaran", Category.FOOD, 2.0, duration = 120)

    val packingList = listOf(
        Triple(PackingCategory.CLOTHING, "Kaos", 4), Triple(PackingCategory.CLOTHING, "Celana pendek", 2),
        Triple(PackingCategory.CLOTHING, "Baju renang", 1), Triple(PackingCategory.DOCUMENTS, "KTP", 1),
        Triple(PackingCategory.DOCUMENTS, "Tiket pesawat", 1), Triple(PackingCategory.ELECTRONICS, "Charger", 1),
        Triple(PackingCategory.ELECTRONICS, "Power bank", 1), Triple(PackingCategory.TOILETRIES, "Sikat gigi", 1),
        Triple(PackingCategory.HEALTH, "Obat pribadi", 1),
    )
    packingList.forEachIndexed { index, (category, name, quantity) ->
        val id = newId()
        packing.add(tripId, CreatePackingItemRequest(id, category, name, quantity, sortOrder = index + 1.0))
        if (index < 5) packing.update(tripId, id, PatchPackingItemRequest(isPacked = true))
    }

    val spentAt = d1.atStartOfDayIn(TimeZone.currentSystemDefault()) + 12.hours
    listOf(
        Triple(1_500_000L, Category.LODGING, "Deposit penginapan"),
        Triple(1_250_000L, Category.TRANSPORT, "Sewa motor 4 hari"),
        Triple(500_000L, Category.FOOD, "Makan malam Jimbaran"),
    ).forEach { (amount, category, note) ->
        expenses.save(
            tripId,
            ExpenseInput(
                id = newId(), amountMinor = amount, currency = "IDR", category = category,
                paymentMethod = PaymentMethod.TRANSFER, note = note, spentAt = spentAt,
            ),
        )
    }
}
