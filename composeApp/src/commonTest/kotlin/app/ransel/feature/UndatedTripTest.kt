package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryItineraryRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.data.sync.NoSyncStatus
import app.ransel.data.voting.VotingStore
import app.ransel.feature.group.Countdown
import app.ransel.feature.group.countdownFor
import app.ransel.feature.itinerary.ItineraryViewModel
import app.ransel.feature.tripform.TripFormEvent
import app.ransel.feature.tripform.TripFormViewModel
import app.ransel.feature.trips.TripListUiState
import app.ransel.feature.trips.TripListViewModel
import app.ransel.feature.trips.TripPhase
import app.ransel.feature.trips.phase
import app.ransel.feature.trips.pickFeaturedTrip
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.dto.TripDto
import app.ransel.stopTestMain
import app.ransel.util.plusDays
import app.ransel.util.today
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val ME = "u1"
private val json = headersOf(HttpHeaders.ContentType, "application/json")
private val stamp = Instant.parse("2026-10-28T10:00:00Z")

private fun trip(id: String, start: LocalDate?, end: LocalDate?, created: Instant = stamp) = TripDto(
    id = id, ownerId = ME, title = "Trip $id", destination = "Bali", startDate = start, endDate = end,
    baseCurrency = "IDR", createdAt = created, updatedAt = created,
)

/** A session manager that is signed out until [signIn]. */
private fun session(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    return SessionManager(store) { error("not used") }
}

private suspend fun SessionManager.signIn() =
    start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

class UndatedTripRulesTest {
    @Test
    fun aTripWithoutDatesIsUpcomingWithoutACountdown() {
        val today = LocalDate(2026, 11, 2)
        val undated = trip("u", null, null)
        assertEquals(TripPhase.UPCOMING, undated.phase(today))
        assertEquals(Countdown.NoDates, countdownFor(undated, today))
    }

    @Test
    fun homeFeaturesADatedUpcomingTripBeforeOneWithoutDates() {
        val today = LocalDate(2026, 11, 2)
        val dated = trip("d", LocalDate(2026, 12, 20), LocalDate(2026, 12, 23))
        val undated = trip("u", null, null)
        assertEquals("d", pickFeaturedTrip(listOf(undated, dated), today)?.id)
        assertEquals("u", pickFeaturedTrip(listOf(undated), today)?.id)
    }
}

class UndatedTripListTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    @Test
    fun undatedTripsAreUpcomingAfterTheDatedOnesNewestFirst() = runTest {
        val today = today()
        val store = InMemoryStore().also {
            it.trips.value = listOf(
                trip("old-undated", null, null, created = Instant.parse("2026-10-01T00:00:00Z")),
                trip("later", today.plusDays(40), today.plusDays(43)),
                trip("new-undated", null, null, created = Instant.parse("2026-10-20T00:00:00Z")),
                trip("soon", today.plusDays(5), today.plusDays(7)),
                trip("done", today.plusDays(-10), today.plusDays(-7)),
            )
        }
        val api = RanselApi(createApiHttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }), origin = "http://api.test")
        val vm = TripListViewModel(InMemoryTripRepository(store), NoSyncStatus, VotingStore(), api, session())
        backgroundScope.launch { vm.state.collect {} }
        eventually { vm.state.value is TripListUiState.Content }
        val content = vm.state.value as TripListUiState.Content
        assertEquals(listOf("soon", "later", "new-undated", "old-undated"), content.upcoming.map { it.trip.id })
        assertEquals(listOf("done"), content.finished.map { it.trip.id })
        assertTrue(content.ongoing.isEmpty())
    }
}

class UndatedItineraryTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private fun idea(id: String, day: LocalDate? = null) =
        ItineraryItemDto(id = id, tripId = "u", dayDate = day, title = "Idea $id", sortOrder = 1.0, createdAt = stamp, updatedAt = stamp)

    @Test
    fun anUndatedTripHasNoDaysAndShowsItsOpenDatePoll() = runTest {
        val undated = trip("u", null, null)
        val store = InMemoryStore().also { it.items.value = listOf(idea("a"), idea("b")) }
        val api = RanselApi(
            createApiHttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/trips/u/polls")) respond(
                        """{"data":[
                        {"id":"old","tripId":"u","kind":"DATE","question":"Kapan?","createdByMemberId":"m2","createdByName":"Dinda",
                         "createdAt":"2026-10-20T00:00:00Z","closesAt":"2026-10-21T00:00:00Z","status":"DECIDED"},
                        {"id":"food","tripId":"u","kind":"FOOD","question":"Makan?","createdByMemberId":"m2","createdByName":"Dinda",
                         "createdAt":"2026-10-27T00:00:00Z","closesAt":"2026-10-30T00:00:00Z","status":"OPEN"},
                        {"id":"dates","tripId":"u","kind":"DATE","question":"Kapan berangkat?","createdByMemberId":"m2","createdByName":"Dinda",
                         "createdAt":"2026-10-26T00:00:00Z","closesAt":"2026-10-30T00:00:00Z","status":"OPEN"}
                        ]}""",
                        HttpStatusCode.OK, json,
                    ) else respond("", HttpStatusCode.NoContent)
                },
            ),
            origin = "http://api.test",
        )
        val signedIn = session().also { it.signIn() }
        val vm = ItineraryViewModel(undated, InMemoryItineraryRepository(store), InMemoryPreferencesRepository(), api = api, session = signedIn)
        backgroundScope.launch { vm.state.collect {} }
        eventually { !vm.state.value.loading && vm.state.value.datePoll != null }
        val state = vm.state.value
        assertTrue(state.undated)
        assertTrue(state.days.isEmpty())
        assertNull(state.selectedDay)
        assertEquals(listOf("a", "b"), state.unscheduled.map { it.id })
        assertEquals("dates", state.datePoll?.id)
        assertNull(vm.previewAutoSchedule())
    }

    @Test
    fun aGuestSeesTheIdeasWithoutAPoll() = runTest {
        val store = InMemoryStore().also { it.items.value = listOf(idea("a")) }
        val vm = ItineraryViewModel(trip("u", null, null), InMemoryItineraryRepository(store), InMemoryPreferencesRepository())
        backgroundScope.launch { vm.state.collect {} }
        eventually { !vm.state.value.loading }
        assertTrue(vm.state.value.undated)
        assertEquals(1, vm.state.value.unscheduled.size)
        assertNull(vm.state.value.datePoll)
    }

    @Test
    fun aDatedTripKeepsItsDays() = runTest {
        val dated = trip("d", LocalDate(2026, 11, 20), LocalDate(2026, 11, 23))
        val vm = ItineraryViewModel(dated, InMemoryItineraryRepository(InMemoryStore()), InMemoryPreferencesRepository())
        backgroundScope.launch { vm.state.collect {} }
        eventually { !vm.state.value.loading }
        assertFalse(vm.state.value.undated)
        assertEquals(4, vm.state.value.days.size)
    }
}

class UndatedTripFormTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val requests = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private var pollStatus = HttpStatusCode.Created

    private fun api() = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests.update { it + (request.method.value + " " + path to request.body.toByteArray().decodeToString()) }
                if (request.method == HttpMethod.Post && path.endsWith("/polls")) {
                    if (pollStatus == HttpStatusCode.Created) respond(
                        """{"data":{"id":"p1","tripId":"t","kind":"DATE","question":"Kapan berangkat?","createdByMemberId":"m1",
                        "createdByName":"Ayu","createdAt":"$stamp","closesAt":"$stamp","status":"OPEN"}}""",
                        HttpStatusCode.Created, json,
                    ) else respond("""{"error":{"code":"INTERNAL","message":"x"}}""", pollStatus, json)
                } else respond("", HttpStatusCode.NoContent)
            },
        ),
        origin = "http://api.test",
    )

    private fun range(id: String, fromToday: Int, days: Int): PollOptionInput {
        val start = today().plusDays(fromToday)
        return PollOptionInput(id = id, label = "", startDate = start, endDate = start.plusDays(days))
    }

    private suspend fun form(store: InMemoryStore, signedIn: Boolean = true): TripFormViewModel {
        val session = session().also { if (signedIn) it.signIn() }
        return TripFormViewModel(
            null, InMemoryTripRepository(store), InMemoryPreferencesRepository(),
            api = api(), session = session, sync = NoSyncStatus,
        ).also {
            it.onTitleChange("Bali bareng geng kantor")
            it.onDestinationChange("Bali")
            it.prefillPollQuestion("Kapan berangkat?")
        }
    }

    @Test
    fun aGuestCannotLeaveTheDatesOpen() = runTest {
        val vm = form(InMemoryStore(), signedIn = false)
        assertFalse(vm.state.value.canAskDates)
        vm.onDatesUndecided(true)
        assertFalse(vm.state.value.datesUndecided)
    }

    @Test
    fun theSwitchNeedsTwoToFiveRangesFromToday() = runTest {
        val store = InMemoryStore()
        val vm = form(store)
        vm.onDatesUndecided(true)
        vm.savePollOption(range("a", 3, 3))
        vm.save()
        assertTrue(vm.state.value.pollError)
        assertTrue(store.trips.value.isEmpty())

        vm.savePollOption(range("past", -2, 3))
        vm.save()
        assertTrue(vm.state.value.pollError)

        vm.removePollOption("past")
        listOf("b", "c", "d", "e", "f").forEachIndexed { i, id -> vm.savePollOption(range(id, 10 + i * 7, 3)) }
        assertEquals(5, vm.state.value.pollOptions.size) // the sixth is not taken
        assertFalse(vm.state.value.pollError)
    }

    @Test
    fun savingMakesTheTripWithoutDatesThenItsDatePoll() = runTest {
        val store = InMemoryStore()
        val vm = form(store)
        vm.onDatesUndecided(true)
        vm.onPollClosingDays(2)
        vm.savePollOption(range("a", 20, 3))
        vm.savePollOption(range("b", 27, 3))
        val saved = backgroundScope.launch { vm.eventFlow.first { it is TripFormEvent.Saved } }
        vm.save()
        eventually { saved.isCompleted }

        val created = store.trips.value.single()
        assertNull(created.startDate)
        assertNull(created.endDate)
        val post = requests.value.single { it.first.startsWith("POST ") && it.first.endsWith("/trips/${created.id}/polls") }.second
        assertTrue("\"kind\":\"DATE\"" in post, post)
        assertTrue("\"multiChoice\":true" in post, post)
        assertTrue("\"question\":\"Kapan berangkat?\"" in post, post)
        assertEquals(2, Regex("\"startDate\"").findAll(post).count())
    }

    @Test
    fun theTripStaysWhenThePollCannotBeMade() = runTest {
        pollStatus = HttpStatusCode.ServiceUnavailable
        val store = InMemoryStore()
        val vm = form(store)
        vm.onDatesUndecided(true)
        vm.savePollOption(range("a", 20, 3))
        vm.savePollOption(range("b", 27, 3))
        vm.save()
        eventually { vm.state.value.pollFailedTripId != null }
        val id = store.trips.value.single().id
        assertEquals(id, vm.state.value.pollFailedTripId)
        assertFalse(vm.state.value.saving)

        val saved = backgroundScope.launch { vm.eventFlow.first { it == TripFormEvent.Saved(id) } }
        vm.acknowledgePollFailure()
        eventually { saved.isCompleted }
        assertNull(vm.state.value.pollFailedTripId)
    }

    @Test
    fun withTheSwitchOffTheTripKeepsItsDatesAndNoPollIsMade() = runTest {
        val store = InMemoryStore()
        val vm = form(store)
        vm.onDatesChange(LocalDate(2027, 1, 10), LocalDate(2027, 1, 12))
        val saved = backgroundScope.launch { vm.eventFlow.first { it is TripFormEvent.Saved } }
        vm.save()
        eventually { saved.isCompleted }
        assertEquals(LocalDate(2027, 1, 10), store.trips.value.single().startDate)
        assertTrue(requests.value.none { it.first.endsWith("/polls") })
    }

    @Test
    fun editingATripWithoutDatesCanGiveItDates() = runTest {
        val store = InMemoryStore().also { it.trips.value = listOf(trip("u", null, null)) }
        val vm = TripFormViewModel("u", InMemoryTripRepository(store), InMemoryPreferencesRepository())
        eventually { !vm.state.value.loading }
        assertNull(vm.state.value.startDate)
        assertFalse(vm.state.value.canAskDates)
        vm.onDatesChange(LocalDate(2027, 2, 1), LocalDate(2027, 2, 4))
        val saved = backgroundScope.launch { vm.eventFlow.first { it is TripFormEvent.Saved } }
        vm.save()
        eventually { saved.isCompleted }
        assertEquals(LocalDate(2027, 2, 4), store.trips.value.single().endDate)
    }
}
