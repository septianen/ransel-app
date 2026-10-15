package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PackingRoutesTest {
    private suspend fun Api.newTrip(): String = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    private fun item(id: String, name: String = "Kaos", category: String = "CLOTHING", quantity: Int = 3) =
        """{"id":"$id","category":"$category","name":"$name","quantity":$quantity}"""

    @Test
    fun createTickAndUncheckAll() = apiTest {
        val trip = newTrip()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/$trip/packing", item(a)).status)
        post("/trips/$trip/packing", item(b, "Charger", "ELECTRONICS", 1))

        val ticked = patch("/trips/$trip/packing/$a", """{"isPacked":true}""")
        assertEquals(HttpStatusCode.OK, ticked.status)
        assertEquals("true", ticked.data().jsonObject.getValue("isPacked").jsonPrimitive.content)
        assertEquals("3", ticked.data().jsonObject.getValue("quantity").jsonPrimitive.content)

        assertEquals(HttpStatusCode.NoContent, post("/trips/$trip/packing/uncheck-all").status)
        val packed = get("/trips/$trip/packing").data().jsonArray.map { it.jsonObject.getValue("isPacked").jsonPrimitive.content }
        assertEquals(listOf("false", "false"), packed)
    }

    @Test
    fun emptyPatchLeavesItemUnchanged() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/packing", item(id))
        val response = patch("/trips/$trip/packing/$id", "{}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Kaos", response.data().jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun invalidInputIs422AndDeleteHidesItem() = apiTest {
        val trip = newTrip()
        val bad = post("/trips/$trip/packing", item(UUID.randomUUID().toString(), name = " ", quantity = 0))
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)

        val id = UUID.randomUUID().toString()
        post("/trips/$trip/packing", item(id))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/packing/$id").status)
        assertEquals(0, get("/trips/$trip/packing").data().jsonArray.size)
        assertEquals(HttpStatusCode.NotFound, patch("/trips/$trip/packing/$id", """{"isPacked":true}""").status)
    }

    @Test
    fun unknownTripIs404() = apiTest {
        assertEquals("TRIP_NOT_FOUND", get("/trips/${UUID.randomUUID()}/packing").errorCode())
    }
}
