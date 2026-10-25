package app.ransel.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.feature.trips.countdownLabel
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.util.today
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.group_countdown_finished
import ransel.composeapp.generated.resources.group_countdown_ongoing
import ransel.composeapp.generated.resources.group_guest_tag
import ransel.composeapp.generated.resources.group_members_title
import ransel.composeapp.generated.resources.group_needs_empty
import ransel.composeapp.generated.resources.group_needs_title
import ransel.composeapp.generated.resources.group_signin_body
import ransel.composeapp.generated.resources.group_signin_title
import ransel.composeapp.generated.resources.members_count
import ransel.composeapp.generated.resources.members_you
import ransel.composeapp.generated.resources.role_editor
import ransel.composeapp.generated.resources.role_owner
import ransel.composeapp.generated.resources.role_viewer

/**
 * The first tab of a trip: its hub. A countdown, "Perlu tindakanmu" (open votings today; polls and money later) and the
 * members, with the ones added by name tagged "Tanpa akun". [needs] draws what asks for the user, [hasNeeds] says whether
 * there is anything.
 */
@Composable
fun GroupTab(
    trip: TripDto,
    signedIn: Boolean,
    hasNeeds: Boolean,
    needs: @Composable () -> Unit,
    onOpenMembers: () -> Unit,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    viewModel: GroupViewModel = koinViewModel(key = "group-${trip.id}") { parametersOf(trip.id) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.load() }
    GroupContent(
        trip = trip,
        today = today(),
        members = state.members,
        signedIn = signedIn,
        myUserId = viewModel.myUserId,
        hasNeeds = hasNeeds,
        needs = needs,
        onOpenMembers = onOpenMembers,
        modifier = modifier,
    )
}

@Composable
fun GroupContent(
    trip: TripDto,
    today: LocalDate,
    members: List<TripMemberDto>,
    signedIn: Boolean,
    myUserId: String?,
    hasNeeds: Boolean,
    needs: @Composable () -> Unit,
    onOpenMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CountdownCard(countdown = countdownFor(trip, today), dates = dates.range(trip.startDate, trip.endDate))

        if (!signedIn) {
            SignInCard()
            return@Column
        }

        Section(stringResource(Res.string.group_needs_title)) {
            if (hasNeeds) needs()
            else Text(
                stringResource(Res.string.group_needs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(
            title = stringResource(Res.string.group_members_title),
            trailing = pluralStringResource(Res.plurals.members_count, members.size, members.size),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(IrisShape.lg).clickable(role = SemanticsRole.Button, onClick = onOpenMembers),
            ) {
                members.forEach { MemberLine(it, isMe = it.userId != null && it.userId == myUserId) }
            }
        }
    }
}

@Composable
private fun CountdownCard(countdown: Countdown, dates: String) {
    val label = when (countdown) {
        is Countdown.Upcoming -> countdownLabel(countdown.days)
        is Countdown.Ongoing -> stringResource(Res.string.group_countdown_ongoing, countdown.day, countdown.totalDays)
        Countdown.Finished -> stringResource(Res.string.group_countdown_finished)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IrisShape.lg)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SignInCard() {
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.group_signin_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.group_signin_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun MemberLine(member: TripMemberDto, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(member.displayName, member.memberId, isOwner = member.role == Role.OWNER, isGuest = member.isGuest, size = 40.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                member.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isMe) {
                Text(
                    " " + stringResource(Res.string.members_you),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (member.isGuest) {
            Tag(stringResource(Res.string.group_guest_tag))
        } else if (member.role != Role.EDITOR) {
            Tag(stringResource(if (member.role == Role.OWNER) Res.string.role_owner else Res.string.role_viewer))
        } else {
            Tag(stringResource(Res.string.role_editor))
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val previewTrip = TripDto(
    id = "t", ownerId = "u1", title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    myRole = Role.OWNER, createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)

private val previewMembers = listOf(
    TripMemberDto("t", "m1", "u1", "Septian", null, Role.OWNER, Instant.parse("2026-10-01T00:00:00Z")),
    TripMemberDto("t", "m2", "u2", "Dinda", null, Role.EDITOR, Instant.parse("2026-10-02T00:00:00Z")),
    TripMemberDto("t", "m3", null, "Rina", null, Role.VIEWER, Instant.parse("2026-10-03T00:00:00Z")),
)

@Preview
@Composable
private fun GroupTabPreview() = IrisPreview {
    GroupContent(previewTrip, LocalDate(2026, 11, 2), previewMembers, signedIn = true, myUserId = "u1", hasNeeds = false, needs = {}, onOpenMembers = {})
}

@Preview
@Composable
private fun GroupTabGuestPreview() = IrisPreview {
    GroupContent(previewTrip, LocalDate(2026, 11, 22), emptyList(), signedIn = false, myUserId = null, hasNeeds = false, needs = {}, onOpenMembers = {})
}
