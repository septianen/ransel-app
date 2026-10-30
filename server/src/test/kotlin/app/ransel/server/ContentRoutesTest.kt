package app.ransel.server

import app.ransel.shared.api.ErrorCode
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Karung stage D (B9.9): drafts, publish, GET /content, categories and curated destination interests. */
class ContentRoutesTest {
    private val config = TestDb.config().copy(adminEmails = setOf("admin@example.com"))

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    @Test
    fun aStringDraftGoesLiveOnPublishAndKeepsItsPlaceholders() = apiTest(config) {
        signUp(email = "admin@example.com")
        val key = "trip_duration"
        val row = get("/admin/content/strings").data().jsonObject.getValue("strings").jsonArray.map { it.jsonObject }.single { it.str("key") == key }
        assertEquals("DEFAULT", row.str("status"))
        val bundled = row.obj("defaults").str("id")
        assertTrue("%1\$d" in bundled && "%2\$d" in bundled, bundled)

        val bad = put("/admin/content/strings", """{"key":"$key","text":{"id":"Lama perjalanan"}}""")
        assertEquals("VALIDATION_FAILED", bad.errorCode())
        assertTrue("PLACEHOLDER_MISMATCH" in bad.bodyAsText())
        assertEquals("VALIDATION_FAILED", put("/admin/content/strings", """{"key":"no_such_key","text":{"id":"x"}}""").errorCode())

        val draft = put("/admin/content/strings", """{"key":"$key","text":{"id":"%1${'$'}d hari, %2${'$'}d malam"}}""")
        assertEquals(HttpStatusCode.OK, draft.status, draft.bodyAsText())
        assertEquals("DRAFT", draft.data().jsonObject.str("status"))
        // nothing is live before "Terbitkan"
        assertEquals("0", get("/content").data().jsonObject.str("version"))

        val published = post("/admin/content/publish", "{}").data().jsonObject
        assertEquals("1", published.str("version"))
        assertEquals("1", published.str("published"))
        val content = get("/content").data().jsonObject
        assertEquals("%1\$d hari, %2\$d malam", content.obj("strings").obj("id").str(key))
        assertEquals(HttpStatusCode.NoContent, get("/content?version=1").status)
        assertEquals("CONTENT_PUBLISH", get("/admin/audit?action=CONTENT_PUBLISH").data().jsonArray.single().jsonObject.str("action"))

        // "Kembalikan bawaan": a draft without text removes the override when published
        put("/admin/content/strings", """{"key":"$key","text":{}}""")
        post("/admin/content/publish", "{}")
        val after = get("/content").data().jsonObject
        assertEquals("2", after.str("version"))
        assertNull(after["strings"]?.jsonObject?.get("id")?.jsonObject?.get(key))
    }

    @Test
    fun errorMessagesAreCheckedAndPublishedPerCode() = apiTest(config) {
        signUp(email = "admin@example.com")
        val list = get("/admin/content/errors").data().jsonObject
        assertEquals(ErrorCode.ALL.size, list.getValue("errors").jsonArray.size)
        assertTrue(list.getValue("illustrations").jsonArray.any { it.jsonPrimitive.content == "ic_confuse" })

        assertEquals("VALIDATION_FAILED", put("/admin/content/errors", """{"code":"NOPE","text":{}}""").errorCode())
        val bad = put("/admin/content/errors", """{"code":"INSUFFICIENT_CREDITS","text":{"messageId":"Halo {name}"}}""")
        assertEquals("VALIDATION_FAILED", bad.errorCode())
        val ok = put(
            "/admin/content/errors",
            """{"code":"INSUFFICIENT_CREDITS","text":{"titleId":"Kredit belum cukup","messageId":"Butuh {cost} kredit, kamu punya {available}.","illustration":"ic_confuse","display":"FULL_SCREEN"}}""",
        )
        assertEquals(HttpStatusCode.OK, ok.status, ok.bodyAsText())
        assertEquals("1", get("/admin/content/errors").data().jsonObject.str("drafts"))
        post("/admin/content/publish", "{}")
        val error = get("/content").data().jsonObject.getValue("errors").jsonArray.single().jsonObject
        assertEquals("INSUFFICIENT_CREDITS", error.str("code"))
        assertEquals("Kredit belum cukup", error.obj("text").str("titleId"))
    }

