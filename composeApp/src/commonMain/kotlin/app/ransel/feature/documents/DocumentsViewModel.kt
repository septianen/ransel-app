package app.ransel.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.remote.ApiJson
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.ItineraryRepository
import app.ransel.data.repository.PreferencesRepository
import app.ransel.platform.DeviceFiles
import app.ransel.shared.dto.CreateDocumentRequest
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.MAX_DOCUMENT_BYTES
import app.ransel.shared.dto.MAX_TRIP_DOCUMENT_BYTES
import app.ransel.shared.dto.TripDocumentDto
import app.ransel.shared.model.DocumentCategory
import app.ransel.shared.model.DocumentKind
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

data class DocumentsUiState(
    val loading: Boolean = true,
    val documents: List<TripDocumentDto> = emptyList(),
    val usedBytes: Long = 0,
    val limitBytes: Long = MAX_TRIP_DOCUMENT_BYTES,
    /** The server could not be asked: this is the list last saved on the device. */
    val fromDevice: Boolean = false,
    /** Ids of documents kept on this device. */
    val kept: Set<String> = emptySet(),
    /** A document being downloaded to open. */
    val opening: String? = null,
    /** The add sheet is saving. */
    val saving: Boolean = false,
    /** Activities a document can belong to. */
    val activities: List<ItineraryItemDto> = emptyList(),
) {
    val byCategory: List<Pair<DocumentCategory, List<TripDocumentDto>>>
        get() = DocumentCategory.entries.map { c -> c to documents.filter { it.category == c } }.filter { it.second.isNotEmpty() }
}

sealed interface DocumentsEvent {
    /** A link, or a web download: the screen opens it in the browser. */
    data class OpenUrl(val url: String) : DocumentsEvent
    /** An ErrorCode (or OPEN_FAILED, LINK, TITLE), shown as a snackbar or in the sheet. */
    data class Problem(val code: String) : DocumentsEvent
    data object Added : DocumentsEvent
    data object Deleted : DocumentsEvent
}

/** A file picked to upload. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * "Dokumen trip" (designs "DokumenTrip", "WebDokumenTrip"). Online: the list comes from the server and is saved on the
 * device; offline the saved list shows, and documents opened before still open from the device. Uploads need a
 * connection; the uploader's own file is kept on the device straight away.
 */
