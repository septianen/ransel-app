package app.ransel.server

import app.ransel.server.db.TemplateSeed
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Karung edits the built-in packing templates (owner, 3 Nov 2026); user templates are only counted. */
class BuiltinTemplateRoutesTest {
    private val config = TestDb.config().copy(adminEmails = setOf("admin@example.com"))
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private val beach = TemplateSeed.idOf("beach").toString()

    private fun body(name: String, vararg items: String, id: String? = null) =
        """{${id?.let { "\"id\":\"$it\"," } ?: ""}"name":"$name","items":[${items.joinToString(",") { """{"category":"CLOTHING","name":"$it","quantity":2}""" }}]}"""

    private suspend fun Api.appTemplates() =
        get("/packing-templates").data().jsonArray.map { it.jsonObject }.associateBy { it.str("name") }

    @Test
    fun onlyAdminsManageThemAndUserTemplatesAreOnlyCounted() = apiTest(config) {
        signUp()
        val traveller = token
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        post("/packing-templates", """{"id":"${UUID.randomUUID()}","name":"Rahasia Rina","items":[{"category":"OTHER","name":"Kado"}]}""")
        post("/packing-templates", """{"id":"${UUID.randomUUID()}","name":"Bersama","sharedTripId":"$trip","items":[{"category":"OTHER","name":"Tenda"}]}""")
        assertEquals("FORBIDDEN", get("/admin/packing-templates").errorCode())
        assertEquals("FORBIDDEN", put("/admin/packing-templates/$beach", body("x", "y")).errorCode())

        signUp(email = "admin@example.com")
        val response = get("/admin/packing-templates")
        val list = response.data().jsonObject
        assertEquals(setOf("Liburan pantai", "Wisata kota", "Mendaki gunung"), list.getValue("templates").jsonArray.map { it.jsonObject.str("name") }.toSet())
        assertEquals("2", list.str("userTemplateCount"))
        assertEquals("1", list.str("sharedTemplateCount"))
        assertFalse("Rahasia Rina" in response.bodyAsText() || "Kado" in response.bodyAsText(), "no user template content")
        // a user template cannot be read or changed through these routes
        val admin = token
        token = traveller
        val own = get("/packing-templates").data().jsonArray.map { it.jsonObject }.first { it.str("name") == "Rahasia Rina" }.str("id")
        token = admin
        assertEquals("NOT_FOUND", put("/admin/packing-templates/$own", body("Diambil", "x")).errorCode())
        assertEquals("NOT_FOUND", delete("/admin/packing-templates/$own").errorCode())
    }

    @Test
    fun anEditIsLiveAtOnceAndSurvivesARestart() = apiTest(config) {
        signUp(email = "admin@example.com")
        val saved = put("/admin/packing-templates/$beach", body("Liburan pantai", "Baju renang", "Topi pantai"))
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        assertEquals(2, appTemplates().getValue("Liburan pantai").getValue("items").jsonArray.size)

        TemplateSeed.run() // what every server start does
        assertEquals(2, appTemplates().getValue("Liburan pantai").getValue("items").jsonArray.size)
        assertEquals("TEMPLATE_UPDATE", get("/admin/audit?action=TEMPLATE_UPDATE").data().jsonArray.single().jsonObject.str("action"))

        val bad = put("/admin/packing-templates/$beach", body(" "))
        assertEquals("VALIDATION_FAILED", bad.errorCode())
    }

    @Test
    fun aDeletedTemplateStaysDeletedUntilRestored() = apiTest(config) {
        signUp(email = "admin@example.com")
        val deleted = delete("/admin/packing-templates/$beach").data().jsonObject
        assertTrue(deleted["deletedAt"] != null)
        assertNull(appTemplates()["Liburan pantai"])

        TemplateSeed.run()
        assertNull(appTemplates()["Liburan pantai"], "the seed must not bring it back")
        val listed = get("/admin/packing-templates").data().jsonObject.getValue("templates").jsonArray.map { it.jsonObject }
        assertEquals("Liburan pantai", listed.last().str("name"), "deleted ones come last")

        post("/admin/packing-templates/$beach/restore")
        assertTrue(appTemplates().containsKey("Liburan pantai"))
    }

    @Test
    fun aNewBuiltinTemplateShowsInTheApps() = apiTest(config) {
        signUp(email = "admin@example.com")
        val id = UUID.randomUUID().toString()
        val created = post("/admin/packing-templates", body("Konser", "Jas hujan", id = id))
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        assertEquals("CONFLICT", post("/admin/packing-templates", body("Konser", "Jas hujan", id = id)).errorCode())
        assertEquals("VALIDATION_FAILED", post("/admin/packing-templates", body("Tanpa id", "x")).errorCode())

        signUp()
        val konser = appTemplates().getValue("Konser")
        assertNull(konser["ownerId"])
        assertEquals("Jas hujan", konser.getValue("items").jsonArray.single().jsonObject.str("name"))
    }

}
