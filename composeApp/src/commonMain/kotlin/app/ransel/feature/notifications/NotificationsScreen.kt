package app.ransel.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ransel.shared.domain.VoteStage
import app.ransel.shared.dto.NotificationDto
import app.ransel.shared.dto.NotificationsDto
import app.ransel.shared.model.NotificationType
import app.ransel.ui.components.AccentTile
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Inbox behind the Home bell (designs `Notifikasi`, `GelapNotifikasi`, `KosongNotifikasi`). */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onOpenSettings: () -> Unit,
    /** A voting notification opens its round; [showResult] for the OWNER's "ready to decide". */
    onOpenRound: (tripId: String, roundId: String, showResult: Boolean) -> Unit,
    /** Someone took a name (MEMBER_CLAIMED): the OWNER lands on the members with a way to undo it. */
    onOpenMembers: (tripId: String, guestMemberId: String?, guestName: String?, claimerName: String?) -> Unit,
    /** A poll notification opens that poll. */
    onOpenPoll: (tripId: String, pollId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NotificationsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onMarkAllRead = viewModel::markAllRead,
        onSettings = onOpenSettings,
        onOpen = { n ->
            viewModel.markRead(n)
            val roundId = n.params["roundId"]
            val tripId = n.tripId
            when {
                // Someone who was removed can no longer open the trip.
                n.type == NotificationType.MEMBER_REMOVED -> Unit
                tripId != null && n.params["pollId"] != null -> onOpenPoll(tripId, n.params["pollId"]!!)
                n.type == NotificationType.MEMBER_CLAIMED && tripId != null ->
                    onOpenMembers(tripId, n.params["guestMemberId"], n.params["guestName"], n.params["actorName"])
                // The vote screen sorts out where the round stands (open, closed, decided, expired).
                tripId != null && roundId != null -> onOpenRound(tripId, roundId, n.params["stage"] == VoteStage.READY)
                tripId != null -> onOpenTrip(tripId)
            }
        },
        modifier = modifier.statusBarsPadding(),
    )
}

@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (NotificationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inbox = state.inbox
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopBar(onBack, onMarkAllRead, enabled = (inbox?.unreadCount ?: 0) > 0)
        when {
            state.loading && inbox == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.failure != null && inbox == null ->
                if (state.failure.isConnection) ConnectionErrorState(onRetry = onRetry, errorCode = state.failure.code, onBack = onBack)
                else ServerErrorState(onRetry = onRetry, errorCode = state.failure.code, onBack = onBack)
            inbox != null && inbox.items.isEmpty() -> FullScreenState(
                title = stringResource(Res.string.notifications_empty_title),
                body = stringResource(Res.string.notifications_empty_body),
                illustration = painterResource(Res.drawable.ic_sun_bathing_water),
                illustrationSize = 200.dp,
                primaryLabel = null,
                secondaryLabel = stringResource(Res.string.notifications_empty_action),
                onSecondary = onSettings,
            )
            inbox != null -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                if (state.unread.isNotEmpty()) {
                    SectionLabel(stringResource(Res.string.notifications_section_new))
                    state.unread.forEach { NotificationRow(it, unread = true, onClick = { onOpen(it) }) }
                }
                if (state.earlier.isNotEmpty()) {
                    SectionLabel(stringResource(Res.string.notifications_section_earlier))
                    state.earlier.forEach { NotificationRow(it, unread = false, onClick = { onOpen(it) }) }
                }
                Text(
                    stringResource(Res.string.notifications_footer),
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onMarkAllRead: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back)) }
        Text(
            stringResource(Res.string.notifications_title),
            Modifier.weight(1f).padding(start = 4.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        TextButton(onClick = onMarkAllRead, enabled = enabled) {
            Text(stringResource(Res.string.notifications_mark_all_read), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NotificationRow(n: NotificationDto, unread: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val line = notificationLine(n)
    val (color, symbol) = notificationTile(n.type, n.params)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (unread) scheme.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).width(10.dp), contentAlignment = Alignment.TopCenter) {
            if (unread) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
        }
        AccentTile(color = color, symbol = symbol, size = 40.dp, cornerRadius = 12.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
            if (line.subtitle.isNotBlank()) Text(line.subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Text(
            relativeTime(n.createdAt),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** "Baru saja", "2 mnt lalu", "3 jam lalu", "Kemarin", then the date. */
@Composable
private fun relativeTime(at: Instant, now: Instant = Clock.System.now()): String {
    val zone = TimeZone.currentSystemDefault()
    val dates = rememberDateFormatter()
    val ago = now - at
    val day = at.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return when {
        ago < 1.minutes -> stringResource(Res.string.notification_time_now)
        day == today && ago.inWholeMinutes < 60 -> stringResource(Res.string.notification_time_minutes, ago.inWholeMinutes.toInt())
        day == today -> stringResource(Res.string.notification_time_hours, ago.inWholeHours.toInt())
        day.toEpochDays() == today.toEpochDays() - 1 -> stringResource(Res.string.notification_time_yesterday)
        else -> dates.dayMonth(day)
    }
}

@Preview
@Composable
private fun NotificationsContentPreview() = IrisPreview {
    val now = Clock.System.now()
    fun n(id: String, type: NotificationType, params: Map<String, String>, ago: Int, read: Boolean) =
        NotificationDto(id, type, "t1", params, now - ago.minutes, if (read) now else null)
    val items = listOf(
        n("1", NotificationType.MEMBER_JOINED, mapOf("tripTitle" to "Bali 4 hari", "actorName" to "Dinda"), 2, false),
        n("2", NotificationType.EXPENSE_ADDED, mapOf("tripTitle" to "Bali 4 hari", "actorName" to "Raka", "amountMinor" to "300000", "currency" to "IDR", "shareMinor" to "100000"), 60, false),
        n("3", NotificationType.PACKING_REMINDER, mapOf("tripTitle" to "Bali 4 hari", "remaining" to "4"), 60 * 30, true),
    )
    Box(Modifier.height(560.dp)) {
        NotificationsContent(
            NotificationsUiState(loading = false, inbox = NotificationsDto(items, 2)),
            onBack = {}, onRetry = {}, onMarkAllRead = {}, onSettings = {}, onOpen = {},
        )
    }
}

@Preview
@Composable
private fun NotificationsEmptyPreview() = IrisPreview {
    Box(Modifier.height(560.dp)) {
        NotificationsContent(
            NotificationsUiState(loading = false, inbox = NotificationsDto(emptyList(), 0)),
            onBack = {}, onRetry = {}, onMarkAllRead = {}, onSettings = {}, onOpen = {},
        )
    }
}
