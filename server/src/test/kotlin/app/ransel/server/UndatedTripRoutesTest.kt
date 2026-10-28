package app.ransel.server

import app.ransel.server.ai.UndatedTrip
import app.ransel.server.push.LoggingPushSender
import app.ransel.server.service.NotificationService
import app.ransel.server.service.ReminderScheduler
import app.ransel.shared.domain.startFromTemplate
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.SyncChanges
import app.ransel.shared.dto.SyncPullResponse
import app.ransel.shared.dto.SyncPushRequest
import app.ransel.shared.dto.SyncPushResponse
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripTemplateDto
import app.ransel.shared.model.Category
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val wire = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.isNull(key: String) = this[key] == null || this[key] is JsonNull

/** A trip without dates ("Tanggal belum pasti"): the dates are simply left out. */
private fun undatedTripJson(id: String, title: String = "Bali bareng") =
    """{"id":"$id","title":"$title","destination":"Bali","baseCurrency":"IDR","budgetMinor":5000000}"""

private suspend fun Api.undatedTrip(): String =
    UUID.randomUUID().toString().also { id ->
        val created = post("/trips", undatedTripJson(id))
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
    }

private suspend fun Api.syncPush(changes: SyncChanges): SyncPushResponse {
    val response = post("/sync", wire.encodeToString(SyncPushRequest.serializer(), SyncPushRequest(changes)))
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    return wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPushResponse>>(response.bodyAsText()).data
}

private suspend fun Api.syncPull(): SyncPullResponse =
    wire.decodeFromString<app.ransel.shared.api.ApiResponse<SyncPullResponse>>(get("/sync").bodyAsText()).data

private suspend fun Api.items(tripId: String) = get("/trips/$tripId/itinerary").data().jsonArray.map { it.jsonObject }

class UndatedTripRoutesTest {
    @Test
    fun aTripWithoutDatesIsCreatedAndReadBackByRest() = apiTest {
        val id = undatedTrip()
        val detail = get("/trips/$id").data().jsonObject
        val trip = detail.getValue("trip").jsonObject
        assertTrue(trip.isNull("startDate") && trip.isNull("endDate"))
        val summary = detail.getValue("summary").jsonObject
        assertEquals("0", summary.str("daysTotal"))
        assertEquals("0", summary.str("daysFilled"))
        assertEquals(listOf(id), get("/trips").data().jsonArray.map { it.jsonObject.str("id") })
    }

