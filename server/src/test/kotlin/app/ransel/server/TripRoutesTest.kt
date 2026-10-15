package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

internal fun tripJson(id: String = UUID.randomUUID().toString(), title: String = "Bali 4 hari", start: String = "2026-11-20", end: String = "2026-11-23", budget: Long? = 5_000_000) =
    """{"id":"$id","title":"$title","destination":"Bali","startDate":"$start","endDate":"$end","baseCurrency":"IDR","budgetMinor":${budget ?: "null"}}"""

class TripRoutesTest {
    @Test
    fun createThenGetDetailWithSummary() = apiTest {
        val id = UUID.randomUUID().toString()
        val created = post("/trips", tripJson(id))
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("OWNER", created.data().jsonObject.getValue("myRole").jsonPrimitive.content)

        val detail = get("/trips/$id")
        assertEquals(HttpStatusCode.OK, detail.status)
        val summary = detail.data().jsonObject.getValue("summary").jsonObject
        assertEquals(4, summary.getValue("daysTotal").jsonPrimitive.content.toInt())
        assertEquals(0, summary.getValue("daysFilled").jsonPrimitive.content.toInt())
        assertEquals(5_000_000L, summary.getValue("remainingMinor").jsonPrimitive.content.toLong())
        assertEquals(1, summary.getValue("memberCount").jsonPrimitive.content.toInt())

        val list = get("/trips").data().jsonArray
        assertEquals(1, list.size)
    }

    @Test
    fun invalidTripIsRejectedWithFieldDetails() = apiTest {
        val response = post("/trips", tripJson(title = " ", start = "2026-11-23", end = "2026-11-20"))
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("VALIDATION_FAILED", response.errorCode())
        val details = response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals("REQUIRED", details.getValue("title").jsonPrimitive.content)
        assertEquals("MUST_BE_AFTER_START", details.getValue("endDate").jsonPrimitive.content)
    }

    @Test
    fun malformedBodyIs400() = apiTest {
        val response = post("/trips", """{"id": 5""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("VALIDATION_FAILED", response.errorCode())
    }

    @Test
    fun unknownTripIs404() = apiTest {
        val response = get("/trips/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("TRIP_NOT_FOUND", response.errorCode())
        assertEquals("TRIP_NOT_FOUND", get("/trips/not-a-uuid").errorCode())
    }

    @Test
    fun duplicateIdIs409() = apiTest {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id))
        val again = post("/trips", tripJson(id))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("CONFLICT", again.errorCode())
    }

    @Test
    fun updateReplacesFields() = apiTest {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id))
        val body = """{"title":"Bali 5 hari","destination":"Ubud","startDate":"2026-11-20","endDate":"2026-11-24","baseCurrency":"IDR"}"""
        val updated = put("/trips/$id", body)
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("Bali 5 hari", updated.data().jsonObject.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun deletedTripDisappears() = apiTest {
        val id = UUID.randomUUID().toString()
        post("/trips", tripJson(id))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$id").status)
        assertEquals(0, get("/trips").data().jsonArray.size)
        assertEquals(HttpStatusCode.NotFound, get("/trips/$id").status)
    }
}
