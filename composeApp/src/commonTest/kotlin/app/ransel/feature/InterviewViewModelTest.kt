package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.destination.DestinationChoice
import app.ransel.feature.interview.InterviewEvent
import app.ransel.feature.interview.InterviewScreen
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.feature.interview.KhasState
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.domain.InterviewStep
import app.ransel.shared.domain.departureSummaryEditStep
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripOriginDto
import app.ransel.shared.dto.TripPlaceDto
import app.ransel.shared.model.DepartureMode
import app.ransel.shared.model.Interest
import app.ransel.shared.model.MainTravelMode
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val bandung = DestinationChoice("Bandung", TripPlaceDto("p-bdg", -6.9175, 107.6191), countryCode = "ID", timeZone = "Asia/Jakarta")
private val tokyo = DestinationChoice("Tokyo", TripPlaceDto("p-tyo", 35.6762, 139.6503), countryCode = "JP", timeZone = "Asia/Tokyo")
private val subang = TripOriginDto("Subang", -6.5694, 107.7606, countryCode = "ID")

private suspend fun <T> eventually(block: () -> T?): T = withContext(Dispatchers.Default) {
    withTimeout(5_000) {
        var value = block()
        while (value == null) { delay(10); value = block() }
        value
    }
}

class InterviewViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val suggestionCalls = mutableListOf<String>()

    private fun vm(
        door: InterviewDoor = InterviewDoor.MY_TRIPS,
        store: InMemoryStore = InMemoryStore(),
        api: RanselApi? = null,
        session: SessionManager? = null,
    ) = InterviewViewModel(door, InMemoryTripRepository(store), InMemoryPreferencesRepository(), api, session).also {
        it.setNameTemplate("Trip ke %s")
    }

    private fun suggestionsApi(answer: (String) -> Pair<HttpStatusCode, String>): RanselApi {
        suggestionCalls.clear()
        return RanselApi(
            createApiHttpClient(
                MockEngine { request ->
                    val url = request.url.toString()
                    if (request.url.encodedPath.endsWith("/explore/suggestions")) {
                        suggestionCalls += url
                        val (status, body) = answer(url)
                        respond(body, status, jsonHeaders)
                    } else {
                        respond("""{"error":{"code":"NOT_FOUND","message":"x"}}""", HttpStatusCode.NotFound, jsonHeaders)
                    }
                },
            ),
            origin = "http://api.test",
        )
    }

    private suspend fun signedInSession(): SessionManager {
        val store = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(store) { error("not used") }.also {
            it.start(AuthSessionDto("a", Instant.parse("2099-01-01T00:00:00Z"), "r", MeDto("u1", "ayu@contoh.id", "Ayu")))
        }
    }

    private fun InterviewViewModel.layar1(choice: DestinationChoice = bandung) {
        onDestinationPicked(choice)
        onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
    }

    @Test
    fun subangToBandungByMotorcycleSkipsW3AndSavesTheAnswers() = runTest {
        val store = InMemoryStore()
        val vm = vm(store = store)
        vm.layar1()
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.ORIGIN), vm.state.value.screen)
        vm.pickOrigin(subang)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.DEPARTURE), vm.state.value.screen)
        vm.chooseDeparture(DepartureMode.OWN_MOTORCYCLE)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen, "W3 is skipped for an own vehicle")
        vm.toggleInterest(Interest.FOOD)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)

        vm.create()
        val created = eventually { store.trips.value.singleOrNull() }
        assertEquals("Trip ke Bandung", created.title)
        assertEquals("Subang", created.origin?.name)
        assertEquals(DepartureMode.OWN_MOTORCYCLE, created.departureMode)
        assertNull(created.localTransport)
        assertEquals(MainTravelMode.MOTORCYCLE, created.mainTravelMode)
        assertEquals("p-bdg", created.destinationPlace?.placeId)
        assertEquals(LocalDate(2026, 12, 20), created.startDate)
    }

    @Test
    fun anAdministrativeCityIsShownAndStoredWithoutThePrefix() = runTest {
        val store = InMemoryStore()
        val vm = vm(store = store)
        vm.onDestinationPicked(DestinationChoice("Administrasi Jakarta Pusat", TripPlaceDto("p-jkp", -6.1805, 106.8284), "ID", "Asia/Jakarta"))
        vm.onDates(LocalDate(2026, 12, 20), LocalDate(2026, 12, 22))
        assertEquals("Jakarta Pusat", vm.state.value.destinationName)
        assertEquals("Trip ke Jakarta Pusat", vm.state.value.tripName)
        vm.next()
        vm.onOriginPicked(DestinationChoice("Kota Administrasi Jakarta Barat", TripPlaceDto("p-jkt", -6.1683, 106.7589), "ID", "Asia/Jakarta"))
        assertEquals("Jakarta Barat", vm.state.value.answers.origin?.name)
        vm.skipInterview()
        vm.create()
        val created = eventually { store.trips.value.singleOrNull() }
        assertEquals("Jakarta Pusat", created.destination)
        assertEquals("Jakarta Barat", created.origin?.name)
    }

    @Test
    fun theCreatedEventSaysWhetherTheUserIsSignedIn() = runTest {
        val vm = vm()
        vm.layar1()
        vm.skipInterviewFromSummaryPath()
        vm.create()
        val event = vm.eventFlow.first()
        assertTrue(event is InterviewEvent.Created && !event.signedIn, "a guest: no Ajak teman")
    }

    /** From Layar 1 to W1, then "Lewati wawancara" to the summary. */
    private fun InterviewViewModel.skipInterviewFromSummaryPath() {
        next()
        skipInterview()
    }

    @Test
    fun skipTheInterviewGoesToTheSummaryFromMyTripsAndToW4FromJelajah() = runTest {
        val mine = vm()
        mine.layar1(); mine.next(); mine.skipInterview()
        assertEquals(InterviewScreen.Summary, mine.state.value.screen)

        val explore = vm(InterviewDoor.EXPLORE)
        explore.layar1(); explore.next(); explore.skipInterview()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), explore.state.value.screen)
    }

    @Test
    fun lewatiStoresNothingAndMovesOn() = runTest {
        val vm = vm()
        vm.layar1(); vm.next()
        vm.skip()
        assertNull(vm.state.value.answers.origin)
        assertEquals(InterviewScreen.Question(InterviewStep.DEPARTURE), vm.state.value.screen)
        // W2 skipped: W3 is still asked (nothing says it is an own vehicle), then W4
        vm.skip()
        assertEquals(InterviewScreen.Question(InterviewStep.LOCAL_TRANSPORT), vm.state.value.screen)
        vm.skip(); vm.skip()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertTrue(vm.state.value.answers.taste.isEmpty)
    }

    @Test
    fun w4FromJelajahCannotBeSkippedAndNeedsAnInterest() = runTest {
        val vm = vm(InterviewDoor.EXPLORE)
        vm.layar1(); vm.next(); vm.skipInterview()
        vm.skip()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen, "no interest yet")
        vm.toggleInterest(Interest.NATURE)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
    }

    @Test
    fun anOverseasTripSkipsW2() = runTest {
        val vm = vm()
        vm.layar1(tokyo)
        vm.next()
        vm.pickOrigin(subang)
        vm.next()
        assertEquals(InterviewScreen.Question(InterviewStep.LOCAL_TRANSPORT), vm.state.value.screen)
    }

    @Test
    fun backOnLayar1AsksOnlyWhenSomethingWasFilledIn() = runTest {
        val empty = vm()
        empty.back()
        assertEquals(InterviewEvent.Leave, empty.eventFlow.first())

        val filled = vm()
        filled.onDestinationPicked(bandung)
        filled.back()
        assertTrue(filled.state.value.leaveDialog)
        filled.dismissLeave()
        assertFalse(filled.state.value.leaveDialog)
    }

    @Test
    fun backGoesToTheQuestionBeforeAndKeepsTheAnswer() = runTest {
        val vm = vm()
        vm.layar1(); vm.next(); vm.pickOrigin(subang); vm.next()
        vm.back()
        assertEquals(InterviewScreen.Question(InterviewStep.ORIGIN), vm.state.value.screen)
        assertEquals("Subang", vm.state.value.answers.origin?.name)
    }

    @Test
    fun modeUbahChangesARowAndBackPutsTheAnswerBack() = runTest {
        val vm = vm()
        vm.layar1(); vm.next(); vm.pickOrigin(subang); vm.next()
        vm.chooseDeparture(DepartureMode.BUS); vm.next()
        vm.skip(); vm.skip()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)

        vm.edit(InterviewStep.DEPARTURE)
        vm.chooseDeparture(DepartureMode.OWN_CAR)
        vm.back()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertEquals(DepartureMode.BUS, vm.state.value.answers.departure, "cancelled")

        vm.edit(InterviewStep.DEPARTURE)
        vm.chooseDeparture(DepartureMode.OWN_CAR)
        vm.next()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        assertEquals(DepartureMode.OWN_CAR, vm.state.value.answers.departure, "saved")
        assertNull(vm.state.value.editing)
    }

    @Test
    fun theNameDefaultsToTripToTheDestinationUntilTheUserTypesOne() = runTest {
        val vm = vm()
        vm.layar1()
        assertEquals("Trip ke Bandung", vm.state.value.tripName)
        vm.onNameChange("Liburan Dago")
        assertEquals("Liburan Dago", vm.state.value.tripName)
    }

    @Test
    fun aGuestOnW4KeepsGenericChipsAndNeverAsksTheServer() = runTest {
        val api = suggestionsApi { HttpStatusCode.OK to """{"data":{"interests":["Malioboro"]}}""" }
        val vm = vm(InterviewDoor.EXPLORE, api = api)
        vm.layar1()
        vm.next()
        vm.skipInterview()
        assertEquals(InterviewScreen.Question(InterviewStep.TASTE), vm.state.value.screen)
        delay(50)
        assertTrue(suggestionCalls.isEmpty(), "guest path must not hit GET /explore/suggestions: $suggestionCalls")
        assertEquals(KhasState.Hidden, vm.state.value.khas)
    }

    @Test
    fun aSignedInSessionLoadsDestinationChipsForThePickedPlace() = runTest {
        val api = suggestionsApi { HttpStatusCode.OK to """{"data":{"interests":["Braga","Gedung Sate"]}}""" }
        val vm = vm(InterviewDoor.EXPLORE, api = api, session = signedInSession())
        vm.layar1()
        val khas = eventually { vm.state.value.khas as? KhasState.Loaded }
        assertEquals(listOf("Braga", "Gedung Sate"), khas.interests)
        assertEquals("Bandung", khas.destination)
        assertTrue(suggestionCalls.any { "destination=Bandung" in it }, suggestionCalls.toString())
    }

    @Test
    fun aFailedSuggestionsCallFallsBackToGenericChips() = runTest {
        val api = suggestionsApi {
            HttpStatusCode.Unauthorized to """{"error":{"code":"UNAUTHORIZED","message":"x"}}"""
        }
        val vm = vm(api = api, session = signedInSession())
        vm.layar1()
        eventually { if (suggestionCalls.isNotEmpty() && vm.state.value.khas != KhasState.Loading) true else null }
        assertEquals(KhasState.Hidden, vm.state.value.khas)
    }

    @Test
    fun tappingTheLeavingSummaryRowOpensW2() = runTest {
        val vm = vm()
        vm.layar1()
        vm.next()
        vm.pickOrigin(subang)
        vm.next()
        vm.chooseDeparture(DepartureMode.OWN_MOTORCYCLE)
        vm.next()
        vm.skip()
        assertEquals(InterviewScreen.Summary, vm.state.value.screen)
        vm.edit(vm.state.value.answers.departureSummaryEditStep())
        assertEquals(InterviewScreen.Question(InterviewStep.DEPARTURE), vm.state.value.screen)
        assertEquals(InterviewStep.DEPARTURE, vm.state.value.editing)
    }
}
