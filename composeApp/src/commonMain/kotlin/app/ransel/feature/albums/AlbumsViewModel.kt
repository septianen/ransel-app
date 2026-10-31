package app.ransel.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PreferencesRepository
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.domain.validateAlbum
import app.ransel.shared.dto.CreateAlbumRequest
import app.ransel.shared.dto.TripAlbumDto
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

data class AlbumsUiState(
    val loading: Boolean = true,
    val albums: List<TripAlbumDto> = emptyList(),
    /** The server could not be asked: this is the list last saved on the device. */
    val fromDevice: Boolean = false,
    /** The add sheet is saving (a Drive folder is asked before it is kept). */
    val saving: Boolean = false,
    /** Why the add sheet could not save: an ErrorCode, or [AlbumsViewModel.URL] / [AlbumsViewModel.TITLE]. */
    val addProblem: String? = null,
)

sealed interface AlbumsEvent {
    data object Added : AlbumsEvent
    data object Deleted : AlbumsEvent
    /** An ErrorCode, shown as a snackbar. */
    data class Problem(val code: String) : AlbumsEvent
}

/**
 * "Album trip" (design "AlbumTrip"): links to photos the members keep in Google Drive or elsewhere. Online only like
 * documents; the last list is kept on the device so it still shows without signal (the photos need a connection).
 */
class AlbumsViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
    session: SessionManager,
) : ViewModel() {
    val myMemberId: String? = session.user?.id?.let { memberIdFor(tripId, it) }

    private val _state = MutableStateFlow(AlbumsUiState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    private val events = Channel<AlbumsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val result = api.albums(tripId)) {
                is ApiResult.Success -> {
                    val albums = result.value.items
                    preferences.putString(listKey(tripId), ApiJson.encodeToString(listSerializer, albums))
                    _state.update { it.copy(loading = false, albums = albums, fromDevice = false) }
                }
                is ApiResult.Failure -> {
                    val saved = preferences.getString(listKey(tripId))?.let { runCatching { ApiJson.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
                    _state.update { it.copy(loading = false, albums = saved, fromDevice = true) }
                }
            }
        }
    }

    /** The sheet opens again: no old problem. */
    fun clearProblem() = _state.update { it.copy(addProblem = null) }

    /** "Simpan album". A private Drive folder comes back as ALBUM_NOT_PUBLIC and stays in the sheet. */
    fun add(url: String, title: String) {
        if (_state.value.saving) return
        val name = title.trim().takeIf { it.isNotEmpty() }
        val invalid = validateAlbum(url, name)
        if (invalid.isNotEmpty()) {
            _state.update { it.copy(addProblem = if ("url" in invalid) URL else TITLE) }
            return
        }
        _state.update { it.copy(saving = true, addProblem = null) }
        viewModelScope.launch {
            when (val result = api.addAlbum(tripId, CreateAlbumRequest(newId(), url.trim(), name))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    events.send(AlbumsEvent.Added)
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, addProblem = result.error.code) }
            }
        }
    }

    fun delete(album: TripAlbumDto) {
        viewModelScope.launch {
            when (val result = api.deleteAlbum(tripId, album.id)) {
                is ApiResult.Success -> {
                    events.send(AlbumsEvent.Deleted)
                    load()
                }
                is ApiResult.Failure -> events.send(AlbumsEvent.Problem(result.error.code))
            }
        }
    }

    companion object {
        const val URL = "URL"
        const val TITLE = "TITLE"
        private val listSerializer = ListSerializer(TripAlbumDto.serializer())

        fun listKey(tripId: String) = "albums_$tripId"
    }
}
