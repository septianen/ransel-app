package app.ransel.feature.inspiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.inspiration.InspirationRepository
import app.ransel.data.remote.ApiFailure
import app.ransel.data.remote.ApiResult
import app.ransel.shared.domain.isSavedIdeaStale
import app.ransel.shared.dto.SavedIdeaDto
import app.ransel.shared.api.ErrorCode
import app.ransel.util.now
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedIdeasUiState(
    val ideas: List<SavedIdeaDto> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failure: ApiFailure? = null,
    val staleIds: Set<String> = emptySet(),
    val waitingRefreshIds: Set<String> = emptySet(),
    val refreshingIds: Set<String> = emptySet(),
)

sealed interface SavedIdeasEvent {
    data class Removed(val id: String) : SavedIdeasEvent
    data object Error : SavedIdeasEvent
}

class SavedIdeasViewModel(private val repository: InspirationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SavedIdeasUiState())
    val state: StateFlow<SavedIdeasUiState> = mutable.asStateFlow()
    private val events = Channel<SavedIdeasEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun load() {
        if (mutable.value.loading) return
        val previous = mutable.value
        mutable.value = previous.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = repository.saved()) {
                is ApiResult.Success -> applyIdeas(result.value.ideas, loaded = true)
                is ApiResult.Failure -> {
                    if (previous.loaded) mutable.value = previous.copy(loading = false)
                    else mutable.value = previous.copy(loading = false, failure = result.error, loaded = true)
                }
            }
        }
    }

    fun remove(idea: SavedIdeaDto) {
        viewModelScope.launch {
            when (repository.delete(idea.id)) {
                is ApiResult.Success -> {
                    mutable.update { state ->
                        val ideas = state.ideas.filterNot { it.id == idea.id }
                        state.copy(ideas = ideas, staleIds = state.staleIds - idea.id, waitingRefreshIds = state.waitingRefreshIds - idea.id)
                    }
                    events.send(SavedIdeasEvent.Removed(idea.id))
                }
                is ApiResult.Failure -> events.send(SavedIdeasEvent.Error)
            }
        }
    }

    fun undoRemove(ideaId: String) {
        viewModelScope.launch {
            when (repository.restore(ideaId)) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> events.send(SavedIdeasEvent.Error)
            }
        }
    }

    fun refresh(idea: SavedIdeaDto) {
        val current = now()
        val waitByServer = idea.canRefreshAt?.let { it > current } == true
        if (idea.id in mutable.value.waitingRefreshIds || waitByServer) {
            mutable.update { it.copy(waitingRefreshIds = it.waitingRefreshIds + idea.id) }
            return
        }
        if (idea.id in mutable.value.refreshingIds) return
        mutable.update { it.copy(refreshingIds = it.refreshingIds + idea.id) }
        viewModelScope.launch {
            when (val result = repository.refresh(idea.id)) {
                is ApiResult.Success -> {
                    mutable.update { state ->
                        val updated = state.ideas.map { if (it.id == idea.id) result.value else it }
                        val waiting = result.value.canRefreshAt?.let { it > now() } == true
                        state.copy(
                            ideas = updated,
                            refreshingIds = state.refreshingIds - idea.id,
                            waitingRefreshIds = if (waiting) state.waitingRefreshIds + idea.id else state.waitingRefreshIds - idea.id,
                            staleIds = updateStaleIds(updated),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    mutable.update { state ->
                        state.copy(
                            refreshingIds = state.refreshingIds - idea.id,
                            waitingRefreshIds = if (result.error.code == ErrorCode.RATE_LIMITED) state.waitingRefreshIds + idea.id else state.waitingRefreshIds,
                        )
                    }
                    if (result.error.code != ErrorCode.RATE_LIMITED) events.send(SavedIdeasEvent.Error)
                }
            }
        }
    }

    private fun applyIdeas(ideas: List<SavedIdeaDto>, loaded: Boolean) {
        val now = now()
        val wait = ideas.filter { it.canRefreshAt?.let { at -> at > now } == true }.mapTo(mutableSetOf()) { it.id }
        mutable.value = mutable.value.copy(
            ideas = ideas,
            loading = false,
            loaded = loaded,
            failure = null,
            staleIds = updateStaleIds(ideas),
            waitingRefreshIds = wait,
            refreshingIds = emptySet(),
        )
    }

    private fun updateStaleIds(ideas: List<SavedIdeaDto>): Set<String> {
        val current = now()
        return ideas.filter { isSavedIdeaStale(it.refreshedAt, current) }.mapTo(mutableSetOf()) { it.id }
    }
}
