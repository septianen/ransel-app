package app.ransel.server

import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.Prompt
import app.ransel.server.ai.TripTransport
import app.ransel.server.ai.tripTransport
import app.ransel.shared.model.Interest
import app.ransel.shared.model.LocalTransport
import app.ransel.shared.model.MainTravelMode
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `/trips/{id}/taste` (plan explore-trip-one-tap) and the trip's transport in the place-search prompt. */
class TripTasteRoutesTest {
    private suspend fun Api.newTrip(): String =
        UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    @Test
    fun aNewTripHasNoTasteThenASaveIsNormalizedAndReplaces() = apiTest {
        val trip = newTrip()
        val empty = get("/trips/$trip/taste")
        assertEquals(HttpStatusCode.OK, empty.status)
        assertEquals(JsonNull, empty.data().jsonObject["taste"] ?: JsonNull)

        val saved = put(
            "/trips/$trip/taste",
            """{"interests":["FOOD","NATURE","FOOD"],"extraInterests":[" Sakura ","","Sakura","Onsen"],"style":"SANTAI","priceLevel":"LOW","notes":"  tanpa babi  "}""",
        )
        assertEquals(HttpStatusCode.OK, saved.status)
        val taste = saved.data().jsonObject.getValue("taste").jsonObject
        assertEquals(listOf("FOOD", "NATURE"), taste.getValue("interests").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Sakura", "Onsen"), taste.getValue("extraInterests").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("SANTAI", taste.getValue("style").jsonPrimitive.content)
        assertEquals("LOW", taste.getValue("priceLevel").jsonPrimitive.content)
        assertEquals("tanpa babi", taste.getValue("notes").jsonPrimitive.content)
        assertTrue(saved.data().jsonObject.getValue("updatedAt").jsonPrimitive.content.isNotEmpty())

        // a save replaces the whole taste: fields left out are cleared
        put("/trips/$trip/taste", """{"interests":["CULTURE"]}""")
        val again = get("/trips/$trip/taste").data().jsonObject.getValue("taste").jsonObject
        assertEquals(listOf("CULTURE"), again.getValue("interests").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(again.getValue("extraInterests").jsonArray.isEmpty())
        assertEquals(JsonNull, again["style"] ?: JsonNull)
        assertEquals(JsonNull, again["notes"] ?: JsonNull)
    }

    @Test
    fun tooManyOrTooLongIsRejectedPerField() = apiTest {
        val trip = newTrip()
        val many = put("/trips/$trip/taste", """{"extraInterests":["a","b","c","d","e","f"]}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, many.status)
        assertEquals("TOO_MANY", many.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.getValue("extraInterests").jsonPrimitive.content)
        val long = put("/trips/$trip/taste", """{"extraInterests":["${"x".repeat(41)}"],"notes":"${"n".repeat(501)}"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, long.status)
        val details = long.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals("TOO_LONG", details.getValue("extraInterests").jsonPrimitive.content)
        assertEquals("TOO_LONG", details.getValue("notes").jsonPrimitive.content)
        assertEquals(JsonNull, get("/trips/$trip/taste").data().jsonObject["taste"] ?: JsonNull, "nothing was stored")
    }

    @Test
    fun aViewerReadsButCannotSaveAndAStrangerSeesNothing() = apiTest {
        val owner = session()
        val trip = newTrip()
        put("/trips/$trip/taste", """{"interests":["FOOD"]}""")
        val code = post("/trips/$trip/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.getValue("code").jsonPrimitive.content
        signUp()
        post("/invites/$code/accept")
        val read = get("/trips/$trip/taste")
        assertEquals(HttpStatusCode.OK, read.status)
        assertEquals("FOOD", read.data().jsonObject.getValue("taste").jsonObject.getValue("interests").jsonArray.single().jsonPrimitive.content)
        val denied = put("/trips/$trip/taste", """{"interests":["NATURE"]}""")
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("FORBIDDEN", denied.errorCode())

        signUp()
        assertFalse(get("/trips/$trip/taste").status == HttpStatusCode.OK, "not a member")
        use(owner)
        assertEquals("FOOD", get("/trips/$trip/taste").data().jsonObject.getValue("taste").jsonObject.getValue("interests").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun localTransportWinsAndUnknownFallsBackToTheMainMode() {
        assertEquals(TripTransport.MOTORCYCLE, tripTransport(LocalTransport.RENT_MOTORCYCLE, MainTravelMode.PUBLIC_TRANSPORT))
        assertEquals(TripTransport.RIDE_HAILING, tripTransport(LocalTransport.RIDE_HAILING, null))
        assertEquals(TripTransport.CAR, tripTransport(LocalTransport.UNKNOWN, MainTravelMode.CAR))
        assertEquals(TripTransport.PUBLIC_TRANSPORT, tripTransport(null, MainTravelMode.PUBLIC_TRANSPORT))
        assertNull(tripTransport(LocalTransport.UNKNOWN, null))
    }

    @Test
    fun thePromptNamesTheTransportOnlyWhenKnown() {
        val query = LlmQuery(
            destination = "Bali", days = 3, startDate = "2026-12-01", priceLevel = PriceLevel.MEDIUM, priceGuide = null,
            interests = listOf(Interest.FOOD), extraInterests = emptyList(), style = TravelStyle.SANTAI, notes = null,
            liked = emptyList(), skipped = emptyList(), count = 10,
        )
        assertFalse(Prompt.user(query).contains("Getting around"))
        val bike = Prompt.user(query.copy(transport = TripTransport.MOTORCYCLE))
        assertTrue(bike.contains("Getting around: rented motorbike"), bike)
        val transit = Prompt.user(query.copy(transport = TripTransport.PUBLIC_TRANSPORT))
        assertTrue(transit.contains("close together"), transit)
    }
}
