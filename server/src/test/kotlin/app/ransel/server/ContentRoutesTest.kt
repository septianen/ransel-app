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
    fun screenTextsAreNoLongerRemoteContent() = apiTest(config) {
        signUp(email = "admin@example.com")
        assertEquals(HttpStatusCode.NotFound, get("/admin/content/strings").status)
        val content = get("/content").data().jsonObject
        assertTrue(content["strings"]?.jsonObject.isNullOrEmpty())
        assertTrue(content["errors"]?.jsonArray.isNullOrEmpty())
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
        val published = post("/admin/content/publish", "{}").data().jsonObject
        assertEquals("1", published.str("published"))
        assertEquals("CONTENT_PUBLISH", get("/admin/audit?action=CONTENT_PUBLISH").data().jsonArray.single().jsonObject.str("action"))
        // error copy is not in GET /content any more: it comes with the error itself
        assertTrue(get("/content").data().jsonObject["errors"]?.jsonArray.isNullOrEmpty())
    }

    @Test
    fun anErrorResponseCarriesKarungsCopyInTheCallersLanguage() = apiTest(config) {
        signUp(email = "admin@example.com")
        val missing = "/trips/${java.util.UUID.randomUUID()}"
        assertNull(get(missing).jsonBody().getValue("error").jsonObject["text"], "nothing published yet")
        put(
            "/admin/content/errors",
            """{"code":"TRIP_NOT_FOUND","text":{"titleId":"Trip tidak ada","messageEn":"This trip is gone.","illustration":"ic_confuse"}}""",
        )
        post("/admin/content/publish", "{}")

        language = "id-ID,id;q=0.9,en;q=0.8"
        val id = get(missing).jsonBody().getValue("error").jsonObject
        assertEquals("TRIP_NOT_FOUND", id.str("code"))
        assertEquals("Trip tidak ada", id.obj("text").str("title"))
        assertNull(id.obj("text")["message"])
        assertEquals("ic_confuse", id.obj("text").str("illustration"))

        language = "en"
        val en = get(missing).jsonBody().getValue("error").jsonObject.obj("text")
        assertEquals("This trip is gone.", en.str("message"))
        assertNull(en["title"])

        // the 401 from the auth challenge goes the same way
        put("/admin/content/errors", """{"code":"UNAUTHORIZED","text":{"titleEn":"Please sign in"}}""")
        post("/admin/content/publish", "{}")
        token = null
        assertEquals("Please sign in", get("/trips").jsonBody().getValue("error").jsonObject.obj("text").str("title"))
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
