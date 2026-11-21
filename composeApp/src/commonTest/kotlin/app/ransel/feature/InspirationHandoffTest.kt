package app.ransel.feature

import app.ransel.data.analytics.AnalyticsEvent
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.InspirationInterviewViewModel
import app.ransel.feature.inspiration.InspirationResultsEvent
import app.ransel.feature.inspiration.InspirationResultsViewModel
import app.ransel.feature.interview.InterviewPrefill
import app.ransel.feature.interview.InterviewViewModel
import app.ransel.shared.domain.InterviewDoor
import app.ransel.shared.dto.*
import app.ransel.shared.model.InspirationChipKey
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.InspirationStatus
import app.ransel.shared.model.Interest
import app.ransel.stopTestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*
import kotlin.time.Instant

/** Phase B of Explore Inspirasi: "Bikin trip dari sini", "Kabari saya" and the tracking events. */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationHandoffTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private class Repo(var session: InspirationSessionDto) : InspirationRepository {
        val buddyRequests = mutableListOf<BuddyInterestRequest>()
        var answerResult: ApiResult<InspirationSessionDto> = ApiResult.Success(session)
        override suspend fun start(request: StartInspirationRequest) = ApiResult.Success(session)
        override suspend fun get(id: String) = ApiResult.Success(session)
        override suspend fun answer(id: String, request: InspirationAnswerRequest) = answerResult
        override suspend fun finish(id: String) = answerResult
        override suspend fun askAgain(id: String) = ApiResult.Success(session)
        override suspend fun editChip(id: String, request: InspirationEditChipRequest) = ApiResult.Success(session)
        override suspend fun saved() = ApiResult.Success(SavedIdeasResponse(emptyList()))
        override suspend fun save(request: SaveIdeaRequest) = ApiResult.Success(
            SaveIdeaResponse(SavedIdeaDto("idea-1", card("c1"), savedAt = at, refreshedAt = at)),
        )
        override suspend fun delete(id: String) = ApiResult.Success(Unit)
        override suspend fun undo(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun buddy(request: BuddyInterestRequest): ApiResult<Unit> { buddyRequests += request; return ApiResult.Success(Unit) }
    }

    @Test
    fun theProfileInterestsPreselectSeleramu() = runTest {
        val vm = InspirationResultsViewModel("sid", Repo(charged(interestChip = "Fotografi + Belanja"))); runCurrent()
        assertEquals(setOf(Interest.PHOTO, Interest.SHOPPING), vm.interestsForTrip())
        // the English labels too ("Sports & outdoors")
        val en = InspirationResultsViewModel("sid", Repo(charged(interestChip = "Sports & outdoors"))); runCurrent()
        assertEquals(setOf(Interest.ADVENTURE), en.interestsForTrip())
    }

    @Test
    fun kabariSayaRecordsTheSessionAndItsFirstInterest() = runTest {
        val repo = Repo(charged(interestChip = "Olahraga & outdoor"))
        val analytics = FakeAnalytics()
        val vm = InspirationResultsViewModel("sid", repo, analytics); runCurrent()
        vm.notifyBuddy(); runCurrent()
        assertEquals(listOf(BuddyInterestRequest("sid", Interest.ADVENTURE)), repo.buddyRequests)
        assertEquals(InspirationResultsEvent.BuddyNoted(recorded = true), vm.eventFlow.first())
        assertTrue(AnalyticsEvent.BuddyInterest in analytics.events)
    }

    @Test
    fun resultsTrackCardsTripsSavesAndPaidAskAgain() = runTest {
        val analytics = FakeAnalytics()
        val vm = InspirationResultsViewModel("sid", Repo(charged(freeLeft = 0)), analytics); runCurrent()
        vm.cardOpened(card("s1", surprise = true))
        vm.makeTripTapped()
        vm.save(card("c1")); runCurrent()
        vm.askAgain(); runCurrent()
        assertEquals(
            listOf(
                AnalyticsEvent.InspirationCardTapped(surprise = true),
                AnalyticsEvent.InspirationMakeTrip,
                AnalyticsEvent.InspirationSaved,
                AnalyticsEvent.InspirationAskAgain(paid = true),
            ),
            analytics.events,
        )
    }

    @Test
    fun theInterviewTracksStepsEnoughAndTheFinishedCards() = runTest {
        val question = InspirationQuestionDto(
            InspirationSlot.DETAIL, "Q", listOf(InspirationOptionDto("a", "A"), InspirationOptionDto("b", "B"), InspirationOptionDto("c", "C")),
            canFinishEarly = true,
        )
        val repo = Repo(InspirationSessionDto("sid", InspirationStatus.ACTIVE, question, expiresAt = at))
        val analytics = FakeAnalytics()
        val vm = InspirationInterviewViewModel("sid", repo, analytics); runCurrent()
        vm.setOtherText("pantai sepi")
        vm.answerOther(); runCurrent()
        repo.answerResult = ApiResult.Success(charged())
        vm.enough(); runCurrent()
        assertEquals(
            listOf(
                AnalyticsEvent.InspirationStep(InspirationSlot.DETAIL, other = true),
                AnalyticsEvent.InspirationEnough,
                AnalyticsEvent.InspirationFinished(cards = 2),
            ),
            analytics.events,
        )
    }

    @Test
    fun bikinTripPrefillsTheDestinationAndInterestsOnce() = runTest {
        val vm = InterviewViewModel(InterviewDoor.MY_TRIPS, InMemoryTripRepository(InMemoryStore()), InMemoryPreferencesRepository(), null, null)
        vm.setNameTemplate("Trip ke %s")
        val prefill = InterviewPrefill.fromRoute("Tanjung Puting, Kalteng", "id", "NATURE,PHOTO,NOT_AN_INTEREST")!!
        assertEquals(setOf(Interest.NATURE, Interest.PHOTO), prefill.interests)
        vm.applyPrefill(prefill); runCurrent()
        assertEquals("Tanjung Puting, Kalteng", vm.state.value.destinationName)
        assertEquals("ID", vm.state.value.destinationChoice?.countryCode)
        assertEquals(setOf(Interest.NATURE, Interest.PHOTO), vm.state.value.answers.taste.interests)
        // the dates stay open: nothing was picked
        assertNull(vm.state.value.answers.startDate)
        // a second call (recomposition) changes nothing the user did since
        vm.toggleInterest(Interest.PHOTO)
        vm.applyPrefill(prefill)
        assertEquals(setOf(Interest.NATURE), vm.state.value.answers.taste.interests)
        assertNull(InterviewPrefill.fromRoute("  ", null, "NATURE"))
    }

    private companion object {
        val at: Instant = Instant.parse("2026-11-21T00:00:00Z")
        fun card(id: String, surprise: Boolean = false) = InspirationCardDto(id, "Activity $id", "Region $id", why = "Fits", surprise = surprise)
        fun charged(interestChip: String = "Fotografi", freeLeft: Int = 1) = InspirationSessionDto(
            "sid", InspirationStatus.CHARGED,
            result = InspirationResultDto(
                profile = listOf(InspirationChipDto(InspirationChipKey.INTEREST, interestChip)),
                cards = listOf(card("c1")),
                surprise = card("s1", surprise = true),
                createdAt = at,
            ),
            askAgainFreeLeft = freeLeft, extraCost = 1, expiresAt = at,
        )
    }
}
