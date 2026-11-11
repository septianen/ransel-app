package app.ransel.feature

import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.update.UpdateCheck
import app.ransel.feature.update.checkForUpdate
import app.ransel.feature.update.UpdateSnooze
import app.ransel.feature.update.notificationSheetAllowed
import app.ransel.feature.update.shouldAskForUpdate
import app.ransel.feature.update.visibleUpdate
import app.ransel.shared.dto.AppUpdateDto
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val queries = mutableListOf<String>()

    private fun api(status: HttpStatusCode, body: String) = RanselApi(
        createApiHttpClient(MockEngine { request -> queries += request.url.encodedQuery; respond(body, status, json) }),
        origin = "http://api.test",
    )

    private val required = """{"data":{"status":"REQUIRED","latestVersion":"1.2.0","storeUrl":"https://play.google.com/store/apps/details?id=app.ransel","minVersion":"1.1.0","message":"Perbaikan penting."}}"""

    private val offer = AppUpdateDto(
        status = AppUpdateStatus.OPTIONAL,
        latestVersion = "1.2.0",
        storeUrl = "https://play.google.com/store/apps/details?id=app.ransel",
        minVersion = "1.0.0",
    )

    @Test
    fun aRequiredUpdateCarriesTheVersionTheMessageAndTheStoreLink() = runTest {
        val check = api(HttpStatusCode.OK, required).checkForUpdate("1.0.0")
        val update = assertIs<UpdateCheck.Ready>(check).update
        assertEquals(AppUpdateStatus.REQUIRED, visibleUpdate(update, snoozedLatest = null))
        assertEquals("1.2.0", update.latestVersion)
        assertEquals("1.1.0", update.minVersion)
        assertEquals("Perbaikan penting.", update.message)
        assertEquals(listOf("platform=ANDROID&version=1.0.0"), queries)
    }

    @Test
    fun debugIosAndTheWebAreNeverAsked() {
        assertTrue(shouldAskForUpdate(DevicePlatform.ANDROID, debug = false))
        assertEquals(false, shouldAskForUpdate(DevicePlatform.ANDROID, debug = true))
        assertEquals(false, shouldAskForUpdate(DevicePlatform.IOS, debug = false))
        assertEquals(false, shouldAskForUpdate(null, debug = false))
    }

    @Test
    fun aFailedCheckNeverBlocksTheApp() = runTest {
        val check = api(HttpStatusCode.InternalServerError, """{"error":{"code":"INTERNAL_ERROR","message":"x"}}""")
            .checkForUpdate("1.0.0")
        assertIs<UpdateCheck.Failed>(check)
        assertNull(visibleUpdate(null, snoozedLatest = null))
    }

    @Test
    fun aLinkLessAnswerIsIgnored() = runTest {
        val check = api(HttpStatusCode.OK, """{"data":{"status":"REQUIRED","latestVersion":"1.2.0"}}""").checkForUpdate("1.0.0")
        assertNull(visibleUpdate(assertIs<UpdateCheck.Ready>(check).update, snoozedLatest = null))
    }

    @Test
    fun nantiIsRememberedForTheProcessNotTheScreen() {
        UpdateSnooze.latestVersion = null
        try {
            assertEquals(AppUpdateStatus.OPTIONAL, visibleUpdate(offer, UpdateSnooze.latestVersion))
            UpdateSnooze.latestVersion = "1.2.0"
            // A new composition (rotation, or key(language)) reads the same process memory.
            assertNull(visibleUpdate(offer, UpdateSnooze.latestVersion))
            assertEquals(AppUpdateStatus.OPTIONAL, visibleUpdate(offer.copy(latestVersion = "1.3.0"), UpdateSnooze.latestVersion))
        } finally {
            UpdateSnooze.latestVersion = null
        }
    }

    @Test
    fun theNotificationSheetStaysDownWhileTheUpdateIsRequired() {
        assertFalse(notificationSheetAllowed(AppUpdateStatus.REQUIRED))
        assertTrue(notificationSheetAllowed(AppUpdateStatus.OPTIONAL))
        assertTrue(notificationSheetAllowed(AppUpdateStatus.NONE))
        assertTrue(notificationSheetAllowed(null))
    }

    @Test
    fun aSoftOfferIsSnoozedOncePerLatestAndAHardGateIsNot() {
        assertEquals(AppUpdateStatus.OPTIONAL, visibleUpdate(offer, snoozedLatest = null))
        assertNull(visibleUpdate(offer, snoozedLatest = "1.2.0"))
        assertEquals(AppUpdateStatus.OPTIONAL, visibleUpdate(offer, snoozedLatest = "1.1.0"))
        assertEquals(AppUpdateStatus.REQUIRED, visibleUpdate(offer.copy(status = AppUpdateStatus.REQUIRED), snoozedLatest = "1.2.0"))
    }
}
