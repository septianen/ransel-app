package app.ransel.server

import app.ransel.server.push.FcmPushSender
import app.ransel.server.push.PushMessage
import app.ransel.server.push.PushResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FcmPushSenderTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val message = PushMessage("Judul", "Isi", mapOf("type" to "MEMBER_JOINED", "tripId" to "t1"))

    private fun serviceAccount(): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded)
        return """{"client_email":"push@ransel.iam.gserviceaccount.com","token_uri":"https://oauth.test/token",
            "private_key":"-----BEGIN PRIVATE KEY-----\n${pem.replace("\n", "\\n")}\n-----END PRIVATE KEY-----\n"}"""
    }

    private class Calls { val tokenExchanges = mutableListOf<String>(); val sends = mutableListOf<Pair<String, String>>() }

    private fun sender(calls: Calls, fcm: (String) -> Pair<HttpStatusCode, String>): FcmPushSender {
        val client = HttpClient(
            MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                if (request.url.host == "oauth.test") {
                    calls.tokenExchanges += body
                    respond("""{"access_token":"ya29.token","expires_in":3600}""", HttpStatusCode.OK, json)
                } else {
                    calls.sends += request.headers[HttpHeaders.Authorization].orEmpty() to body
                    val (status, answer) = fcm(body)
                    respond(answer, status, json)
                }
            },
        )
        return FcmPushSender(client, "ransel-test", serviceAccount())
    }

    @Test
    fun sendsTheMessageWithABearerFromAJwtExchangeAndReusesTheToken() = runBlocking {
        val calls = Calls()
        val sender = sender(calls) { HttpStatusCode.OK to """{"name":"projects/x/messages/1"}""" }
        assertEquals(PushResult.OK, sender.send("phone-1", message))
        assertEquals(PushResult.OK, sender.send("phone-2", message))

        assertEquals(1, calls.tokenExchanges.size, "the access token is cached")
        assertTrue("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer" in calls.tokenExchanges.single())
        assertTrue("assertion=" in calls.tokenExchanges.single())
        assertEquals("Bearer ya29.token", calls.sends.first().first)
        assertTrue("\"token\":\"phone-1\"" in calls.sends.first().second)
        assertTrue("\"tripId\":\"t1\"" in calls.sends.first().second)
        assertTrue("\"title\":\"Judul\"" in calls.sends.first().second)
    }

    @Test
    fun anUnregisteredTokenIsInvalidAndOtherFailuresAreRetried() = runBlocking {
        val gone = sender(Calls()) {
            HttpStatusCode.NotFound to """{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}"""
        }
        assertEquals(PushResult.INVALID_TOKEN, gone.send("old", message))

        val down = sender(Calls()) { HttpStatusCode.ServiceUnavailable to """{"error":{"status":"UNAVAILABLE"}}""" }
        assertEquals(PushResult.RETRY, down.send("any", message))
    }
}