class DocumentsViewModel(
    private val tripId: String,
    private val api: RanselApi,
    private val preferences: PreferencesRepository,
    private val files: DeviceFiles,
    itinerary: ItineraryRepository,
    session: app.ransel.data.auth.SessionManager,
) : ViewModel() {
    /** Phones keep opened documents; the web downloads them. */
    val keepsFiles: Boolean get() = files.keepsFiles
    val myMemberId: String? = session.user?.id?.let { app.ransel.shared.domain.memberIdFor(tripId, it) }

    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    private val events = Channel<DocumentsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            itinerary.observe(tripId).collect { items ->
                _state.update { s -> s.copy(activities = items.filter { it.dayDate != null }.sortedWith(compareBy({ it.dayDate }, { it.startTime }))) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val result = api.documents(tripId)) {
                is ApiResult.Success -> {
                    val docs = result.value.items
                    preferences.putString(listKey(tripId), ApiJson.encodeToString(listSerializer, docs))
                    _state.update {
                        it.copy(loading = false, documents = docs, usedBytes = result.value.usedBytes, limitBytes = result.value.limitBytes, fromDevice = false, kept = kept(docs))
                    }
                }
                is ApiResult.Failure -> {
                    val saved = preferences.getString(listKey(tripId))?.let { runCatching { ApiJson.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
                    _state.update { it.copy(loading = false, documents = saved, fromDevice = true, kept = kept(saved)) }
                }
            }
        }
    }

    /** A link opens in the browser; a file opens from the device, downloading it first when needed (web: a download). */
    fun open(doc: TripDocumentDto) {
        if (doc.kind == DocumentKind.LINK) {
            doc.url?.let { viewModelScope.launch { events.send(DocumentsEvent.OpenUrl(it)) } }
            return
        }
        if (_state.value.opening != null) return
        viewModelScope.launch {
            val name = fileName(doc)
            if (files.keepsFiles && files.has(name)) {
                runCatching { files.open(name) }.onFailure { events.send(DocumentsEvent.Problem(OPEN_FAILED)) }
                return@launch
            }
            _state.update { it.copy(opening = doc.id) }
            val link = (api.documentDownload(tripId, doc.id) as? ApiResult.Success)?.value?.url
            when {
                link == null -> events.send(DocumentsEvent.Problem(OPEN_FAILED))
                !files.keepsFiles -> events.send(DocumentsEvent.OpenUrl(api.absoluteUrl(link)))
                else -> when (val bytes = api.downloadBytes(link)) {
                    is ApiResult.Success -> {
                        files.save(name, bytes.value)
                        _state.update { it.copy(kept = it.kept + doc.id) }
                        runCatching { files.open(name) }.onFailure { events.send(DocumentsEvent.Problem(OPEN_FAILED)) }
                    }
                    is ApiResult.Failure -> events.send(DocumentsEvent.Problem(OPEN_FAILED))
                }
            }
            _state.update { it.copy(opening = null) }
        }
    }

    /** "Simpan" in the add sheet: a picked file, or else a link. */
    fun add(title: String, category: DocumentCategory, itineraryItemId: String?, file: PickedFile?, link: String) {
        if (_state.value.saving) return
        if (title.isBlank()) return problem(TITLE)
        if (file == null && !link.trim().matches(Regex("^https?://\\S+$"))) return problem(LINK)
        if (file != null && file.bytes.size > MAX_DOCUMENT_BYTES) return problem(app.ransel.shared.api.ErrorCode.FILE_TOO_LARGE)
        val meta = CreateDocumentRequest(newId(), title.trim(), category, url = if (file == null) link.trim() else null, itineraryItemId = itineraryItemId)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (file == null) api.addDocumentLink(tripId, meta)
            else api.uploadDocument(tripId, meta, file.name, mimeOf(file.name), file.bytes)
            when (result) {
                is ApiResult.Success -> {
                    // the uploader has the file already: keep it for use without signal
                    if (file != null && files.keepsFiles) {
                        files.save(fileName(result.value), file.bytes)
                    }
                    _state.update { it.copy(saving = false) }
                    events.send(DocumentsEvent.Added)
                    load()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(saving = false) }
                    events.send(DocumentsEvent.Problem(result.error.code))
                }
            }
        }
    }

    fun delete(doc: TripDocumentDto) {
        viewModelScope.launch {
            when (val result = api.deleteDocument(tripId, doc.id)) {
                is ApiResult.Success -> {
                    files.delete(fileName(doc))
                    events.send(DocumentsEvent.Deleted)
                    load()
                }
                is ApiResult.Failure -> events.send(DocumentsEvent.Problem(result.error.code))
            }
        }
    }

    private fun problem(code: String) {
        viewModelScope.launch { events.send(DocumentsEvent.Problem(code)) }
    }

    private suspend fun kept(docs: List<TripDocumentDto>): Set<String> =
        if (!files.keepsFiles) emptySet() else docs.filter { it.kind == DocumentKind.FILE && files.has(fileName(it)) }.map { it.id }.toSet()

    companion object {
        const val OPEN_FAILED = "OPEN_FAILED"
        const val LINK = "LINK"
        const val TITLE = "TITLE"
        private val listSerializer = ListSerializer(TripDocumentDto.serializer())

        fun listKey(tripId: String) = "documents_$tripId"

        /** On the device: the id plus the original extension, so the phone's viewer knows the type. */
        fun fileName(doc: TripDocumentDto): String =
            doc.id + (doc.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }?.let { ".$it" } ?: "")

        /** The type the server checks, from the extension (pickers do not always say). */
        fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "application/octet-stream"
        }
    }
}
