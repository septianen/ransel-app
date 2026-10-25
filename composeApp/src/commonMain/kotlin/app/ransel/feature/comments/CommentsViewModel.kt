package app.ransel.feature.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.domain.MentionCandidate
import app.ransel.shared.domain.extractMentions
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.domain.validateCommentBody
import app.ransel.shared.dto.CommentDto
import app.ransel.shared.dto.CreateCommentRequest
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.UpdateCommentRequest
import app.ransel.shared.model.CommentTarget
import app.ransel.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the comments sheet hangs on. One value so Koin can hand it to the ViewModel. */
data class CommentTargetRef(val tripId: String, val kind: CommentTarget, val targetId: String)

data class CommentsUiState(
    val loading: Boolean = true,
    val comments: List<CommentDto> = emptyList(),
    val members: List<TripMemberDto> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    /** The comment being edited; the field then holds its text and Send reads "Simpan". */
    val editing: CommentDto? = null,
    /** The last load or send failed (offline, or the item is not on the server yet). */
    val failed: Boolean = false,
    val myMemberId: String? = null,
) {
    /** People a comment can mention: accounts other than me (members added by name have no inbox). */
    val mentionable: List<TripMemberDto> get() = members.filter { it.userId != null && it.memberId != myMemberId }
    val canSend: Boolean get() = !sending && validateCommentBody(draft).isEmpty()
}

/** Comments on one itinerary item or expense. Online only: they are not part of sync. */
class CommentsViewModel(
    private val target: CommentTargetRef,
    private val api: RanselApi,
    session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(CommentsUiState(myMemberId = session.user?.id?.let { memberIdFor(target.tripId, it) }))
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val list = api.comments(target.tripId, target.kind, target.targetId)
            val members = api.members(target.tripId)
            _state.update {
                it.copy(
                    loading = false,
                    comments = (list as? ApiResult.Success)?.value ?: it.comments,
                    members = (members as? ApiResult.Success)?.value ?: it.members,
                    failed = list is ApiResult.Failure,
                )
            }
        }
    }

    fun onDraft(value: String) = _state.update { it.copy(draft = value) }

    /** Puts "@Name " in the field for the member picked from the chips. */
    fun mention(member: TripMemberDto) = _state.update {
        val gap = if (it.draft.isEmpty() || it.draft.endsWith(" ")) "" else " "
        it.copy(draft = it.draft + gap + "@" + member.displayName + " ")
    }

    fun send() {
        val s = _state.value
        if (!s.canSend) return
        val body = s.draft.trim()
        val mentions = extractMentions(body, s.members.map { MentionCandidate(it.memberId, it.displayName) })
        _state.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            val editing = s.editing
            val result = if (editing == null) {
                api.createComment(target.tripId, CreateCommentRequest(newId(), target.kind, target.targetId, body, mentions))
            } else {
                api.updateComment(editing.id, UpdateCommentRequest(body, mentions))
            }
            when (result) {
                is ApiResult.Success -> _state.update {
                    val saved = result.value
                    it.copy(
                        sending = false, draft = "", editing = null,
                        comments = if (editing == null) it.comments + saved else it.comments.map { c -> if (c.id == saved.id) saved else c },
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(sending = false, failed = true) }
            }
        }
    }

    fun startEdit(comment: CommentDto) = _state.update { it.copy(editing = comment, draft = comment.body, failed = false) }
    fun cancelEdit() = _state.update { it.copy(editing = null, draft = "") }

    fun delete(comment: CommentDto) {
        _state.update { it.copy(comments = it.comments - comment, editing = it.editing?.takeIf { e -> e.id != comment.id }) }
        viewModelScope.launch {
            if (api.deleteComment(comment.id) is ApiResult.Failure) {
                _state.update { it.copy(failed = true) }
                load()
            }
        }
    }
}
