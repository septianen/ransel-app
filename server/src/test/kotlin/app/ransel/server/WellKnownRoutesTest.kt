package app.ransel.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class WellKnownRoutesTest {
    private val configured = TestDb.config().copy(
        androidCertSha256 = listOf("AA:BB"),
        iosAppIds = listOf("ABCDE12345.app.ransel"),
    )

    @Test
    fun servedAtTheRootWithoutAToken() = apiTest(configured) {
        token = null
        val android = client.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, android.status)
        assertEquals(ContentType.Application.Json, android.contentType()?.withoutParameters())
        val target = TestJson.parseToJsonElement(android.bodyAsText()).jsonArray.single().jsonObject.getValue("target").jsonObject
        assertEquals("app.ransel", target.getValue("package_name").jsonPrimitive.content)
        assertEquals("AA:BB", target.getValue("sha256_cert_fingerprints").jsonArray.single().jsonPrimitive.content)

        val ios = client.get("/.well-known/apple-app-site-association")
        assertEquals(HttpStatusCode.OK, ios.status)
        val details = TestJson.parseToJsonElement(ios.bodyAsText()).jsonObject.getValue("applinks").jsonObject
            .getValue("details").jsonArray.single().jsonObject
        assertEquals("ABCDE12345.app.ransel", details.getValue("appIDs").jsonArray.single().jsonPrimitive.content)
        assertEquals("/j/*", details.getValue("components").jsonArray.single().jsonObject.getValue("/").jsonPrimitive.content)
    }

    @Test
    fun pastedKeytoolLabelIsStripped() {
        val config = app.ransel.server.AppConfig.fromEnv(
            mapOf("ANDROID_CERT_SHA256" to "SHA256: aa:bb:cc , 11:22"),
        )
        assertEquals(listOf("AA:BB:CC", "11:22"), config.androidCertSha256)
    }

    @Test
    fun notConfiguredAnswers404() = apiTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/apple-app-site-association").status)
    }
}
