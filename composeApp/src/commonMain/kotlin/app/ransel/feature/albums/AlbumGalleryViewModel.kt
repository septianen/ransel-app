package app.ransel.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.AlbumItemDto
import app.ransel.shared.dto.AlbumItemsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whose Drive album: a member's trip ([tripId]) or the guest page ([guestCode]). */
data class AlbumSource(val albumId: String, val tripId: String? = null, val guestCode: String? = null) {
    init {
        require((tripId == null) != (guestCode == null)) { "a trip or a guest code" }
    }
}

data class AlbumGalleryUiState(
    val loading: Boolean = true,
    val items: List<AlbumItemDto> = emptyList(),
    /** More photos wait on the server. */
    val nextPageToken: String? = null,
    val loadingMore: Boolean = false,
    /** The first page failed: ALBUM_NOT_PUBLIC, ALBUM_NOT_FOUND, ALBUM_UNAVAILABLE or a connection code. */
    val failure: String? = null,
)

/**
 * The photos of one public Drive folder (design "AlbumGaleri"), 100 per page; the next page loads as the grid or the
 * viewer nears the end. The server only lists the folder: the images load straight from Google.
 */
class AlbumGalleryViewModel(
    private val source: AlbumSource,
    private val api: RanselApi,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumGalleryUiState())
    val state: StateFlow<AlbumGalleryUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** The first page again ("Coba lagi"). */
    fun load() {
        _state.update { AlbumGalleryUiState(loading = true) }
        viewModelScope.launch {
            when (val result = page(null)) {
                is ApiResult.Success -> _state.update {
                    AlbumGalleryUiState(loading = false, items = result.value.items, nextPageToken = result.value.nextPageToken)
                }
                is ApiResult.Failure -> _state.update { AlbumGalleryUiState(loading = false, failure = result.error.code) }
            }
        }
    }

    /** The next page, if there is one and none is on its way; a failure leaves the photos so far and tries again later. */
    fun loadMore() {
        val current = _state.value
        val token = current.nextPageToken ?: return
        if (current.loading || current.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val result = page(token)) {
                is ApiResult.Success -> _state.update { s ->
                    val known = s.items.map { it.id }.toSet()
                    s.copy(items = s.items + result.value.items.filter { it.id !in known }, nextPageToken = result.value.nextPageToken, loadingMore = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun page(token: String?): ApiResult<AlbumItemsDto> =
        if (source.tripId != null) api.albumItems(source.tripId, source.albumId, token)
        else api.guestAlbumItems(source.guestCode!!, source.albumId, token)
}