    @Test
    fun oneDateAloneIsRejected() = apiTest {
        val onlyStart = post("/trips", """{"id":"${UUID.randomUUID()}","title":"Bali","destination":"Bali","startDate":"2026-11-20","baseCurrency":"IDR"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, onlyStart.status)
        assertEquals("REQUIRED", onlyStart.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("endDate"))
        val onlyEnd = post("/trips", """{"id":"${UUID.randomUUID()}","title":"Bali","destination":"Bali","endDate":"2026-11-20","baseCurrency":"IDR"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, onlyEnd.status)
        assertEquals("REQUIRED", onlyEnd.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("startDate"))

        val id = undatedTrip()
        val update = put("/trips/$id", """{"title":"Bali","destination":"Bali","startDate":"2026-11-20","baseCurrency":"IDR"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, update.status)
    }

    @Test
    fun aTripWithoutDatesIsCreatedAndReadBackBySync() = apiTest {
        val id = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val pushed = syncPush(
            SyncChanges(
                trips = listOf(
                    TripDto(
                        id = id, ownerId = userId, title = "Bali bareng", destination = "Bali", startDate = null, endDate = null,
                        baseCurrency = "IDR", createdAt = now, updatedAt = now,
                    ),
                ),
            ),
        )
        assertEquals(1, pushed.accepted.size, pushed.rejected.toString())
        val trip = syncPull().changes.trips.single()
        assertNull(trip.startDate)
        assertNull(trip.endDate)

        // one date alone is rejected in a push too
        val half = syncPush(
            SyncChanges(trips = listOf(trip.copy(id = UUID.randomUUID().toString(), startDate = LocalDate(2026, 11, 20), updatedAt = Clock.System.now()))),
        )
        assertEquals("VALIDATION_FAILED", half.rejected.single().code)
    }

    @Test
    fun anItemOnAnUndatedTripMustBeUnscheduled() = apiTest {
        val trip = undatedTrip()
        val dated = post("/trips/$trip/itinerary", itemJson(day = "2026-11-20"))
        assertEquals(HttpStatusCode.UnprocessableEntity, dated.status)
        assertEquals("OUTSIDE_TRIP", dated.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("dayDate"))

        val id = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/$trip/itinerary", itemJson(id, day = null, start = null, end = null)).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, put("/trips/$trip/itinerary/$id", itemJson(id, day = "2026-11-21")).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, put("/trips/$trip/days/2026-11-21/order", """{"items":[]}""").status)

        // a dated trip: a day outside its range is refused as well
        val datedTrip = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/$datedTrip/itinerary", itemJson(day = "2026-11-24")).status)
        assertEquals(HttpStatusCode.Created, post("/trips/$datedTrip/itinerary", itemJson(day = "2026-11-23")).status)
    }

    @Test
    fun aSyncedItemWithADayOnAnUndatedTripIsKeptUnscheduled() = apiTest {
        val trip = undatedTrip()
        val itemId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val pushed = syncPush(
            SyncChanges(
                itineraryItems = listOf(
                    ItineraryItemDto(
                        id = itemId, tripId = trip, dayDate = LocalDate(2026, 11, 20), startTime = LocalTime(10, 0), endTime = LocalTime(11, 30),
                        title = "Pantai Kuta", category = Category.ATTRACTION, sortOrder = 1.0, createdAt = now, updatedAt = now,
                    ),
                ),
            ),
        )
        assertEquals(1, pushed.accepted.size)
        val item = items(trip).single()
        assertTrue(item.isNull("dayDate") && item.isNull("startTime"))
        assertEquals("90", item.str("durationMin"))
    }

    @Test
    fun clearingTheDatesUnschedulesEveryItem() = apiTest {
        val trip = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }
        post("/trips/$trip/itinerary", itemJson(day = "2026-11-20", start = "10:00", end = "11:00"))
        post("/trips/$trip/itinerary", itemJson(day = "2026-11-22", start = "09:00", end = "09:45"))

        val cleared = put("/trips/$trip", """{"title":"Bali 4 hari","destination":"Bali","baseCurrency":"IDR"}""")
        assertEquals(HttpStatusCode.OK, cleared.status, cleared.bodyAsText())
        assertTrue(cleared.data().jsonObject.isNull("startDate"))
        val items = items(trip)
        assertEquals(2, items.size, "nothing is deleted")
        assertTrue(items.all { it.isNull("dayDate") && it.isNull("startTime") })
        assertEquals(setOf("60", "45"), items.map { it.str("durationMin") }.toSet())
    }

    @Test
    fun clearingTheDatesBySyncUnschedulesEveryItem() = apiTest {
        val trip = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }
        post("/trips/$trip/itinerary", itemJson(day = "2026-11-21"))
        val stored = syncPull().changes.trips.single()
        val pushed = syncPush(SyncChanges(trips = listOf(stored.copy(startDate = null, endDate = null, updatedAt = Clock.System.now() + 1.minutes))))
        assertEquals(1, pushed.accepted.size, pushed.rejected.toString())
        assertTrue(items(trip).single().isNull("dayDate"))
    }

    @Test
    fun aDatePollDecidedOnAnUndatedTripSetsTheDates() = apiTest {
        val trip = undatedTrip()
        post("/trips/$trip/itinerary", itemJson(day = null, start = null, end = null, title = "Tanah Lot"))
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        val pollId = UUID.randomUUID().toString()
        val created = post(
            "/trips/$trip/polls",
            """{"id":"$pollId","kind":"DATE","question":"Kapan berangkat?","closesAt":"2099-01-01T00:00:00Z","options":[""" +
                """{"id":"$a","label":"","startDate":"2026-12-01","endDate":"2026-12-04"},{"id":"$b","label":"","startDate":"2026-12-10","endDate":"2026-12-13"}]}""",
        )
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val decided = post("/polls/$pollId/decide", """{"optionId":"$b"}""")
        assertEquals("DECIDED", decided.data().jsonObject.str("status"), decided.bodyAsText())

        val detail = get("/trips/$trip").data().jsonObject
        val dto = detail.getValue("trip").jsonObject
        assertEquals("2026-12-10", dto.str("startDate"))
        assertEquals("2026-12-13", dto.str("endDate"))
        assertEquals("4", detail.getValue("summary").jsonObject.str("daysTotal"))
        assertEquals("Tanah Lot", items(trip).single().str("title"), "the unscheduled place stays")
        // now the trip has days
        assertEquals(HttpStatusCode.Created, post("/trips/$trip/itinerary", itemJson(day = "2026-12-11")).status)
    }

    @Test
    fun theInvitePreviewAndTheGuestPageShowNoDates() = apiTest {
        val owner = session()
        val trip = undatedTrip()
        val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")

        val pollId = UUID.randomUUID().toString()
        post(
            "/trips/$trip/polls",
            """{"id":"$pollId","kind":"CUSTOM","question":"Makan di mana?","closesAt":"2099-01-01T00:00:00Z","options":[{"id":"${UUID.randomUUID()}","label":"Sate"},{"id":"${UUID.randomUUID()}","label":"Bakso"}]}""",
        )
        val shareCode = post("/polls/$pollId/share").data().jsonObject.str("code")

        signUp()
        val preview = get("/invites/$code")
        assertEquals(HttpStatusCode.OK, preview.status, preview.bodyAsText())
        assertTrue(preview.data().jsonObject.let { it.isNull("startDate") && it.isNull("endDate") })

        token = null
        val page = get("/guest/$shareCode")
        assertEquals(HttpStatusCode.OK, page.status, page.bodyAsText())
        assertTrue(page.data().jsonObject.let { it.isNull("startDate") && it.isNull("endDate") })
        use(owner)
    }

    @Test
    fun templatesFromAndOntoAnUndatedTrip() = apiTest {
        // from: no days, every line unscheduled
        val undated = undatedTrip()
        post("/trips/$undated/itinerary", itemJson(day = null, start = null, end = null, title = "Tanah Lot"))
        val fromUndated = post("/trip-templates", """{"id":"${UUID.randomUUID()}","name":"Belum pasti","tripId":"$undated"}""")
        assertEquals(HttpStatusCode.Created, fromUndated.status, fromUndated.bodyAsText())
        val t1 = fromUndated.data().jsonObject
        assertEquals("0", t1.str("dayCount"))
        assertTrue(t1.getValue("items").jsonArray.single().jsonObject.isNull("dayIndex"))

        // onto: a template with days laid on a trip without dates gives only unscheduled items, which the server takes
        val dated = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }
        post("/trips/$dated/itinerary", itemJson(day = "2026-11-21", start = "09:00", end = "10:00", title = "Ubud"))
        val created = post("/trip-templates", """{"id":"${UUID.randomUUID()}","name":"Bali","tripId":"$dated"}""")
        val template = wire.decodeFromString<app.ransel.shared.api.ApiResponse<TripTemplateDto>>(created.bodyAsText()).data
        val target = undatedTrip()
        val start = startFromTemplate(template, null, null) { UUID.randomUUID().toString() }
        start.items.forEach { input ->
            val body = wire.encodeToString(app.ransel.shared.dto.ItineraryItemInput.serializer(), input)
            assertEquals(HttpStatusCode.Created, post("/trips/$target/itinerary", body).status)
        }
        assertTrue(items(target).single().isNull("dayDate"))
    }

    @Test
    fun noRemindersOrRecapWithoutDates() = apiTest {
        val trip = undatedTrip()
        post("/trips/$trip/packing", """{"id":"${UUID.randomUUID()}","category":"OTHER","name":"Kompor","quantity":1}""")
        val scheduler = TestDb.config().let { ReminderScheduler(it, NotificationService(it, LoggingPushSender())) }
        fun at(date: String, time: String) = LocalDateTime.parse("${date}T$time").atZone(ZoneId.of("Asia/Jakarta")).toInstant()
        scheduler.tick(at("2026-11-19", "19:00"))
        scheduler.tick(at("2026-11-24", "09:00"))
        val types = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("type") }
        assertTrue("RECAP_READY" !in types && "PACKING_REMINDER" !in types, types.toString())
    }

    @Test
    fun theNeutralSearchDatesAreThreeDaysFromTheFirstOfNextMonth() {
        assertEquals(LocalDate(2026, 11, 1), UndatedTrip.startDate(LocalDate(2026, 10, 28)))
        assertEquals(LocalDate(2027, 1, 1), UndatedTrip.startDate(LocalDate(2026, 12, 31)))
        assertEquals("2027-01", UndatedTrip.month(LocalDate(2026, 12, 5)))
        assertEquals(3, UndatedTrip.DAYS)
    }
}
