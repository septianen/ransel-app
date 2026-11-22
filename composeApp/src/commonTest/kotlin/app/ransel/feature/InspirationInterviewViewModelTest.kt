package app.ransel.feature

import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.*
import app.ransel.shared.dto.*
import app.ransel.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import app.ransel.stopTestMain
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InspirationInterviewViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val q = InspirationQuestionDto(InspirationSlot.DISTANCE, "Distance?", listOf(InspirationOptionDto("same_city", "City"), InspirationOptionDto("domestic", "Domestic"), InspirationOptionDto("abroad", "Abroad")), canFinishEarly = true)
    private fun session(status: InspirationStatus = InspirationStatus.ACTIVE, question: InspirationQuestionDto? = q) = InspirationSessionDto("sid", status, question, expiresAt = Instant.parse("2026-12-01T00:00:00Z"))
    private class Repo : InspirationRepository {
        var getResult: ApiResult<InspirationSessionDto> = ApiResult.Success(InspirationInterviewViewModelTest().session())
        var answerResult: ApiResult<InspirationSessionDto> = ApiResult.Success(InspirationInterviewViewModelTest().session())
        var finishResult: ApiResult<InspirationSessionDto> = ApiResult.Success(InspirationInterviewViewModelTest().session(InspirationStatus.CHARGED, null))
        var answerRequest: InspirationAnswerRequest? = null
        var finishCalls = 0
        override suspend fun start(request: StartInspirationRequest) = getResult
        override suspend fun get(id: String) = getResult
        override suspend fun answer(id: String, request: InspirationAnswerRequest): ApiResult<InspirationSessionDto> { answerRequest = request; return answerResult }
        override suspend fun finish(id: String): ApiResult<InspirationSessionDto> { finishCalls++; return finishResult }
        override suspend fun askAgain(id: String) = getResult
        override suspend fun editChip(id: String, request: InspirationEditChipRequest) = getResult
        override suspend fun saved() = ApiResult.Success(SavedIdeasResponse(emptyList()))
        override suspend fun save(request: SaveIdeaRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun delete(id: String) = ApiResult.Success(Unit)
        override suspend fun undo(id: String) = ApiResult.Success(Unit)
        override suspend fun restore(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun buddy(request: BuddyInterestRequest) = ApiResult.Success(Unit)
    }

    @Test fun resumesByGettingSessionOnOpen() = runTest {
        val repo = Repo(); val vm = InspirationInterviewViewModel("sid", repo)
        runCurrent()
        assertEquals("sid", vm.state.value.session?.id)
        assertEquals(q, vm.state.value.question)
    }

    @Test fun answersOptionAndOtherText() = runTest {
        val repo = Repo(); val vm = InspirationInterviewViewModel("sid", repo); runCurrent()
        vm.answer("same_city"); assertEquals("same_city", repo.answerRequest?.optionId)
        vm.setOtherText("nearby"); vm.answerOther(); assertEquals("nearby", repo.answerRequest?.otherText)
    }

    @Test fun errorKeepsQuestionAndRetryRecovers() = runTest {
        val repo = Repo(); val vm = InspirationInterviewViewModel("sid", repo); runCurrent()
        repo.answerResult = ApiResult.Failure(ApiFailure("NETWORK_UNAVAILABLE", "offline"))
        vm.answer("same_city"); assertEquals(q, vm.state.value.question); assertTrue(vm.state.value.inlineError)
        repo.getResult = ApiResult.Success(session()); vm.retry(); assertFalse(vm.state.value.inlineError)
    }

    @Test fun canFinishEarlyIsReflectedInQuestion() = runTest {
        val vm = InspirationInterviewViewModel("sid", Repo()); runCurrent()
        assertTrue(vm.state.value.question?.canFinishEarly == true)
        vm.state.value.question?.let { assertFalse(it.copy(canFinishEarly = false).canFinishEarly) }
    }

    @Test fun readyAutomaticallyFinishes() = runTest {
        val repo = Repo(); repo.getResult = ApiResult.Success(session(InspirationStatus.READY, null))
        InspirationInterviewViewModel("sid", repo); runCurrent()
        assertEquals(1, repo.finishCalls)
    }

    @Test fun chargedEmitsResultsNavigation() = runTest {
        val repo = Repo(); repo.getResult = ApiResult.Success(session(InspirationStatus.CHARGED, null))
        val vm = InspirationInterviewViewModel("sid", repo)
        assertEquals(InspirationInterviewEvent.Results, vm.eventFlow.first())
    }

    @Test fun insufficientCreditsEmitsOutOfCredits() = runTest {
        val repo = Repo(); repo.finishResult = ApiResult.Failure(ApiFailure("INSUFFICIENT_CREDITS", "low", 402))
        val vm = InspirationInterviewViewModel("sid", repo); runCurrent()
        val event = async { vm.eventFlow.first() }; vm.finish(); runCurrent()
        assertEquals(InspirationInterviewEvent.OutOfCredits, event.await())
    }

    @Test fun notFoundShowsLostSession() = runTest {
        val repo = Repo(); repo.getResult = ApiResult.Failure(ApiFailure("NOT_FOUND", "gone", 404))
        val vm = InspirationInterviewViewModel("sid", repo); runCurrent()
        assertTrue(vm.state.value.lostSession)
        assertFalse(vm.state.value.inlineError)
    }
}
