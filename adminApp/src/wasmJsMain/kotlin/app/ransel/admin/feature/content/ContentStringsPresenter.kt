package app.ransel.admin.feature.content

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiFailure
import app.ransel.admin.data.ApiResult
import app.ransel.shared.domain.validateContentString
import app.ransel.shared.dto.AdminContentStringDto
import app.ransel.shared.dto.AdminContentStringsDto
import app.ransel.shared.dto.ContentTextDto
import app.ransel.shared.dto.UpdateContentStringRequest
import app.ransel.shared.model.ContentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StringFilter { ALL, DRAFTS, CHANGED }

/** The text an admin types for one key; empty = the app's own text for that language. */
data class StringEdit(val key: String, val id: String, val en: String) {
    fun problems(row: AdminContentStringDto): Map<String, String> =
        validateContentString(row.defaults.id.orEmpty(), row.defaults.en.orEmpty(), id.takeIf { it.isNotBlank() }, en.takeIf { it.isNotBlank() })

    fun text() = ContentTextDto(id.takeIf { it.isNotBlank() }, en.takeIf { it.isNotBlank() })
}

data class ContentStringsUiState(
    val loading: Boolean = true,
    val data: AdminContentStringsDto? = null,
    val error: ApiFailure? = null,
    val query: String = "",
    val filter: StringFilter = StringFilter.ALL,
    /** Rows shown; "Tampilkan lebih banyak" adds a page. */
    val limit: Int = PAGE,
    val edit: StringEdit? = null,
    val saving: Boolean = false,
    val saveError: ApiFailure? = null,
    val savedKey: String? = null,
    val confirmPublish: Boolean = false,
    val publishing: Boolean = false,
    val publishError: ApiFailure? = null,
    val publishedVersion: Int? = null,
) {
    val rows: List<AdminContentStringDto>
        get() {
            val q = query.trim().lowercase()
            return data?.strings.orEmpty().filter { row ->
                when (filter) {
                    StringFilter.ALL -> true
                    StringFilter.DRAFTS -> row.status == ContentStatus.DRAFT
                    StringFilter.CHANGED -> row.status != ContentStatus.DEFAULT
                }
            }.filter { row ->
                q.isEmpty() || q in row.key || listOfNotNull(row.defaults.id, row.defaults.en, row.shownId(), row.shownEn()).any { q in it.lowercase() }
            }
        }

    val selected: AdminContentStringDto? get() = edit?.let { e -> data?.strings?.firstOrNull { it.key == e.key } }

    companion object {
        const val PAGE = 100
    }
}

/** What the row shows: the draft, else the live override, else the bundled text. */
fun AdminContentStringDto.shownId(): String = (if (draft != null) draft?.id else published?.id) ?: defaults.id.orEmpty()

fun AdminContentStringDto.shownEn(): String = (if (draft != null) draft?.en else published?.en) ?: defaults.en.orEmpty()

/** "Teks & wording" (F9.6, design `KontenTeks`). */
class ContentStringsPresenter(
    private val api: AdminApi,
    private val scope: CoroutineScope,
    private val onAccessLost: suspend (ApiFailure) -> Unit,
) {
    private val _state = MutableStateFlow(ContentStringsUiState())
    val state: StateFlow<ContentStringsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = api.contentStrings()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, data = r.value) }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(loading = false, error = r.error) }
                }
            }
        }
    }

    fun search(query: String) = _state.update { it.copy(query = query, limit = ContentStringsUiState.PAGE) }
    fun filter(filter: StringFilter) = _state.update { it.copy(filter = filter, limit = ContentStringsUiState.PAGE) }
    fun showMore() = _state.update { it.copy(limit = it.limit + ContentStringsUiState.PAGE) }

    fun open(row: AdminContentStringDto) = _state.update {
        it.copy(edit = StringEdit(row.key, row.shownId(), row.shownEn()), saveError = null, savedKey = null)
    }

    fun close() = _state.update { it.copy(edit = null, saveError = null) }
    fun editId(text: String) = _state.update { s -> s.copy(edit = s.edit?.copy(id = text), savedKey = null) }
    fun editEn(text: String) = _state.update { s -> s.copy(edit = s.edit?.copy(en = text), savedKey = null) }

    /** "Kembalikan bawaan": the fields show the bundled text; saving makes a draft that removes the override. */
    fun resetToDefault() = _state.update { s ->
        val row = s.selected ?: return@update s
        s.copy(edit = s.edit?.copy(id = row.defaults.id.orEmpty(), en = row.defaults.en.orEmpty()), savedKey = null)
    }

    fun saveDraft() = send(discard = false)
    fun discardDraft() = send(discard = true)

    private fun send(discard: Boolean) {
        val s = _state.value
        val edit = s.edit ?: return
        val row = s.selected ?: return
        if (s.saving || (!discard && edit.problems(row).isNotEmpty())) return
        // the bundled text typed back is "no override"
        val text = edit.text().let { t ->
            ContentTextDto(t.id?.takeIf { it != row.defaults.id }, t.en?.takeIf { it != row.defaults.en })
        }
        _state.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            when (val r = api.saveContentString(UpdateContentStringRequest(edit.key, text, discard))) {
                is ApiResult.Success -> {
                    _state.update { st ->
                        val data = st.data ?: return@update st.copy(saving = false)
                        val strings = data.strings.map { if (it.key == r.value.key) r.value else it }
                        st.copy(
                            saving = false,
                            savedKey = edit.key,
                            data = data.copy(strings = strings, drafts = data.drafts + draftDelta(row, r.value)),
                            edit = StringEdit(r.value.key, r.value.shownId(), r.value.shownEn()),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(saving = false, saveError = r.error) }
                }
            }
        }
    }

    fun askToPublish() = _state.update { if ((it.data?.drafts ?: 0) > 0) it.copy(confirmPublish = true, publishError = null) else it }
    fun dismissPublish() = _state.update { if (it.publishing) it else it.copy(confirmPublish = false) }

    fun publish() {
        if (_state.value.publishing) return
        _state.update { it.copy(publishing = true, publishError = null) }
        scope.launch {
            when (val r = api.publishContent()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(publishing = false, confirmPublish = false, publishedVersion = r.value.version) }
                    reload()
                }
                is ApiResult.Failure -> {
                    onAccessLost(r.error)
                    _state.update { it.copy(publishing = false, publishError = r.error) }
                }
            }
        }
    }
}

/** +1 when a draft appeared, −1 when one went away. */
internal fun draftDelta(before: AdminContentStringDto, after: AdminContentStringDto): Int =
    (if (after.status == ContentStatus.DRAFT) 1 else 0) - (if (before.status == ContentStatus.DRAFT) 1 else 0)
