package app.ransel.feature

import app.ransel.data.analytics.Analytics
import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.analytics.BreadcrumbAnalytics
import app.ransel.data.analytics.CrashReporter
import app.ransel.data.analytics.UserProperty
import app.ransel.data.analytics.applySessionContext
import app.ransel.data.auth.SessionState
import app.ransel.data.remote.routePattern
import app.ransel.data.repository.AppLanguage
import app.ransel.shared.dto.MeDto
import app.ransel.data.analytics.screenNameOf
import app.ransel.data.fake.InMemoryPackingRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.feature.packing.PackingViewModel
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.shared.dto.CreatePackingItemRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val screens = mutableListOf<String>()
    var userId: String? = null
    val properties = mutableMapOf<String, String?>()
    override fun screen(name: String) { screens += name }
    override fun event(event: AnalyticsEvent) { events += event }
    override fun setUser(userId: String?) { this.userId = userId }
    override fun setUserProperty(name: String, value: String?) { properties[name] = value }
}

class FakeCrashReporter : CrashReporter {
    var userId: String? = null
    val recorded = mutableListOf<Throwable>()
    val logs = mutableListOf<String>()
    val keys = mutableMapOf<String, String>()
    override fun setUser(userId: String?) { this.userId = userId }
    override fun record(error: Throwable) { recorded += error }
    override fun log(message: String) { logs += message }
    override fun setKey(key: String, value: String) { keys[key] = value }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsEventsTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun screenNameDropsPackageAndArguments() {
        assertEquals("TripRoute", screenNameOf("app.ransel.navigation.TripRoute/{tripId}?tab={tab}"))
        assertEquals("HomeRoute", screenNameOf("app.ransel.navigation.HomeRoute"))
        assertEquals("ExplorePreferencesRoute", screenNameOf("app.ransel.navigation.ExplorePreferencesRoute?tripId={tripId}"))
    }

    @Test
    fun creatingATripIsTrackedButEditingIsNot() = runTest {
        val store = InMemoryStore()
        val trips = InMemoryTripRepository(store)
        val analytics = FakeAnalytics()

        val create = TripFormViewModel(null, trips, InMemoryPreferencesRepository(), analytics)
        create.onTitleChange("Bali")
        val saved = backgroundScope.launch { create.eventFlow.first() }
        create.save()
        saved.join()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.TripCreated), analytics.events)

        val tripId = trips.observeTrips().first().single().id
        val edit = TripFormViewModel(tripId, trips, InMemoryPreferencesRepository(), analytics)
        edit.onTitleChange("Bali 4 hari")
        val edited = backgroundScope.launch { edit.eventFlow.first() }
        edit.save()
        edited.join()
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun checkingAPackingItemReportsProgressAndUncheckingDoesNot() = runTest {
        val packing = InMemoryPackingRepository(InMemoryStore())
        packing.add("t1", CreatePackingItemRequest(id = "a", name = "Paspor"))
        packing.add("t1", CreatePackingItemRequest(id = "b", name = "Sandal"))
        val analytics = FakeAnalytics()
        val vm = PackingViewModel("t1", packing, analytics)
        backgroundScope.launch { vm.state.collect {} }
        fun item(id: String) = vm.state.value.groups.flatMap { it.items }.first { it.id == id }

        vm.state.first { it.total == 2 }
        vm.toggle(item("a"))
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PackingItemChecked(50)), analytics.events)

        vm.state.first { it.packed == 1 }
        vm.toggle(item("a"))
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun sessionContextSetsTheAccountIdAndPropertiesAndClearsThemOnSignOut() {
        val analytics = FakeAnalytics()
        val crashes = FakeCrashReporter()
        val me = MeDto(id = "u1", email = "sen@example.com", displayName = "SEN", emailVerified = false)

        applySessionContext(analytics, crashes, SessionState.SignedIn(me), AppLanguage.ENGLISH, requiresAccount = false)
        assertEquals("u1", analytics.userId)
        assertEquals("u1", crashes.userId)
        assertEquals("signed_in", analytics.properties[UserProperty.ACCOUNT_STATUS])
        assertEquals("false", analytics.properties[UserProperty.EMAIL_VERIFIED])
        assertEquals("en", analytics.properties[UserProperty.APP_LANGUAGE])
        assertEquals("signed_in", crashes.keys["account_status"])

        applySessionContext(analytics, crashes, SessionState.SignedOut, AppLanguage.SYSTEM, requiresAccount = false)
        assertEquals(null, analytics.userId)
        assertEquals("guest", analytics.properties[UserProperty.ACCOUNT_STATUS])
        assertEquals(null, analytics.properties[UserProperty.EMAIL_VERIFIED])

        applySessionContext(analytics, crashes, SessionState.SignedOut, AppLanguage.SYSTEM, requiresAccount = true)
        assertEquals("signed_out", analytics.properties[UserProperty.ACCOUNT_STATUS])
    }

    @Test
    fun screensAndEventsBecomeCrashBreadcrumbs() {
        val analytics = FakeAnalytics()
        val crashes = FakeCrashReporter()
        val tracked = BreadcrumbAnalytics(analytics, crashes)
        tracked.screen("TripRoute")
        tracked.event(AnalyticsEvent.TripCreated)
        tracked.event(AnalyticsEvent.PackingItemChecked(50))
        assertEquals(listOf("screen TripRoute", "trip_created", "packing_item_checked {progress_percent=50}"), crashes.logs)
        assertEquals(listOf("TripRoute"), analytics.screens)
        assertEquals(2, analytics.events.size)
    }

    @Test
    fun reportedPathsKeepNoIdsOrCodes() {
        assertEquals("/trips/{}/expenses", routePattern("/trips/0b9f3c1e-6a3d-4e8a-9f53-1c2d3e4f5a6b/expenses"))
        assertEquals("/invites/{}", routePattern("/invites/ABCD-EFGH-JKLM"))
        assertEquals("/auth/verify-email", routePattern("/auth/verify-email?token=secret"))
    }
}
