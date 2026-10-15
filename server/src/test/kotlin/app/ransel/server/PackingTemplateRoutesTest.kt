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
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

class PackingTemplateRoutesTest {
    private fun template(id: String = UUID.randomUUID().toString(), name: String = "Weekend", shared: String? = null) =
        """{"id":"$id","name":"$name","items":[
            {"category":"CLOTHING","name":"Kaos","quantity":3},
            {"category":"TOILETRIES","name":"Sikat gigi"}
        ]${if (shared != null) ""","sharedTripId":"$shared"""" else ""}}"""

    @Test
    fun builtInTemplatesAreSeededOnceAndListedFirst() = apiTest {
        val list = get("/packing-templates").data().jsonArray.map { it.jsonObject }
        assertEquals(3, list.size)
        assertTrue(list.all { it["ownerId"] == null })
        assertEquals(setOf("Liburan pantai", "Wisata kota", "Mendaki gunung"), list.map { it.str("name") }.toSet())
        assertTrue(list.all { it.getValue("items").jsonArray.size > 10 })
    }

    @Test
    fun ownTemplateIsPrivateUntilSharedWithATrip() = apiTest {
        val owner = session()
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))

        val privateId = UUID.randomUUID().toString()
        val sharedId = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/packing-templates", template(privateId, "Pribadi")).status)
        val shared = post("/packing-templates", template(sharedId, "Bersama", shared = tripId))
        assertEquals(tripId, shared.data().jsonObject.str("sharedTripId"))
        assertEquals(5, get("/packing-templates").data().jsonArray.size)

        // a member of the trip sees the shared one only
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        val outsider = get("/packing-templates").data().jsonArray.map { it.jsonObject.str("name") }
        assertEquals(3, outsider.size)
        post("/invites/$code/accept")
        val member = get("/packing-templates").data().jsonArray.map { it.jsonObject.str("name") }
        assertEquals(4, member.size)
        assertTrue("Bersama" in member && "Pribadi" !in member)

        // sharing needs a trip you belong to
        use(owner)
        signUp()
        val denied = post("/packing-templates", template(shared = tripId))
        assertEquals("TRIP_NOT_FOUND", denied.errorCode())
    }

    @Test
    fun templateValidation() = apiTest {
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/packing-templates", """{"id":"${UUID.randomUUID()}","name":" ","items":[]}""").status)
        val id = UUID.randomUUID().toString()
        post("/packing-templates", template(id))
        assertEquals(HttpStatusCode.Conflict, post("/packing-templates", template(id)).status)
    }

    @Test
    fun applyCreatesItemsAndSkipsWhatIsThere() = apiTest {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))
        val templateId = UUID.randomUUID().toString()
        post("/packing-templates", template(templateId))
        post("/trips/$tripId/packing", """{"id":"${UUID.randomUUID()}","category":"CLOTHING","name":"KAOS","quantity":1}""")

        val first = post("/trips/$tripId/packing/apply-template", """{"templateId":"$templateId","skipExisting":true}""")
        assertEquals(HttpStatusCode.Created, first.status)
        val created = first.data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("Sikat gigi"), created.map { it.str("name") })
        assertEquals("1", created.single().str("quantity")) // quantity omitted in the template -> 1
        assertEquals("false", created.single().str("isPacked"))

        val again = post("/trips/$tripId/packing/apply-template", """{"templateId":"$templateId","skipExisting":true}""")
        assertEquals(0, again.data().jsonArray.size)

        val all = post("/trips/$tripId/packing/apply-template", """{"templateId":"$templateId","skipExisting":false}""")
        assertEquals(2, all.data().jsonArray.size)
        assertEquals(4, get("/trips/$tripId/packing").data().jsonArray.size) // KAOS + Sikat gigi + both again
    }

    @Test
    fun applyNeedsEditorAndAVisibleTemplate() = apiTest {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId))
        val builtIn = get("/packing-templates").data().jsonArray.first().jsonObject.str("id")
        val privateId = UUID.randomUUID().toString()
        post("/packing-templates", template(privateId))
        val code = post("/trips/$tripId/invites", """{"role":"VIEWER","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")

        signUp()
        post("/invites/$code/accept")
        assertEquals(HttpStatusCode.Forbidden, post("/trips/$tripId/packing/apply-template", """{"templateId":"$builtIn"}""").status)

        // another editor-level user cannot use somebody's private template
        val other = UUID.randomUUID().toString()
        post("/trips", tripJson(other))
        val notVisible = post("/trips/$other/packing/apply-template", """{"templateId":"$privateId"}""")
        assertEquals(HttpStatusCode.NotFound, notVisible.status)
        assertNull(notVisible.jsonBody()["data"])
        assertEquals(HttpStatusCode.Created, post("/trips/$other/packing/apply-template", """{"templateId":"$builtIn"}""").status)
    }
}
