package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelModeRoutesTest {
    private suspend fun Api.newTrip(): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    private suspend fun Api.twoStops(trip: String): Pair<String, String> {
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        post("/trips/$trip/itinerary", itemJson(a, title = "A", sort = 1.0))
        post("/trips/$trip/itinerary", itemJson(b, title = "B", sort = 2.0))
        return a to b
    }

    private fun body(id: String, from: String, to: String, mode: String = "WALK") =
        """{"id":"$id","fromItemId":"$from","toItemId":"$to","mode":"$mode"}"""

    @Test
    fun upsertListAndDeleteRoundTrip() = apiTest {
        val trip = newTrip()
        val (from, to) = twoStops(trip)
        val id = UUID.randomUUID().toString()
        val created = put("/trips/$trip/travel-modes", body(id, from, to, "MOTORCYCLE"))
        assertEquals(HttpStatusCode.OK, created.status)
        assertEquals("MOTORCYCLE", created.data().jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals(1, get("/trips/$trip/travel-modes").data().jsonArray.size)

        val again = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, to, "CAR"))
        assertEquals("CAR", again.data().jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals(id, again.data().jsonObject.getValue("id").jsonPrimitive.content, "the pair keeps its id")
        assertEquals(1, get("/trips/$trip/travel-modes").data().jsonArray.size)

        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/travel-modes/$id").status)
        assertEquals(0, get("/trips/$trip/travel-modes").data().jsonArray.size)
        val revived = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, to, "WALK"))
        assertEquals(id, revived.data().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("WALK", revived.data().jsonObject.getValue("mode").jsonPrimitive.content)
    }

    @Test
    fun farAndTheSameItemAreRejectedAndAMissingItemIsNotInTheTrip() = apiTest {
        val trip = newTrip()
        val (from, to) = twoStops(trip)
        val far = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, to, "FAR"))
        assertEquals(HttpStatusCode.UnprocessableEntity, far.status)
        assertEquals("INVALID", far.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.getValue("mode").jsonPrimitive.content)
        val same = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, from, "WALK"))
        assertEquals(HttpStatusCode.UnprocessableEntity, same.status)
        val missing = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, UUID.randomUUID().toString(), "WALK"))
        assertEquals(HttpStatusCode.UnprocessableEntity, missing.status)
        assertEquals("NOT_IN_TRIP", missing.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.getValue("toItemId").jsonPrimitive.content)
    }

    @Test
    fun aViewerCannotSave() = apiTest {
        val owner = session()
        val trip = newTrip()
        val (from, to) = twoStops(trip)
        val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.getValue("code").jsonPrimitive.content
        signUp()
        post("/invites/$code/accept")
        val denied = put("/trips/$trip/travel-modes", body(UUID.randomUUID().toString(), from, to))
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("FORBIDDEN", denied.errorCode())
        assertEquals(0, get("/trips/$trip/travel-modes").data().jsonArray.size)
        use(owner)
        assertTrue(get("/trips/$trip/travel-modes").data().jsonArray.isEmpty())
    }
}
