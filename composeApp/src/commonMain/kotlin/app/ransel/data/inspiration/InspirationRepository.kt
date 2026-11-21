package app.ransel.data.inspiration

import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.ApiResult
import app.ransel.shared.dto.*

interface InspirationRepository {
    suspend fun start(request: StartInspirationRequest): ApiResult<InspirationSessionDto>
    suspend fun get(id: String): ApiResult<InspirationSessionDto>
    suspend fun answer(id: String, request: InspirationAnswerRequest): ApiResult<InspirationSessionDto>
    suspend fun finish(id: String): ApiResult<InspirationSessionDto>
    suspend fun askAgain(id: String): ApiResult<InspirationSessionDto>
    suspend fun editChip(id: String, request: InspirationEditChipRequest): ApiResult<InspirationSessionDto>
    suspend fun saved(): ApiResult<SavedIdeasResponse>
    suspend fun save(request: SaveIdeaRequest): ApiResult<SaveIdeaResponse>
    suspend fun delete(id: String): ApiResult<Unit>
    suspend fun undo(id: String): ApiResult<Unit>
    suspend fun refresh(id: String): ApiResult<SavedIdeaDto>
    suspend fun buddy(request: BuddyInterestRequest): ApiResult<Unit>
}

class ApiInspirationRepository(private val api: RanselApi) : InspirationRepository {
    override suspend fun start(request: StartInspirationRequest) = api.startInspiration(request)
    override suspend fun get(id: String) = api.inspirationSession(id)
    override suspend fun answer(id: String, request: InspirationAnswerRequest) = api.answerInspiration(id, request)
    override suspend fun finish(id: String) = api.finishInspiration(id)
    override suspend fun askAgain(id: String) = api.askAgainInspiration(id)
    override suspend fun editChip(id: String, request: InspirationEditChipRequest) = api.editInspirationChip(id, request)
    override suspend fun saved() = api.savedIdeas()
    override suspend fun save(request: SaveIdeaRequest) = api.saveIdea(request)
    override suspend fun delete(id: String) = api.deleteSavedIdea(id)
    override suspend fun undo(id: String) = api.undoSavedIdea(id)
    override suspend fun refresh(id: String) = api.refreshSavedIdea(id)
    override suspend fun buddy(request: BuddyInterestRequest) = api.registerBuddyInterest(request)
}
