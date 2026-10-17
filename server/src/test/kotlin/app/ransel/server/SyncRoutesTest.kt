package app.ransel.server

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.PackingItemDto
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Category
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val wire = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun id() = UUID.randomUUID().toString()

private suspend fun Api.push(changes: SyncChanges): SyncPushResponse {
    val response = post("/sync", wire.encodeToString(SyncPushRequest(changes)))
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPushResponse>>(response.bodyAsText()).data
}

private suspend fun Api.pull(since: Instant? = null): SyncPullResponse {
    val response: HttpResponse = get("/sync" + (since?.let { "?since=$it" } ?: ""))
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPullResponse>>(response.bodyAsText()).data
}

private fun Api.trip(
    id: String = id(),
    updatedAt: Instant = Clock.System.now(),
    deletedAt: Instant? = null,
    title: String = "Bali 4 hari",
    end: LocalDate = LocalDate(2026, 11, 23),
) = TripDto(
    id = id, ownerId = userId, title = title, destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = end, baseCurrency = "IDR", budgetMinor = 5_000_000,
    createdAt = Clock.System.now(), updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun item(
    tripId: String,
    id: String = id(),
    title: String = "Pantai Kuta",
    day: LocalDate? = LocalDate(2026, 11, 20),
    sort: Double = 1.0,
    updatedAt: Instant = Clock.System.now(),
    deletedAt: Instant? = null,
) = ItineraryItemDto(
    id = id, tripId = tripId, dayDate = day,
    startTime = day?.let { LocalTime(10, 0) }, endTime = day?.let { LocalTime(11, 0) },
    title = title, category = Category.ATTRACTION, sortOrder = sort,
    createdAt = Clock.System.now(), updatedAt = updatedAt, deletedAt = deletedAt,
)

class SyncRoutesTest {
    @Test
    fun offlineTripAppearsOnAnotherDevice() = apiTest {
        val tripId = id()
        val itemId = id()
        val pushed = push(
            SyncChanges(
                trips = listOf(trip(tripId)),
                itineraryItems = listOf(item(tripId, itemId)),
                packingItems = listOf(
                    PackingItemDto(id(), tripId, name = "Kaos", quantity = 3, createdAt = Clock.System.now(), updatedAt = Clock.System.now()),
                ),
                expenses = listOf(
                    ExpenseDto(
                        id(), tripId, 50_000, "IDR", spentAt = Clock.System.now(), paidByUserId = userId,
                        splits = listOf(ExpenseSplitDto(userId, 50_000)),
                        createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
                    ),
                ),
            ),
        )
        assertEquals(4, pushed.accepted.size)
        assertTrue(pushed.rejected.isEmpty())

        // "the web": the same account, read through REST and through a first pull
        assertEquals(HttpStatusCode.OK, get("/trips/$tripId").status)
        val pulled = pull()
        assertEquals(1, pulled.changes.trips.size)
        assertEquals(itemId, pulled.changes.itineraryItems.single().id)
        assertEquals(1, pulled.changes.packingItems.size)
        assertEquals(1, pulled.changes.expenses.single().splits.size)
    }

    @Test
    fun newerEditWinsAndOlderEditIsRejectedAsConflict() = apiTest {
        val tripId = id()
        val itemId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId, itemId, title = "Original"))))

        val newer = push(SyncChanges(itineraryItems = listOf(item(tripId, itemId, title = "Phone edit", updatedAt = Clock.System.now() + 1.minutes))))
        assertEquals(1, newer.accepted.size)

        val stale = push(SyncChanges(itineraryItems = listOf(item(tripId, itemId, title = "Stale edit", updatedAt = Clock.System.now() - 1.hours))))
        assertEquals(0, stale.accepted.size)
        assertEquals("CONFLICT", stale.rejected.single().code)

        assertEquals("Phone edit", pull().changes.itineraryItems.single().title)
    }

    @Test
    fun deletesTravelAsTombstones() = apiTest {
        val tripId = id()
        val itemId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId, itemId))))
        val cursor = pull().serverTime

        val later = Clock.System.now() + 1.minutes
        push(SyncChanges(itineraryItems = listOf(item(tripId, itemId, updatedAt = later, deletedAt = later))))

        // REST no longer lists it, sync pull delivers the tombstone
        assertEquals(0, get("/trips/$tripId/itinerary").data().jsonArray.size)
        val tomb = pull(cursor).changes.itineraryItems.single()
        assertNotNull(tomb.deletedAt)
    }

    @Test
    fun pullSinceOnlyReturnsNewerRows() = apiTest {
        val tripId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId, title = "First"))))
        val first = pull()
        assertEquals(1, first.changes.itineraryItems.size)

        // The cursor is taken a few seconds in the past on purpose, so wait it out with a clearly newer row.
        val otherItem = id()
        post("/trips/$tripId/itinerary", """{"id":"$otherItem","title":"Second","dayDate":"2026-11-21","sortOrder":2.0}""")
        val delta = pull(Instant.fromEpochMilliseconds(System.currentTimeMillis() - 2_000))
        assertTrue(delta.changes.itineraryItems.any { it.id == otherItem })
        assertTrue(pull(Clock.System.now() + 1.hours).changes.itineraryItems.isEmpty())
    }

    @Test
    fun rejectedRowDoesNotAbortTheBatch() = apiTest {
        val tripId = id()
        val good = item(tripId, title = "Good")
        val bad = item(tripId, title = "   ")
        val response = push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(bad, good)))
        assertEquals(2, response.accepted.size) // trip + good
        val rejection = response.rejected.single()
        assertEquals("VALIDATION_FAILED", rejection.code)
        assertEquals(bad.id, rejection.id)
    }

    @Test
    fun otherUsersTripIsInvisibleToPushAndPull() = apiTest {
        val tripId = id()
        push(SyncChanges(trips = listOf(trip(tripId))))
        signUp() // now acting as a second account

        val attempt = push(SyncChanges(itineraryItems = listOf(item(tripId))))
        assertEquals("TRIP_NOT_FOUND", attempt.rejected.single().code)
        val hijack = push(SyncChanges(trips = listOf(trip(tripId, updatedAt = Clock.System.now() + 1.hours, title = "Hijacked"))))
        assertEquals("TRIP_NOT_FOUND", hijack.rejected.single().code)
        assertTrue(pull().changes.trips.isEmpty())
    }

    @Test
    fun mismatchedSplitsAreRejectedWithTheirOwnCode() = apiTest {
        val tripId = id()
        val expense = ExpenseDto(
            id(), tripId, 100, "IDR", spentAt = Clock.System.now(), paidByUserId = userId,
            splits = listOf(ExpenseSplitDto(userId, 60)), createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
        )
        val response = push(SyncChanges(trips = listOf(trip(tripId)), expenses = listOf(expense)))
        assertEquals("SPLIT_TOTAL_MISMATCH", response.rejected.single().code)
    }

    @Test
    fun shortenedTripUnschedulesItemsOnRemovedDays() = apiTest {
        val tripId = id()
        val itemId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId, itemId, day = LocalDate(2026, 11, 23)))))

        push(SyncChanges(trips = listOf(trip(tripId, updatedAt = Clock.System.now() + 1.minutes, end = LocalDate(2026, 11, 21)))))

        val moved = pull().changes.itineraryItems.single()
        assertNull(moved.dayDate)
        assertNull(moved.startTime)
        assertEquals(60, moved.durationMin)
    }

    @Test
    fun pullCarriesMembersAndSharedTripsReachTheGuest() = apiTest {
        val tripId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId))))
        val owner = session()
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.getValue("code").jsonPrimitive.content
        val cursor = pull().serverTime

        signUp()
        post("/invites/$code/accept")
        val guestPull = pull()
        assertEquals(1, guestPull.changes.trips.size)
        assertEquals(1, guestPull.changes.itineraryItems.size)
        assertEquals(2, guestPull.changes.members.size)
        assertTrue(guestPull.changes.members.all { it.email == null })

        use(owner)
        val delta = pull(cursor - 1.hours)
        val joined = delta.changes.members.single { it.userId != owner.userId }
        assertEquals("EDITOR", joined.role.name)
        assertNotNull(delta.changes.members.first { it.userId == owner.userId }.email)
    }

    @Test
    fun joiningTripAfterTheCursorDeliversItWhole() = apiTest {
        val tripId = id()
        val itemId = id()
        push(SyncChanges(trips = listOf(trip(tripId)), itineraryItems = listOf(item(tripId, itemId))))
        val owner = session()
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.getValue("code").jsonPrimitive.content

        signUp()
        // The guest's cursor is later than every row of the trip: only the membership is newer.
        kotlinx.coroutines.delay(1_000)
        val cursor = Clock.System.now()
        kotlinx.coroutines.delay(200)
        post("/invites/$code/accept")

        val delta = pull(cursor).changes
        assertEquals(tripId, delta.trips.single().id)
        assertEquals(itemId, delta.itineraryItems.single().id)
        assertEquals(2, delta.members.size)
        use(owner)
    }

    @Test
    fun invalidSinceIs422() = apiTest {
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/sync?since=yesterday").status)
    }
}
