package app.ransel.server

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** The update dialog: Karung sets a minimum per platform, `GET /app-update` tells the app what to do. */
class AppUpdateRoutesTest {
    private val config = TestDb.config().copy(adminEmails = setOf("admin@example.com"))
    private val play = "https://play.google.com/store/apps/details?id=app.ransel"

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private suspend fun Api.status(platform: String, version: String): JsonObject =
        get("/app-update?platform=$platform&version=$version").data().jsonObject

    @Test
    fun nothingIsAskedUntilKarungSetsAMinimumAndALink() = apiTest(config) {
        assertEquals("NONE", status("ANDROID", "0.1.0").str("status"))
        signUp(email = "admin@example.com")
        put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"0.3.0"}}""")
        // a minimum without a store link still answers NONE (nowhere to send people)
        assertEquals("NONE", status("ANDROID", "0.1.0").str("status"))
    }

    @Test
    fun optionalAndRequiredFollowTheForceSetting() = apiTest(config) {
        signUp(email = "admin@example.com")
        val saved = put("/admin/settings", """{"values":{"ANDROID_MIN_VERSION":"0.3.0","ANDROID_STORE_URL":"$play"}}""")
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        val admin = token
        token = null // the call is public

        val optional = status("ANDROID", "0.1.0")
        assertEquals("OPTIONAL", optional.str("status"))
        assertEquals("0.3.0", optional.str("latestVersion"))
        assertEquals(play, optional.str("storeUrl"))
        assertEquals("NONE", status("ANDROID", "0.3.0").str("status"))
        assertEquals("NONE", status("ANDROID", "0.3.1").str("status"))
        assertEquals("OPTIONAL", status("ANDROID", "0.2.9-debug").str("status"))
        // iOS has its own settings
        assertEquals("NONE", status("IOS", "0.1.0").str("status"))

        token = admin
        put("/admin/settings", """{"values":{"ANDROID_FORCE_UPDATE":"true"}}""")
        token = null
        assertEquals("REQUIRED", status("ANDROID", "0.1.0").str("status"))
        assertEquals("NONE", status("ANDROID", "0.3.0").str("status"))
    }

    @Test
    fun badInputsAreRefused() = apiTest(config) {
        assertEquals("VALIDATION_FAILED", get("/app-update?platform=WEB&version=1.0.0").errorCode())
        assertEquals("VALIDATION_FAILED", get("/app-update?platform=IOS").errorCode())
        signUp(email = "admin@example.com")
        assertEquals("VALIDATION_FAILED", put("/admin/settings", """{"values":{"IOS_MIN_VERSION":"3"}}""").errorCode())
        assertEquals("VALIDATION_FAILED", put("/admin/settings", """{"values":{"IOS_STORE_URL":"apps.apple.com/app/id1"}}""").errorCode())
    }
}
