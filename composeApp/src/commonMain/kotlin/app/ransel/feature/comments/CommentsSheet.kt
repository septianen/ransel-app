package app.ransel.feature.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.data.auth.SessionManager
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.dto.CommentDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.CommentTarget
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.comments_button
import ransel.composeapp.generated.resources.comments_cancel_edit
import ransel.composeapp.generated.resources.comments_count
import ransel.composeapp.generated.resources.comments_delete
import ransel.composeapp.generated.resources.comments_edit
import ransel.composeapp.generated.resources.comments_edited
import ransel.composeapp.generated.resources.comments_empty
import ransel.composeapp.generated.resources.comments_error
import ransel.composeapp.generated.resources.comments_hint
import ransel.composeapp.generated.resources.comments_mention
import ransel.composeapp.generated.resources.comments_save
import ransel.composeapp.generated.resources.comments_send
import ransel.composeapp.generated.resources.comments_title

/** What a row needs to show and open comments: set by [ProvideComments], null when the user cannot comment (a guest). */
class CommentsAccess(val countOf: (targetId: String) -> Int, val open: (targetId: String, title: String) -> Unit)

val LocalComments = staticCompositionLocalOf<CommentsAccess?> { null }

/**
 * Makes comments available to the rows inside [content] (itinerary items or expenses): a count per row and the sheet.
 * Only for signed-in users, because comments live on the server. [refreshKey] reloads the counts.
 */
@Composable
fun ProvideComments(
    tripId: String,
    kind: CommentTarget,
    refreshKey: Int = 0,
    content: @Composable () -> Unit,
) {
    val session: SessionManager = koinInject()
    val api: RanselApi = koinInject()
    val signedIn = session.user != null
    var counts by remember(tripId, kind) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun reload() {
        if (!signedIn) return
        (api.commentCounts(tripId, kind) as? ApiResult.Success)?.let { r -> counts = r.value.associate { it.targetId to it.count } }
    }
    LaunchedEffect(tripId, kind, signedIn, refreshKey) { reload() }

    val access = if (signedIn) CommentsAccess({ counts[it] ?: 0 }, { id, title -> open = id to title }) else null
    CompositionLocalProvider(LocalComments provides access) { content() }

    open?.let { (targetId, title) ->
        CommentsSheet(
            target = CommentTargetRef(tripId, kind, targetId),
            title = title,
            onDismiss = { open = null },
        )
        // The sheet changes counts; fetch them again once it is closed.
        LaunchedEffect(open) { if (open == null) reload() }
    }
}

/** Small button on a row: a note icon and the number of comments. Hidden when comments are not available. */
@Composable
fun CommentButton(targetId: String, title: String, modifier: Modifier = Modifier) {
    val access = LocalComments.current ?: return
    val count = access.countOf(targetId)
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { access.open(targetId, title) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Symbol(
            Symbols.editNote,
            size = 20.dp,
            contentDescription = if (count > 0) pluralStringResource(Res.plurals.comments_count, count, count) else stringResource(Res.string.comments_button),
            tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun CommentsSheet(
    target: CommentTargetRef,
    title: String,
    onDismiss: () -> Unit,
    viewModel: CommentsViewModel = koinViewModel(key = "comments-${target.targetId}") { parametersOf(target) },
) {
    val state by viewModel.state.collectAsState()
    AdaptiveSheet(onDismiss = onDismiss) {
        CommentsContent(
            title = title,
            state = state,
            onDraft = viewModel::onDraft,
            onSend = viewModel::send,
            onMention = viewModel::mention,
            onEdit = viewModel::startEdit,
            onCancelEdit = viewModel::cancelEdit,
            onDelete = viewModel::delete,
        )
    }
}

@Composable
fun CommentsContent(
    title: String,
    state: CommentsUiState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMention: (TripMemberDto) -> Unit,
    onEdit: (CommentDto) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (CommentDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(Res.string.comments_title), style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(
            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading && state.comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.comments.isEmpty() -> Text(
                    stringResource(Res.string.comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> state.comments.forEach { comment ->
                    CommentRow(
                        comment = comment,
                        mine = comment.authorMemberId == state.myMemberId,
                        onEdit = { onEdit(comment) },
                        onDelete = { onDelete(comment) },
                    )
                }
            }
        }
        if (state.failed) {
            Text(stringResource(Res.string.comments_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.mentionable.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.comments_mention), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.mentionable.forEach { member ->
                    Text(
                        "@" + member.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(role = Role.Button) { onMention(member) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        IrisTextField(
            label = stringResource(Res.string.comments_hint),
            value = state.draft,
            onValueChange = onDraft,
            singleLine = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.editing != null) {
                TextButton(onClick = onCancelEdit) { Text(stringResource(Res.string.comments_cancel_edit)) }
            }
            Box(Modifier.weight(1f))
            LoadingButton(onClick = onSend, loading = state.sending, enabled = state.canSend) {
                Text(
                    stringResource(if (state.editing != null) Res.string.comments_save else Res.string.comments_send),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommentDto, mine: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MemberAvatar(comment.authorName, comment.authorMemberId, size = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.authorName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                if (comment.editedAt != null) {
                    Text(
                        stringResource(Res.string.comments_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            if (mine) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(Res.string.comments_edit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onEdit).padding(vertical = 6.dp),
                    )
                    Text(
                        stringResource(Res.string.comments_delete),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onDelete).padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private val previewComments = listOf(
    CommentDto("c1", "t", CommentTarget.ITINERARY_ITEM, "i", "m1", "Dinda", "Berangkat jam 8 ya?", createdAt = Instant.parse("2026-10-25T01:00:00Z")),
    CommentDto("c2", "t", CommentTarget.ITINERARY_ITEM, "i", "m2", "Septian", "@Dinda boleh, aku jemput.", listOf("m1"), Instant.parse("2026-10-25T01:05:00Z"), Instant.parse("2026-10-25T01:06:00Z")),
)

@Preview
@Composable
private fun CommentsPreview() = IrisPreview {
    CommentsContent(
        title = "Pantai Kuta",
        state = CommentsUiState(
            loading = false, comments = previewComments, myMemberId = "m2", draft = "Siap",
            members = listOf(TripMemberDto("t", "m1", "u1", "Dinda", null, app.ransel.shared.model.Role.EDITOR, Instant.parse("2026-10-01T00:00:00Z"))),
        ),
        onDraft = {}, onSend = {}, onMention = {}, onEdit = {}, onCancelEdit = {}, onDelete = {},
    )
}

@Preview
@Composable
private fun CommentsEmptyPreview() = IrisPreview {
    CommentsContent("Pantai Kuta", CommentsUiState(loading = false, failed = true), {}, {}, {}, {}, {}, {})
}
