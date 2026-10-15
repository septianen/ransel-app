package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal fun itemJson(
    id: String = UUID.randomUUID().toString(),
    title: String = "Pantai Kuta",
    day: String? = "2026-11-20",
    start: String? = "10:00",
    end: String? = "11:00",
    sort: Double = 1.0,
) = buildString {
    append("""{"id":"$id","title":"$title","category":"ATTRACTION","sortOrder":$sort""")
    if (day != null) append(""","dayDate":"$day"""")
    if (start != null) append(""","startTime":"$start"""")
    if (end != null) append(""","endTime":"$end"""")
    append("}")
}

private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

class ItineraryRoutesTest {
    private suspend fun Api.newTrip(): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    @Test
    fun createListOrderedWithUnscheduledFirst() = apiTest {
        val trip = newTrip()
        post("/trips/$trip/itinerary", itemJson(title = "B", sort = 2.0))
        post("/trips/$trip/itinerary", itemJson(title = "A", sort = 1.0))
        val created = post("/trips/$trip/itinerary", itemJson(title = "Unscheduled", day = null, start = null, end = null))
        assertEquals(HttpStatusCode.Created, created.status)

        val titles = get("/trips/$trip/itinerary").data().jsonArray.map { it.jsonObject.str("title") }
        assertEquals(listOf("Unscheduled", "A", "B"), titles)
    }

    @Test
    fun overnightActivityIsAccepted() = apiTest {
        val trip = newTrip()
        val response = post("/trips/$trip/itinerary", itemJson(start = "23:00", end = "01:00"))
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("01:00", response.data().jsonObject.str("endTime"))
    }

    @Test
    fun blankTitleAndHalfCoordinateAreRejected() = apiTest {
        val trip = newTrip()
        val bad = post("/trips/$trip/itinerary", itemJson(title = " ").dropLast(1) + ""","lat":1.5}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        val details = bad.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals("REQUIRED", details.str("title"))
        assertEquals("LAT_LNG_BOTH_OR_NONE", details.str("lat"))
    }

    @Test
    fun scheduleByReplacingUnscheduledItem() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/itinerary", itemJson(id, day = null, start = null, end = null))
        val response = put("/trips/$trip/itinerary/$id", itemJson(id, day = "2026-11-21", start = "09:00", end = "10:00"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("2026-11-21", response.data().jsonObject.str("dayDate"))
    }

    @Test
    fun deleteHidesItemAndMissingItemIs404() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/itinerary", itemJson(id))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/itinerary/$id").status)
        assertEquals(0, get("/trips/$trip/itinerary").data().jsonArray.size)
        val again = delete("/trips/$trip/itinerary/$id")
        assertEquals(HttpStatusCode.NotFound, again.status)
        assertEquals("NOT_FOUND", again.errorCode())
    }

    @Test
    fun dayOrderIsAppliedAtomically() = apiTest {
        val trip = newTrip()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        post("/trips/$trip/itinerary", itemJson(a, title = "A", start = "10:00", end = "11:25", sort = 1.0))
        post("/trips/$trip/itinerary", itemJson(b, title = "B", start = "11:25", end = "12:00", sort = 2.0))

        val ok = put(
            "/trips/$trip/days/2026-11-20/order",
            """{"items":[{"id":"$b","sortOrder":1.0,"startTime":"10:00","endTime":"10:35"},{"id":"$a","sortOrder":2.0,"startTime":"10:35","endTime":"12:00"}]}""",
        )
        assertEquals(HttpStatusCode.OK, ok.status)
        val day = ok.data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("B", "A"), day.map { it.str("title") })
        assertEquals("10:35", day[0].str("endTime"))

        // One unknown id: nothing at all may change.
        val bad = put(
            "/trips/$trip/days/2026-11-20/order",
            """{"items":[{"id":"$a","sortOrder":9.0,"startTime":"08:00","endTime":"09:00"},{"id":"${UUID.randomUUID()}","sortOrder":10.0}]}""",
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        val after = get("/trips/$trip/itinerary").data().jsonArray.map { it.jsonObject }.first { it.str("id") == a }
        assertEquals("10:35", after.str("startTime"))
    }

    @Test
    fun shorteningTripMovesItemsToUnscheduled() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/itinerary", itemJson(id, day = "2026-11-23", start = "10:00", end = "11:30"))
        put("/trips/$trip", """{"title":"Bali","destination":"Bali","startDate":"2026-11-20","endDate":"2026-11-21","baseCurrency":"IDR"}""")

        val item = get("/trips/$trip/itinerary").data().jsonArray.single().jsonObject
        assertNull(item["dayDate"]?.takeIf { it.toString() != "null" })
        assertEquals(90, item.str("durationMin")?.toInt())

        val summary = get("/trips/$trip").data().jsonObject.getValue("summary").jsonObject
        assertEquals(1, summary.str("unscheduledCount")?.toInt())
        assertEquals(0, summary.str("activityCount")?.toInt())
    }
}
