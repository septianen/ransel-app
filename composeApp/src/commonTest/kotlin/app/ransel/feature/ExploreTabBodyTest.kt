package app.ransel.feature

import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.ExploreTabBody
import app.ransel.feature.inspiration.SavedIdeasViewModel
import app.ransel.feature.inspiration.exploreTabBody
import app.ransel.shared.dto.BuddyInterestRequest
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationChipDto
import app.ransel.shared.dto.InspirationEditChipRequest
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.dto.SaveIdeaRequest
import app.ransel.shared.dto.SavedIdeaDto
import app.ransel.shared.dto.SavedIdeasResponse
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.model.InspirationChipKey
import app.ransel.util.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.ransel.stopTestMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreTabBodyTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private class Repo(var ideas: List<SavedIdeaDto>) : InspirationRepository {
        override suspend fun start(request: StartInspirationRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun get(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun answer(id: String, request: InspirationAnswerRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun finish(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun back(id: String) = ApiResult.Failure(ApiFailure("CONFLICT", "none"))
        override suspend fun askAgain(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun editChip(id: String, request: InspirationEditChipRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun saved() = ApiResult.Success(SavedIdeasResponse(ideas))
        override suspend fun save(request: SaveIdeaRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun delete(id: String): ApiResult<Unit> {
            ideas = ideas.filterNot { it.id == id }
            return ApiResult.Success(Unit)
        }
        override suspend fun restore(id: String) = ApiResult.Success(Unit)
        override suspend fun undo(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun buddy(request: BuddyInterestRequest) = ApiResult.Success(Unit)
    }

    @Test fun emptySavedIdeasShowsTheAskAiForm() = runTest {
        val vm = SavedIdeasViewModel(Repo(emptyList()))
        vm.load()
        runCurrent()
        assertEquals(ExploreTabBody.Input, exploreTabBody(vm.state.value))
    }

    @Test fun atLeastOneSavedIdeaShowsTheList() = runTest {
        val vm = SavedIdeasViewModel(Repo(listOf(idea())))
        vm.load()
        runCurrent()
        assertEquals(ExploreTabBody.SavedList, exploreTabBody(vm.state.value))
        assertEquals(1, vm.state.value.ideas.size)
    }

    @Test fun unsavingTheLastIdeaFallsBackToTheAskAiForm() = runTest {
        val repo = Repo(listOf(idea()))
        val vm = SavedIdeasViewModel(repo)
        vm.load()
        runCurrent()
        assertEquals(ExploreTabBody.SavedList, exploreTabBody(vm.state.value))
        vm.remove(vm.state.value.ideas.single())
        runCurrent()
        assertTrue(vm.state.value.ideas.isEmpty())
        assertEquals(ExploreTabBody.Input, exploreTabBody(vm.state.value))
    }

    @Test fun firstLoadShowsTheListSkeleton() {
        assertEquals(ExploreTabBody.Loading, exploreTabBody(app.ransel.feature.inspiration.SavedIdeasUiState()))
    }

    private companion object {
        fun idea() = SavedIdeaDto(
            id = "saved-1",
            card = InspirationCardDto("c1", "Activity", "Region", why = "Reason"),
            profile = listOf(InspirationChipDto(InspirationChipKey.LEVEL, "Beginner")),
            savedAt = now(),
            refreshedAt = now(),
        )
    }
}
