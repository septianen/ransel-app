package app.ransel.feature

import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.SavedIdeasEvent
import app.ransel.feature.inspiration.SavedIdeasViewModel
import app.ransel.shared.dto.*
import app.ransel.shared.model.InspirationChipKey
import app.ransel.util.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.ransel.stopTestMain
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SavedIdeasViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private class Repo : InspirationRepository {
        var ideas: List<SavedIdeaDto> = listOf(idea())
        var savedCalls = 0
        var deletedIds = mutableListOf<String>()
        var restoredIds = mutableListOf<String>()
        var refreshCalls = 0
        var refreshResult: ApiResult<SavedIdeaDto> = ApiResult.Success(idea().copy(card = idea().card.copy(activity = "Updated activity")))
        var refreshFailure: ApiFailure? = null
        override suspend fun start(request: StartInspirationRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun get(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun answer(id: String, request: InspirationAnswerRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun finish(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun back(id: String): ApiResult<InspirationSessionDto> = ApiResult.Failure(ApiFailure("CONFLICT", "none"))
        override suspend fun askAgain(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun editChip(id: String, request: InspirationEditChipRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun saved(): ApiResult<SavedIdeasResponse> { savedCalls++; return ApiResult.Success(SavedIdeasResponse(ideas)) }
        override suspend fun save(request: SaveIdeaRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun delete(id: String): ApiResult<Unit> { deletedIds += id; ideas = ideas.filterNot { it.id == id }; return ApiResult.Success(Unit) }
        override suspend fun restore(id: String): ApiResult<Unit> { restoredIds += id; ideas = listOf(idea(id)) + ideas; return ApiResult.Success(Unit) }
        override suspend fun undo(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String): ApiResult<SavedIdeaDto> {
            refreshCalls++
            return refreshFailure?.let { ApiResult.Failure(it) } ?: refreshResult
        }
        override suspend fun buddy(request: BuddyInterestRequest) = ApiResult.Success(Unit)
    }

    @Test fun loadsSavedIdeas() = runTest {
        val repo = Repo(); val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        assertEquals(1, repo.savedCalls)
        assertEquals(listOf("saved-1"), vm.state.value.ideas.map { it.id })
    }

    @Test fun removeAndUndoRestoresBySavedId() = runTest {
        val repo = Repo(); val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        val event = async { vm.eventFlow.first() }
        vm.remove(repo.ideas.single()); runCurrent()
        assertEquals(SavedIdeasEvent.Removed("saved-1"), event.await())
        assertTrue(vm.state.value.ideas.isEmpty())
        vm.undoRemove("saved-1"); runCurrent()
        assertEquals(listOf("saved-1"), repo.restoredIds)
        assertEquals(listOf("saved-1"), vm.state.value.ideas.map { it.id })
    }

    @Test fun identifiesIdeasOlderThanSixtyDaysAsStale() = runTest {
        val repo = Repo().also { it.ideas = listOf(idea(refreshedAt = now() - 70.days)) }
        val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        assertEquals(setOf("saved-1"), vm.state.value.staleIds)
    }

    @Test fun futureRefreshTimeDisablesRefreshWithoutRequest() = runTest {
        val repo = Repo().also { it.ideas = listOf(idea(canRefreshAt = now() + 1.hours)) }
        val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        vm.refresh(vm.state.value.ideas.single()); runCurrent()
        assertEquals(0, repo.refreshCalls)
        assertEquals(setOf("saved-1"), vm.state.value.waitingRefreshIds)
    }

    @Test fun refreshSuccessReplacesTheSavedRow() = runTest {
        val repo = Repo()
        repo.refreshResult = ApiResult.Success(idea().copy(card = idea().card.copy(activity = "Refreshed activity"), refreshedAt = now()))
        val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        vm.refresh(vm.state.value.ideas.single()); runCurrent()
        assertEquals("Refreshed activity", vm.state.value.ideas.single().card.activity)
        assertTrue(vm.state.value.staleIds.isEmpty())
    }

    @Test fun rateLimitShowsRefreshWaitLine() = runTest {
        val repo = Repo(); repo.refreshFailure = ApiFailure("RATE_LIMITED", "wait", 429)
        val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        vm.refresh(vm.state.value.ideas.single()); runCurrent()
        assertEquals(setOf("saved-1"), vm.state.value.waitingRefreshIds)
        assertEquals(1, repo.refreshCalls)
    }

    @Test fun emptyListHasLoadedEmptyState() = runTest {
        val repo = Repo().also { it.ideas = emptyList() }
        val vm = SavedIdeasViewModel(repo); vm.load(); runCurrent()
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.state.value.ideas.isEmpty())
    }

    private companion object {
        fun idea(
            id: String = "saved-1",
            refreshedAt: Instant = now(),
            canRefreshAt: Instant? = null,
        ) = SavedIdeaDto(
            id = id,
            card = InspirationCardDto("c1", "Activity", "Region", why = "Reason", durationDays = 3),
            profile = listOf(InspirationChipDto(InspirationChipKey.LEVEL, "Beginner")),
            savedAt = now(),
            refreshedAt = refreshedAt,
            canRefreshAt = canRefreshAt,
        )
    }
}
