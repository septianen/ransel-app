package app.ransel.feature.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.data.repository.PackingRepository
import app.ransel.data.repository.PersonalItemRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.CreatePackingItemRequest
import app.ransel.shared.dto.CreatePersonalItemRequest
import app.ransel.shared.dto.MemberListDto
import app.ransel.shared.dto.MemberListItemDto
import app.ransel.shared.dto.MemberListProgressDto
import app.ransel.shared.dto.PackingTemplateDto
import app.ransel.shared.dto.PatchPersonalItemRequest
import app.ransel.shared.dto.PersonalItemDto
import app.ransel.shared.dto.SaveMemberListRequest
import app.ransel.shared.dto.SendMemberListRequest
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.PackingCategory
import app.ransel.util.newId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category of the Pribadi list. */
data class PersonalGroup(val category: PackingCategory, val items: List<PersonalItemDto>) {
    val packed: Int get() = items.count { it.isPacked }
}

data class GroupPackingUiState(
    val loading: Boolean = true,
    val personal: List<PersonalItemDto> = emptyList(),
    /** Members from the server (empty offline). */
    val members: List<TripMemberDto> = emptyList(),
    val myMemberId: String? = null,
    /** The owner's member list; null = none yet (or not loaded). */
    val list: MemberListDto? = null,
    /** The server could not be asked: the member list parts are hidden. */
    val offline: Boolean = false,
    val progress: List<MemberListProgressDto> = emptyList(),
    val templates: List<PackingTemplateDto> = emptyList(),
    val busy: Boolean = false,
) {
    /** Lines from the owner's list, by category. */
    val fromList: List<PersonalGroup> get() = groups(personal.filter { it.sourceItemId != null })

    /** The member's own lines: by category, or one "Tambahan saya" block when a list arrived too. */
    val own: List<PersonalItemDto> get() = personal.filter { it.sourceItemId == null }
    val ownGroups: List<PersonalGroup> get() = groups(own)
    val total: Int get() = personal.size
    val packed: Int get() = personal.count { it.isPacked }
    val requiredTotal: Int get() = personal.count { it.required }
    val requiredDone: Int get() = personal.count { it.required && it.isPacked }
    val receivedList: Boolean get() = personal.any { it.sourceItemId != null }
    fun member(id: String?): TripMemberDto? = members.firstOrNull { it.memberId == id }

    private fun groups(items: List<PersonalItemDto>) = PackingCategory.entries
        .map { c -> PersonalGroup(c, items.filter { it.category == c }) }
        .filter { it.items.isNotEmpty() }
}

sealed interface GroupPackingEvent {
    data object Saved : GroupPackingEvent
    data object Sent : GroupPackingEvent
    data object Reminded : GroupPackingEvent
    data class Problem(val code: String) : GroupPackingEvent
}

/**
 * Pribadi (designs "PackingPribadiGunung", "PackingOwnerGunung") and the owner's member list ("MulaiDaftarAnggota",
 * "SusunDaftarAnggota", "KirimDaftarAnggota"). The member's own lines are local-first like other packing items; the
 * member list, its progress and the members are online only, like balances.
 */
