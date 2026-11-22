package app.ransel.feature

import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.InspirationResultsEvent
import app.ransel.feature.inspiration.InspirationResultsViewModel
import app.ransel.shared.dto.*
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.InspirationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import app.ransel.shared.api.ErrorCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.ransel.stopTestMain
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InspirationResultsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private class Repo : InspirationRepository {
        var getResult: ApiResult<InspirationSessionDto> = ApiResult.Success(session())
        var againResult: ApiResult<InspirationSessionDto> = ApiResult.Success(session(InspirationStatus.ACTIVE, question = question()))
        var answerResult: ApiResult<InspirationSessionDto> = ApiResult.Success(session())
        var editResult: ApiResult<InspirationSessionDto> = ApiResult.Success(session())
        var saveResult: ApiResult<SaveIdeaResponse> = ApiResult.Success(saveResponse())
        var editRequest: InspirationEditChipRequest? = null
        var undoIds = mutableListOf<String>()
        var getCalls = 0
        var lastSaveRequest: SaveIdeaRequest? = null
        override suspend fun start(request: StartInspirationRequest) = getResult
        override suspend fun get(id: String): ApiResult<InspirationSessionDto> { getCalls++; return getResult }
        override suspend fun answer(id: String, request: InspirationAnswerRequest) = answerResult
        override suspend fun finish(id: String) = answerResult
        override suspend fun back(id: String): ApiResult<InspirationSessionDto> = ApiResult.Failure(ApiFailure("CONFLICT", "none"))
        override suspend fun askAgain(id: String) = againResult
        override suspend fun editChip(id: String, request: InspirationEditChipRequest): ApiResult<InspirationSessionDto> { editRequest = request; return editResult }
        override suspend fun saved() = ApiResult.Success(SavedIdeasResponse(emptyList()))
        override suspend fun save(request: SaveIdeaRequest): ApiResult<SaveIdeaResponse> { lastSaveRequest = request; return saveResult }
        override suspend fun delete(id: String) = ApiResult.Success(Unit)
        override suspend fun undo(id: String): ApiResult<Unit> { undoIds += id; return ApiResult.Success(Unit) }
        override suspend fun restore(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun buddy(request: BuddyInterestRequest) = ApiResult.Success(Unit)
    }

    @Test fun loadsSessionThroughGet() = runTest {
        val repo = Repo(); val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        assertEquals(1, repo.getCalls)
        assertEquals("sid", vm.state.value.session?.id)
        assertEquals(listOf("c1", "s1"), vm.state.value.cards.map { it.card.id })
    }

    @Test fun askAgainAnswerAppendsCardsAndKeepsEarlierOnes() = runTest {
        val repo = Repo()
        val old = listOf(card("c1"), card("c2"))
        repo.getResult = ApiResult.Success(session(result = result(old)))
        repo.againResult = ApiResult.Success(session(InspirationStatus.ACTIVE, result(old), question()))
        repo.answerResult = ApiResult.Success(session(InspirationStatus.CHARGED, result(old + card("c3"))))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.askAgain(); assertEquals("q1", vm.state.value.askAgainQuestion?.options?.first()?.id)
        vm.answer("q1"); runCurrent()
        assertEquals(listOf("c1", "c2", "c3"), vm.state.value.cards.map { it.card.id })
        assertEquals(2, vm.state.value.appendDividerAt)
    }

    @Test fun freeLeftAndExtraCostProvidePriceInputs() = runTest {
        val repo = Repo(); val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        assertEquals(1, vm.state.value.askAgainFreeLeft)
        assertNull(vm.state.value.askAgainPaidCost)
        repo.againResult = ApiResult.Success(session(InspirationStatus.ACTIVE, question = question(), freeLeft = 0, extraCost = 4))
        vm.askAgain(); runCurrent()
        assertEquals(0, vm.state.value.askAgainFreeLeft)
        assertEquals(4, vm.state.value.askAgainPaidCost)
    }

    @Test fun aFailedChipEditKeepsTheSheetAndShowsASnackbar() = runTest {
        val repo = Repo()
        repo.editResult = ApiResult.Failure(ApiFailure(ErrorCode.AI_UNAVAILABLE, "down", 503))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.openChipEdit(InspirationChipDto(app.ransel.shared.model.InspirationChipKey.DURATION, "Sampai 3 hari"))
        vm.setChipEditValue("4-7 hari")
        vm.editChip(); runCurrent()
        assertTrue(vm.state.value.chipEditOpen)
        assertFalse(vm.state.value.chipEditLoading)
        assertFalse(vm.state.value.askAgainOpen, "not the Tanya lagi sheet")
        assertEquals(listOf("c1", "s1"), vm.state.value.cards.map { it.card.id })
        assertEquals(InspirationResultsEvent.ChipEditFailed, vm.eventFlow.first())
    }

    @Test fun askAgainWithoutCreditsClosesTheSheet() = runTest {
        val repo = Repo()
        repo.againResult = ApiResult.Failure(ApiFailure(ErrorCode.INSUFFICIENT_CREDITS, "No credits", 402))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.askAgain(); runCurrent()
        assertFalse(vm.state.value.askAgainOpen)
        assertFalse(vm.state.value.askAgainLoading)
        assertEquals(InspirationResultsEvent.OutOfCredits, vm.eventFlow.first())
    }

    @Test fun saveSuccessEmitsSavedSnackbarEvent() = runTest {
        val repo = Repo(); val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        val event = async { vm.eventFlow.first() }
        vm.save(card("c1")); runCurrent()
        assertEquals(InspirationResultsEvent.Saved(false, "saved-1", "c1"), event.await())
        assertEquals("sid", repo.lastSaveRequest?.sessionId)
        assertEquals("c1", repo.lastSaveRequest?.cardId)
        assertContains(vm.state.value.savedCardIds, "c1")
    }

    @Test fun oldestRemovalEmitsUndoEventAndUndoCallsRepository() = runTest {
        val repo = Repo(); repo.saveResult = ApiResult.Success(saveResponse(removedOldestId = "oldest"))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        val event = async { vm.eventFlow.first() }
        vm.save(card("c1")); runCurrent()
        val saved = event.await() as InspirationResultsEvent.Saved
        assertTrue(saved.removedOldest)
        vm.undoSave(saved.newIdeaId); runCurrent()
        assertEquals(listOf("saved-1"), repo.undoIds)
    }

    @Test fun insufficientCreditsEmitsOutOfCredits() = runTest {
        val repo = Repo(); repo.againResult = ApiResult.Failure(ApiFailure("INSUFFICIENT_CREDITS", "low", 402))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        val event = async { vm.eventFlow.first() }
        vm.askAgain(); runCurrent()
        assertEquals(InspirationResultsEvent.OutOfCredits, event.await())
    }

    @Test fun aiUnavailableIsInlineAndDoesNotCharge() = runTest {
        val repo = Repo(); repo.againResult = ApiResult.Failure(ApiFailure("AI_UNAVAILABLE", "offline model", 503))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.askAgain(); runCurrent()
        assertTrue(vm.state.value.inlineError)
        assertEquals("c1", vm.state.value.cards.first().card.id)
    }

    @Test fun chipEditReplacesCardsAndClearsAppendDivider() = runTest {
        val repo = Repo()
        repo.getResult = ApiResult.Success(session(result = result(listOf(card("old-a"), card("old-b")))))
        repo.editResult = ApiResult.Success(session(result = result(listOf(card("new-a"), card("new-b")))))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.openChipEdit(InspirationChipDto(app.ransel.shared.model.InspirationChipKey.LEVEL, "Pemula"))
        vm.setChipEditValue("Menengah")
        vm.editChip(); runCurrent()
        assertEquals(listOf("new-a", "new-b"), vm.state.value.cards.map { it.card.id })
        assertNull(vm.state.value.appendDividerAt)
        assertEquals(app.ransel.shared.model.InspirationChipKey.LEVEL, repo.editRequest?.key)
        assertEquals("Menengah", repo.editRequest?.value)
    }

    @Test fun chipEditReturningQuestionShowsQuestionInSheet() = runTest {
        val repo = Repo()
        repo.editResult = ApiResult.Success(session(InspirationStatus.ACTIVE, result = null, question = question()))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.openChipEdit(InspirationChipDto(app.ransel.shared.model.InspirationChipKey.BUDGET, "Sedang"))
        vm.setChipEditValue("Mewah")
        vm.editChip(); runCurrent()
        assertTrue(vm.state.value.chipEditOpen)
        assertEquals(question(), vm.state.value.chipEditQuestion)
    }

    @Test fun chipEditInsufficientCreditsClosesSheet() = runTest {
        val repo = Repo()
        repo.editResult = ApiResult.Failure(ApiFailure(ErrorCode.INSUFFICIENT_CREDITS, "No credits", 402))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        vm.openChipEdit(InspirationChipDto(app.ransel.shared.model.InspirationChipKey.BUDGET, "Sedang"))
        vm.setChipEditValue("Mewah")
        val event = async { vm.eventFlow.first() }
        vm.editChip(); runCurrent()
        assertFalse(vm.state.value.chipEditOpen)
        assertEquals(InspirationResultsEvent.OutOfCredits, event.await())
    }

    @Test fun saveFailureEmitsSnackbarAndKeepsFeed() = runTest {
        val repo = Repo()
        repo.saveResult = ApiResult.Failure(ApiFailure("NETWORK_UNAVAILABLE", "offline"))
        val vm = InspirationResultsViewModel("sid", repo); runCurrent()
        val event = async { vm.eventFlow.first() }
        vm.save(card("c1")); runCurrent()
        assertEquals(InspirationResultsEvent.SaveFailed, event.await())
        assertFalse(vm.state.value.inlineError)
        assertEquals("c1", vm.state.value.cards.first().card.id)
    }

    private companion object {
        fun card(id: String, surprise: Boolean = false) = InspirationCardDto(id, "Activity $id", "Region $id", why = "Fits your interests", surprise = surprise)
        fun result(cards: List<InspirationCardDto>, surprise: InspirationCardDto? = null) = InspirationResultDto(
            profile = listOf(InspirationChipDto(app.ransel.shared.model.InspirationChipKey.INTEREST, "Photography")),
            cards = cards,
            surprise = surprise,
            createdAt = Instant.parse("2026-11-21T00:00:00Z"),
        )
        fun session(
            status: InspirationStatus = InspirationStatus.CHARGED,
            result: InspirationResultDto? = result(listOf(card("c1")), card("s1", true)),
            question: InspirationQuestionDto? = null,
            freeLeft: Int = 1,
            extraCost: Int = 2,
        ) = InspirationSessionDto("sid", status, question, result, freeLeft, 3, extraCost, expiresAt = Instant.parse("2026-11-22T00:00:00Z"))
        fun question() = InspirationQuestionDto(
            InspirationSlot.OPTIONAL,
            "Question?",
            listOf(InspirationOptionDto("q1", "Option 1"), InspirationOptionDto("q2", "Option 2"), InspirationOptionDto("q3", "Option 3")),
        )
        fun saveResponse(removedOldestId: String? = null) = SaveIdeaResponse(
            SavedIdeaDto("saved-1", cardForSave(), savedAt = Instant.parse("2026-11-21T00:00:00Z"), refreshedAt = Instant.parse("2026-11-21T00:00:00Z")),
            removedOldestId,
        )
        fun cardForSave() = InspirationCardDto("c1", "Activity", "Region", why = "Reason")
    }
}
