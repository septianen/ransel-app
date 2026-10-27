package app.ransel.feature

import app.ransel.data.notification.NoPushRegistrar
import app.ransel.data.notification.NotificationsStore
import app.ransel.data.notification.PushRegistrar
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.notifications.NotificationKind
import app.ransel.feature.notifications.NotificationSettingsViewModel
import app.ransel.feature.notifications.NotificationsViewModel
import app.ransel.shared.model.DevicePlatform
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val calls = mutableListOf<Pair<String, String>>()

    private val inboxJson = """{"data":{"unreadCount":2,"items":[
        {"id":"n1","type":"MEMBER_JOINED","tripId":"t1","params":{"tripTitle":"Bali","actorName":"Dinda"},"createdAt":"2026-10-21T10:00:00Z"},
        {"id":"n2","type":"EXPENSE_ADDED","tripId":"t1","params":{"amountMinor":"300000","currency":"IDR"},"createdAt":"2026-10-21T09:00:00Z"},
        {"id":"n3","type":"ROLE_CHANGED","tripId":"t1","params":{"role":"EDITOR"},"createdAt":"2026-10-20T09:00:00Z","readAt":"2026-10-20T10:00:00Z"}]}}"""

    private fun api(settings: String = """{"data":{"tripActivity":true,"expenses":true,"reminders":true}}""", patchStatus: HttpStatusCode = HttpStatusCode.OK) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                calls += request.method.value + " " + path to request.body.toByteArray().decodeToString()
                when {
                    path.endsWith("/notifications/read") -> respond("", HttpStatusCode.NoContent)
                    path.endsWith("/notifications") -> respond(inboxJson, HttpStatusCode.OK, json)
                    path.endsWith("/notification-settings") && request.method.value == "PATCH" ->
                        if (patchStatus == HttpStatusCode.OK) respond("""{"data":{"tripActivity":true,"expenses":true,"reminders":false}}""", patchStatus, json)
                        else respond("""{"error":{"code":"INTERNAL_ERROR","message":"x"}}""", patchStatus, json)
                    path.endsWith("/notification-settings") -> respond(settings, HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun loadsTheInboxAndSplitsNewFromEarlier() = runTest {
        val vm = NotificationsViewModel(api(), NotificationsStore())
        eventually { vm.state.value.inbox != null }
        assertEquals(listOf("n1", "n2"), vm.state.value.unread.map { it.id })
        assertEquals(listOf("n3"), vm.state.value.earlier.map { it.id })
        assertEquals(2, vm.state.value.inbox?.unreadCount)
    }

    @Test
    fun openingOneMarksOnlyThatOneRead() = runTest {
        val store = NotificationsStore()
        val vm = NotificationsViewModel(api(), store)
        eventually { vm.state.value.inbox != null }
        vm.markRead(vm.state.value.unread.first())
        eventually { store.inbox.value?.unreadCount == 1 }
        assertTrue(calls.any { it.first == "POST /api/v1/notifications/read" && "\"n1\"" in it.second })
        assertEquals(listOf("n2"), vm.state.value.unread.map { it.id })
    }

    @Test
    fun markAllReadClearsTheBadge() = runTest {
        val store = NotificationsStore()
        val vm = NotificationsViewModel(api(), store)
        eventually { vm.state.value.inbox != null }
        vm.markAllRead()
        eventually { store.inbox.value?.unreadCount == 0 }
        assertTrue(vm.state.value.unread.isEmpty())
    }

    private class FakeRegistrar(override val available: Boolean, private val permission: Boolean) : PushRegistrar {
        var openedSettings = false
        override val platform = DevicePlatform.ANDROID
        override val appVersion: String? = null
        override suspend fun token(): String? = null
        override fun hasPermission() = permission
        override fun requestPermission() = Unit
        override fun shouldExplainPermission() = false
        override fun explanationDismissed() = Unit
        override fun openSystemSettings() { openedSettings = true }
        override fun deviceLanguage() = "id"
    }

    @Test
    fun aSwitchMovesAtOnceAndIsSavedWithOnlyThatField() = runTest {
        val vm = NotificationSettingsViewModel(api(), NoPushRegistrar)
        eventually { vm.state.value.settings != null }
        vm.set(NotificationKind.REMINDERS, false)
        eventually { calls.any { it.first == "PATCH /api/v1/notification-settings" } }
        assertEquals("""{"reminders":false}""", calls.last { it.first.startsWith("PATCH") }.second)
        assertEquals(false, vm.state.value.settings?.reminders)
    }

    @Test
    fun aFailedSaveSwitchesItBack() = runTest {
        val vm = NotificationSettingsViewModel(api(patchStatus = HttpStatusCode.InternalServerError), NoPushRegistrar)
        eventually { vm.state.value.settings != null }
        vm.set(NotificationKind.EXPENSES, false)
        eventually { vm.state.value.saveFailed }
        assertEquals(true, vm.state.value.settings?.expenses)
    }

    @Test
    fun theBannerShowsOnlyWhenPushIsAvailableAndThePermissionIsOff() = runTest {
        assertEquals(true, NotificationSettingsViewModel(api(), FakeRegistrar(available = true, permission = false)).state.value.permissionOff)
        assertEquals(false, NotificationSettingsViewModel(api(), FakeRegistrar(available = true, permission = true)).state.value.permissionOff)
        assertEquals(false, NotificationSettingsViewModel(api(), FakeRegistrar(available = false, permission = false)).state.value.permissionOff)
        val registrar = FakeRegistrar(available = true, permission = false)
        NotificationSettingsViewModel(api(), registrar).openSystemSettings()
        assertTrue(registrar.openedSettings)
        assertNull(null)
    }
}
