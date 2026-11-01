package app.ransel.feature

import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.update.pendingUpdate
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.shared.model.DevicePlatform
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppUpdateTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val queries = mutableListOf<String>()

    private fun api(status: HttpStatusCode, body: String) = RanselApi(
        createApiHttpClient(MockEngine { request -> queries += request.url.encodedQuery; respond(body, status, json) }),
        origin = "http://api.test",
    )

    private val required = """{"data":{"status":"REQUIRED","latestVersion":"0.3.0","storeUrl":"https://play.google.com/store/apps/details?id=app.ransel"}}"""

    @Test
    fun aRequiredUpdateCarriesTheVersionAndTheStoreLink() = runTest {
        val update = api(HttpStatusCode.OK, required).pendingUpdate(DevicePlatform.ANDROID, "0.1.0")
        assertEquals(AppUpdateStatus.REQUIRED, update?.status)
        assertEquals("0.3.0", update?.latestVersion)
        assertEquals(listOf("platform=ANDROID&version=0.1.0"), queries)
    }

    @Test
    fun nothingToShowWhenUpToDateOnTheWebOrWithoutAVersion() = runTest {
        assertNull(api(HttpStatusCode.OK, """{"data":{"status":"NONE"}}""").pendingUpdate(DevicePlatform.IOS, "0.3.0"))
        assertNull(api(HttpStatusCode.OK, required).pendingUpdate(null, null), "the web is never asked")
        assertNull(api(HttpStatusCode.OK, required).pendingUpdate(DevicePlatform.ANDROID, null))
        assertEquals(emptyList(), queries)
    }

    @Test
    fun aFailedCheckNeverBlocksTheApp() = runTest {
        assertNull(api(HttpStatusCode.InternalServerError, """{"error":{"code":"INTERNAL_ERROR","message":"x"}}""").pendingUpdate(DevicePlatform.ANDROID, "0.1.0"))
    }

    @Test
    fun aLinkLessAnswerIsIgnored() = runTest {
        assertNull(api(HttpStatusCode.OK, """{"data":{"status":"REQUIRED","latestVersion":"0.3.0"}}""").pendingUpdate(DevicePlatform.IOS, "0.1.0"))
    }
}
