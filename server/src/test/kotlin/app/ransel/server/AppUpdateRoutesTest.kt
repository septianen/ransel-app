package app.ransel.server

import app.ransel.shared.domain.PLAY_STORE_HTTPS
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Android force update: Karung sets a minimum and a latest, `GET /app-update` tells the app which screen to show. */
class AppUpdateRoutesTest {
    private val config = TestDb.config().copy(adminEmails = setOf("admin@example.com"))
    private val play = "https://play.google.com/store/apps/details?id=app.ransel"

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private suspend fun HttpResponse.errorDetail(field: String): String =
        jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str(field)

    private suspend fun Api.status(version: String, platform: String = "ANDROID"): JsonObject =
        get("/app-update?platform=$platform&version=$version").let { response ->
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            response.data().jsonObject
        }

    @Test
    fun nothingIsAskedUntilKarungSetsAVersion() = apiTest(config) {
        val open = status("1.0.0")
        assertEquals("NONE", open.str("status"))
        assertFalse(open.containsKey("storeUrl"))
        // the built-in link is the Play listing, so a version alone is enough to prompt
        signUp(email = "admin@example.com")
        put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"1.1.0"}}""")
        token = null
        val required = status("1.0.0")
        assertEquals("REQUIRED", required.str("status"))
        assertEquals("1.1.0", required.str("latestVersion"))
        assertEquals("1.1.0", required.str("minVersion"))
        assertEquals(PLAY_STORE_HTTPS, required.str("storeUrl"))
    }

    @Test
    fun minimumBlocksAndLatestOnlyOffers() = apiTest(config) {
        signUp(email = "admin@example.com")
        val saved = put(
            "/admin/settings",
            """{"values":{"ANDROID_MIN_VERSION":"1.1.0","ANDROID_LATEST_VERSION":"1.2.0","ANDROID_UPDATE_MESSAGE":"Perbaikan penting.","ANDROID_STORE_URL":"$play"}}""",
        )
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        token = null

        val blocked = status("1.0.0")
        assertEquals("REQUIRED", blocked.str("status"))
        assertEquals("1.2.0", blocked.str("latestVersion"))
        assertEquals("1.1.0", blocked.str("minVersion"))
        assertEquals("Perbaikan penting.", blocked.str("message"))
        assertEquals(play, blocked.str("storeUrl"))

        val offer = status("1.1.0")
        assertEquals("OPTIONAL", offer.str("status"))
        assertEquals("1.2.0", offer.str("latestVersion"))
        assertEquals("NONE", status("1.2.0").str("status"))
        assertEquals("NONE", status("1.2.1").str("status"))
        assertEquals("OPTIONAL", status("1.1.9-debug").str("status"))
        // iOS is not enforced, including for an app that still asks
        assertEquals("NONE", status("1.0.0", platform = "IOS").str("status"))
    }

    @Test
    fun loweringTheMinimumStopsTheGate() = apiTest(config) {
        signUp(email = "admin@example.com")
        val admin = token
        put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"2.0.0","ANDROID_LATEST_VERSION":"2.0.0"}}""")
        token = null
        assertEquals("REQUIRED", status("1.0.0").str("status"))
        token = admin
        put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"","ANDROID_LATEST_VERSION":"1.0.0"}}""")
        token = null
        assertEquals("NONE", status("1.0.0").str("status"))
        assertEquals("OPTIONAL", status("0.9.0").str("status"))
    }

    @Test
    fun aMissingLinkOrAMinimumPastTheLatestIsRefused() = apiTest(config) {
        assertEquals("VALIDATION_FAILED", get("/app-update?platform=WEB&version=1.0.0").errorCode())
        assertEquals("VALIDATION_FAILED", get("/app-update?platform=ANDROID").errorCode())
        signUp(email = "admin@example.com")
        assertEquals("MIN_AFTER_LATEST", put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"1.3.0","ANDROID_LATEST_VERSION":"1.2.0"}}""").errorDetail("ANDROID_MIN_VERSION"))
        assertEquals("NOT_A_VERSION", put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"3"}}""").errorDetail("ANDROID_MIN_VERSION"))
        assertEquals("NOT_A_URL", put("/admin/settings", """{"values":{"ANDROID_STORE_URL":"play.google.com/store"}}""").errorDetail("ANDROID_STORE_URL"))
        assertEquals("STORE_URL_REQUIRED", put("/admin/settings", """{"values":{"ANDROID_LATEST_VERSION":"1.2.0","ANDROID_STORE_URL":""}}""").errorDetail("ANDROID_STORE_URL"))
        val message = "m".repeat(281)
        assertEquals("TOO_LONG", put("/admin/settings", """{"values":{"ANDROID_UPDATE_MESSAGE":"$message"}}""").errorDetail("ANDROID_UPDATE_MESSAGE"))
    }

    @Test
    fun aBlankStoreLinkAnswersNoneSoTheGateCannotTrapAnyone() = apiTest(
        config.copy(androidUpdate = app.ransel.server.AppConfig.AndroidUpdate(minVersion = "1.1.0", storeUrl = "")),
    ) {
        assertEquals("NONE", status("1.0.0").str("status"))
    }
}
