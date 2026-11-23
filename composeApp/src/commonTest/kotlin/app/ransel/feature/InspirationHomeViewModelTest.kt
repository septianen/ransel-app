package app.ransel.feature

import app.ransel.data.credit.CreditsStore
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.inspiration.InspirationSessionStore
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.feature.inspiration.InspirationHomeViewModel
import app.ransel.feature.inspiration.InspirationStartState
import app.ransel.feature.inspiration.canAskAi
import app.ransel.shared.dto.*
import app.ransel.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.ransel.stopTestMain
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InspirationHomeViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private class FakeRepository(var result: ApiResult<InspirationSessionDto> = ApiResult.Failure(ApiFailure("x", "x"))) : InspirationRepository {
        var request: StartInspirationRequest? = null
        override suspend fun start(request: StartInspirationRequest) = this.request.let { this.request = request; result }
        override suspend fun get(id: String) = result
        override suspend fun answer(id: String, request: InspirationAnswerRequest) = result
        override suspend fun finish(id: String) = result
        override suspend fun back(id: String): ApiResult<InspirationSessionDto> = ApiResult.Failure(ApiFailure("CONFLICT", "none"))
        override suspend fun askAgain(id: String) = result
        override suspend fun editChip(id: String, request: InspirationEditChipRequest) = result
        override suspend fun saved() = ApiResult.Success(SavedIdeasResponse(emptyList()))
        override suspend fun save(request: SaveIdeaRequest) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun delete(id: String) = ApiResult.Success(Unit)
        override suspend fun undo(id: String) = ApiResult.Success(Unit)
        override suspend fun restore(id: String) = ApiResult.Success(Unit)
        override suspend fun refresh(id: String) = ApiResult.Failure(ApiFailure("x", "x"))
        override suspend fun buddy(request: BuddyInterestRequest) = ApiResult.Success(Unit)
    }
    private fun session(id: String = "session-1") = InspirationSessionDto(id, InspirationStatus.ACTIVE, expiresAt = Instant.parse("2026-11-22T00:00:00Z"))
    private fun vm(repo: FakeRepository, credits: CreditsStore = CreditsStore()) = InspirationHomeViewModel(repo, InspirationSessionStore(InMemoryPreferencesRepository()), credits)

    @Test fun chipLimitAndTextLimit() = runTest {
        val viewModel = vm(FakeRepository())
        Interest.entries.take(5).forEach(viewModel::toggleInterest)
        viewModel.toggleInterest(Interest.entries[5])
        assertEquals(5, viewModel.state.value.interests.size)
        assertTrue(viewModel.state.value.limitReached)
        viewModel.toggleInterest(Interest.entries[0])
        assertFalse(viewModel.state.value.limitReached)
        viewModel.setText("x".repeat(130))
        assertEquals(120, viewModel.state.value.text.length)
    }

    @Test fun costTracksCreditsUpdates() = runTest {
        val credits = CreditsStore()
        val viewModel = vm(FakeRepository(), credits)
        credits.update(CreditsDto(0, 0, 0, Instant.parse("2026-11-22T00:00:00Z"), CreditCostsDto(3, 1, 0, inspiration = 7)))
        assertEquals(7, viewModel.state.value.cost)
    }

    @Test fun askAiNeedsTextOrAnInterest() = runTest {
        val repo = FakeRepository(ApiResult.Success(session()))
        val viewModel = vm(repo)
        assertFalse(viewModel.state.value.canAskAi)
        viewModel.start()
        assertNull(repo.request, "empty text and no chips must not start a session")
        viewModel.setText("   ")
        assertFalse(viewModel.state.value.canAskAi)
        viewModel.start()
        assertNull(repo.request)
        viewModel.setText("freediving")
        assertTrue(viewModel.state.value.canAskAi)
        viewModel.setText("")
        assertFalse(viewModel.state.value.canAskAi)
        viewModel.toggleInterest(Interest.FOOD)
        assertTrue(viewModel.state.value.canAskAi)
        viewModel.start()
        assertNotNull(repo.request)
        assertEquals(listOf(Interest.FOOD), repo.request?.interests)
    }

    @Test fun successStoresSessionId() = runTest {
        val repo = FakeRepository(ApiResult.Success(session()))
        val viewModel = vm(repo)
        viewModel.setText("bali")
        viewModel.start()
        assertEquals("session-1", viewModel.state.value.sessionId)
    }

    @Test fun dismissingOfflineErrorReturnsToIdle() = runTest {
        val repo = FakeRepository(ApiResult.Failure(ApiFailure("NETWORK_UNAVAILABLE", "offline")))
        val viewModel = vm(repo)
        viewModel.setText("bali")
        viewModel.start()
        assertEquals(InspirationStartState.Offline, viewModel.state.value.startState)
        viewModel.dismissError()
        assertEquals(InspirationStartState.Idle, viewModel.state.value.startState)
    }

    @Test fun consumingSessionAllowsAnotherStart() = runTest {
        val repo = FakeRepository(ApiResult.Success(session("session-1")))
        val viewModel = vm(repo)
        viewModel.toggleInterest(Interest.NATURE)
        viewModel.start()
        assertEquals("session-1", viewModel.state.value.sessionId)
        viewModel.consumeStarted()
        assertNull(viewModel.state.value.sessionId)
        repo.result = ApiResult.Success(session("session-2"))
        viewModel.start()
        assertEquals("session-2", viewModel.state.value.sessionId)
    }

    @Test fun mapsFiveErrorKinds() = runTest {
        suspend fun mapped(code: String, status: Int? = null, details: Map<String, String>? = null, connection: Boolean = false) = run {
            val repo = FakeRepository(ApiResult.Failure(ApiFailure(code, "error", status, details)))
            if (connection) repo.result = ApiResult.Failure(ApiFailure(code, "error"))
            val viewModel = vm(repo)
            viewModel.setText("bali")
            viewModel.start()
            viewModel.state.value
        }
        assertEquals(InspirationStartState.OutOfCredits, mapped("INSUFFICIENT_CREDITS", 402).startState)
        assertEquals(InspirationStartState.RateLimited(2), mapped("RATE_LIMITED", 429, mapOf("retryAfterSeconds" to "75")).startState)
        assertEquals(InspirationStartState.RateLimited(null), mapped("RATE_LIMITED", 429).startState)
        assertEquals(InspirationStartState.NeedsVerification, mapped("EMAIL_NOT_VERIFIED", 403).startState)
        assertEquals(InspirationStartState.Offline, mapped("NETWORK_UNAVAILABLE", connection = true).startState)
        assertEquals(InspirationStartState.Failed, mapped("INTERNAL_ERROR").startState)
    }

    @Test fun creditsAreUnknownUntilTheStoreHasThem() = runTest {
        val credits = CreditsStore()
        val viewModel = vm(FakeRepository(), credits)
        assertFalse(viewModel.state.value.creditsKnown)
        credits.update(CreditsDto(1, 1, 3, Instant.parse("2026-11-22T00:00:00Z"), CreditCostsDto(3, 1, 0, inspiration = 3)))
        assertTrue(viewModel.state.value.creditsKnown)
        assertEquals(2, viewModel.state.value.availableCredits)
    }
}