class GroupPackingViewModel(
    private val tripId: String,
    private val personal: PersonalItemRepository,
    private val packing: PackingRepository,
    private val api: RanselApi,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupPackingUiState(myMemberId = session.user?.id?.let { memberIdFor(tripId, it) }))
    val state: StateFlow<GroupPackingUiState> = _state.asStateFlow()

    private val events = Channel<GroupPackingEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            personal.observe(tripId).collect { lines -> _state.update { it.copy(loading = false, personal = lines) } }
        }
        refresh()
    }

    /** Members, the list and (for editors) its progress. */
    fun refresh() {
        viewModelScope.launch {
            val members = api.members(tripId)
            if (members is ApiResult.Failure && members.error.isConnection) {
                _state.update { it.copy(offline = true) }
                return@launch
            }
            _state.update { it.copy(offline = false, members = (members as? ApiResult.Success)?.value.orEmpty()) }
            loadList()
        }
    }

    private suspend fun loadList() {
        when (val result = api.memberList(tripId)) {
            is ApiResult.Success -> _state.update { it.copy(list = result.value) }
            is ApiResult.Failure -> if (result.error.isNotFound) _state.update { it.copy(list = null, progress = emptyList()) }
        }
        if (_state.value.list?.sentAt != null) {
            // only editors may see it; a VIEWER simply gets nothing
            (api.memberListProgress(tripId) as? ApiResult.Success)?.let { r -> _state.update { it.copy(progress = r.value) } }
        }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            (api.packingTemplates() as? ApiResult.Success)?.let { r -> _state.update { it.copy(templates = r.value) } }
        }
    }

    // ── own lines ───────────────────────────────────────────────────────────

    fun toggle(item: PersonalItemDto) {
        viewModelScope.launch { personal.update(tripId, item.id, PatchPersonalItemRequest(isPacked = !item.isPacked)) }
    }

    fun add(name: String, category: PackingCategory, quantity: Int) {
        val order = (_state.value.personal.maxOfOrNull { it.sortOrder } ?: 0.0) + 1.0
        viewModelScope.launch { personal.add(tripId, CreatePersonalItemRequest(newId(), category, name.trim(), quantity, order)) }
    }

    fun edit(item: PersonalItemDto, name: String, category: PackingCategory, quantity: Int) {
        viewModelScope.launch {
            personal.update(tripId, item.id, PatchPersonalItemRequest(category = category, name = name.trim(), quantity = quantity))
        }
    }

    fun delete(item: PersonalItemDto) {
        viewModelScope.launch { personal.delete(tripId, item.id) }
    }

    // ── Bersama ─────────────────────────────────────────────────────────────

    /** "Tambah barang bersama" with "Siapa yang bawa?". */
    fun addShared(name: String, category: PackingCategory, quantity: Int, assigneeMemberId: String?, order: Double) {
        viewModelScope.launch {
            packing.add(tripId, CreatePackingItemRequest(newId(), category, name.trim(), quantity, order, assigneeMemberId))
        }
    }

    // ── the owner's member list ─────────────────────────────────────────────

    /** Lines to start the editor with ("Salin dari daftar pribadi saya"). */
    fun linesFromMine(): List<MemberListItemDto> = _state.value.personal.mapIndexed { i, it ->
        MemberListItemDto(newId(), it.category, it.name, it.quantity, it.required, i.toDouble())
    }

    fun linesFromTemplate(template: PackingTemplateDto): List<MemberListItemDto> = template.items.mapIndexed { i, it ->
        MemberListItemDto(newId(), it.category, it.name, it.quantity ?: 1, it.required, i.toDouble())
    }

    /** Saves the list; [onSaved] runs with the saved list (the editor then closes or goes on to sending). */
    fun save(name: String, items: List<MemberListItemDto>, onSaved: (MemberListDto) -> Unit = {}) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val request = SaveMemberListRequest(name.trim(), _state.value.list?.version, items.mapIndexed { i, it -> it.copy(sortOrder = i.toDouble()) })
            when (val result = api.saveMemberList(tripId, request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, list = result.value) }
                    events.send(GroupPackingEvent.Saved)
                    onSaved(result.value)
                    if (result.value.sentAt != null) loadList()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(busy = false) }
                    events.send(GroupPackingEvent.Problem(result.error.code))
                    // someone else saved first: take theirs so the next save is on the right version
                    if (result.error.code == ErrorCode.LIST_STALE) loadList()
                }
            }
        }
    }

    /** [memberIds] empty = everyone with an account, now and later. */
    fun send(memberIds: List<String>, saveAsTemplate: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val list = _state.value.list
            val result = api.sendMemberList(tripId, SendMemberListRequest(memberIds, saveAsTemplate, list?.name))
            _state.update { it.copy(busy = false) }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(list = result.value) }
                    events.send(GroupPackingEvent.Sent)
                    loadList()
                }
                is ApiResult.Failure -> events.send(GroupPackingEvent.Problem(result.error.code))
            }
        }
    }

    /** "Ingatkan yang belum siap": every recipient with required lines left. */
    fun remindNotReady() {
        val ids = _state.value.progress.filter { it.requiredDone < it.requiredTotal }.map { it.memberId }
        if (ids.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = api.remindMemberList(tripId, ids)
            _state.update { it.copy(busy = false) }
            events.send(if (result is ApiResult.Success) GroupPackingEvent.Reminded else GroupPackingEvent.Problem((result as ApiResult.Failure).error.code))
        }
    }
}
