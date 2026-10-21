package app.ransel.feature

import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.notification.NoPushRegistrar
import app.ransel.data.notification.PushRegistrar
import app.ransel.data.notification.PushRegistration
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.repository.AppLanguage
import app.ransel.shared.model.DevicePlatform
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRegistrationTest {
    private val calls = mutableListOf<Triple<String, String, String>>()

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                calls += Triple(request.method.value, request.url.encodedPath + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""), request.body.toByteArray().decodeToString())
                respond("", HttpStatusCode.NoContent)
            },
        ),
        origin = "http://api.test",
    )

    private class FakeRegistrar(override val available: Boolean = true, val token: String? = "fcm-token") : PushRegistrar {
        var asked = 0
        override fun shouldExplainPermission() = false
        override fun explanationDismissed() = Unit
        override fun openSystemSettings() = Unit
        override val platform = DevicePlatform.ANDROID
        override val appVersion = "0.1.0"
        override suspend fun token() = token
        override fun hasPermission() = false
        override fun requestPermission() { asked++ }
        override fun deviceLanguage() = "id"
    }

    @Test
    fun registersTheTokenWithTheAppLanguageWithoutAskingForPermission() = runTest {
        val prefs = InMemoryPreferencesRepository().also { it.setLanguage(AppLanguage.ENGLISH) }
        val registrar = FakeRegistrar()
        PushRegistration(registrar, api(), prefs).register()

        assertEquals(0, registrar.asked, "the permission is asked from the explanation sheet, not here")
        val (method, path, body) = calls.single()
        assertEquals("POST", method)
        assertEquals("/api/v1/devices", path)
        assertTrue("\"pushToken\":\"fcm-token\"" in body && "\"locale\":\"en\"" in body && "\"platform\":\"ANDROID\"" in body)
    }

    @Test
    fun theSystemLanguageComesFromTheDevice() = runTest {
        PushRegistration(FakeRegistrar(), api(), InMemoryPreferencesRepository()).register()
        assertTrue("\"locale\":\"id\"" in calls.single().third)
    }

    @Test
    fun unregisterRemovesTheTokenThatWasSent() = runTest {
        val push = PushRegistration(FakeRegistrar(), api(), InMemoryPreferencesRepository())
        push.register()
        push.unregister()
        assertEquals("DELETE" to "/api/v1/devices?token=fcm-token", calls.last().let { it.first to it.second })
    }

    @Test
    fun withoutPushNothingIsSent() = runTest {
        PushRegistration(NoPushRegistrar, api(), InMemoryPreferencesRepository()).run { register(); unregister() }
        PushRegistration(FakeRegistrar(available = false), api(), InMemoryPreferencesRepository()).register()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun aNewTokenIsSentAgain() = runTest {
        PushRegistration(FakeRegistrar(), api(), InMemoryPreferencesRepository()).onNewToken("rotated")
        assertTrue("\"pushToken\":\"rotated\"" in calls.single().third)
    }
}