    @Test
    fun categoriesAreRelabelledAndReorderedButNeverAdded() = apiTest(config) {
        signUp(email = "admin@example.com")
        val all = get("/admin/content/categories").data().jsonObject.getValue("categories").jsonArray.map { it.jsonObject }
        val expense = all.filter { it.obj("category").str("kind") == "EXPENSE" }
        assertEquals("FOOD", expense[1].obj("category").str("constant"))
        assertEquals("local_activity", expense.first().str("defaultIcon"))

        val missing = put("/admin/content/categories", """{"kind":"EXPENSE","categories":[{"kind":"EXPENSE","constant":"FOOD","sortOrder":0}]}""")
        assertEquals("VALIDATION_FAILED", missing.errorCode())

        val order = listOf("FOOD", "ATTRACTION", "TRANSPORT", "LODGING", "SHOPPING", "OTHER")
        val body = order.joinToString(",") { c ->
            if (c == "FOOD") """{"kind":"EXPENSE","constant":"FOOD","labelId":"Kuliner","icon":"restaurant","color":"magenta","sortOrder":0}"""
            else """{"kind":"EXPENSE","constant":"$c","sortOrder":0}"""
        }
        val saved = put("/admin/content/categories", """{"kind":"EXPENSE","categories":[$body]}""")
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        val live = get("/content").data().jsonObject
        assertEquals("1", live.str("version"))
        val food = live.getValue("categories").jsonArray.map { it.jsonObject }.single { it.str("constant") == "FOOD" }
        assertEquals("Kuliner", food.str("labelId"))
        assertEquals("0", food.str("sortOrder"))
    }

    @Test
    fun curatedInterestsChangeTheSuggestionsAtOnce() = apiTest(config, ai = MonitoringAi) {
        val traveller = token
        signUp(email = "admin@example.com")
        val admin = token
        TestDb.exec(
            "INSERT INTO explore_suggestions_cache (destination, month, language, interests, price_low_max_usd, price_mid_max_usd, model, expires_at) " +
                "VALUES ('bali', '2099-11', 'id', '[\"Pantai selatan\",\"Wisata malam\",\"Snorkeling\"]', 30, 90, 'fake', now() + interval '30 days')",
        )
        val rows = get("/admin/content/interests?query=bal").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("bali" to "11"), rows.map { it.str("destination") to it.str("month") })

        val curation = get("/admin/content/interests/item?destination=Bali&month=11&language=id").data().jsonObject
        assertEquals(3, curation.getValue("chips").jsonArray.size)
        val save = put(
            "/admin/content/interests/item",
            """{"destination":"Bali","month":11,"language":"id","chips":[{"label":"Melukis batik","kind":"MANUAL"},{"label":"Snorkeling","kind":"PINNED"},{"label":"Wisata malam","kind":"HIDDEN"},{"label":"Pantai selatan","kind":"AI"}]}""",
        )
        assertEquals(HttpStatusCode.OK, save.status, save.bodyAsText())

        token = traveller
        val chips = get("/explore/suggestions?destination=Bali&month=2099-11").data().jsonObject.getValue("interests").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("Melukis batik", "Snorkeling", "Pantai selatan"), chips)
        token = admin
        assertTrue(get("/admin/content/interests").data().jsonArray.single().jsonObject.str("curated").toBoolean())
    }

    @Test
    fun everyErrorCodeConstantIsListed() {
        val constants = ErrorCode::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        assertEquals(constants.toSet(), ErrorCode.ALL.toSet(), "add new codes to ErrorCode.ALL")
    }
}
