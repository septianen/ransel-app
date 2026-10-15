package app.ransel.feature.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.ransel.data.remote.ApiFailure
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.InvitePreviewDto
import app.ransel.shared.model.Role
import app.ransel.ui.components.ConnectionErrorState
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.ServerErrorState
import app.ransel.ui.components.TripCover
import app.ransel.ui.format.rememberDateFormatter
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.capybara_scanning_qr
import ransel.composeapp.generated.resources.capybara_with_duck
import ransel.composeapp.generated.resources.invalid_invite_expired_body
import ransel.composeapp.generated.resources.invalid_invite_expired_title
import ransel.composeapp.generated.resources.invalid_invite_full_body
import ransel.composeapp.generated.resources.invalid_invite_full_title
import ransel.composeapp.generated.resources.invalid_invite_home
import ransel.composeapp.generated.resources.invalid_invite_revoked_body
import ransel.composeapp.generated.resources.invalid_invite_revoked_title
import ransel.composeapp.generated.resources.invalid_invite_unknown_body
import ransel.composeapp.generated.resources.invalid_invite_unknown_title
import ransel.composeapp.generated.resources.join_trip_action
import ransel.composeapp.generated.resources.join_trip_later
import ransel.composeapp.generated.resources.join_trip_members
import ransel.composeapp.generated.resources.join_trip_sign_in
import ransel.composeapp.generated.resources.join_trip_sign_in_body
import ransel.composeapp.generated.resources.join_trip_sign_in_title
import ransel.composeapp.generated.resources.join_trip_title
import ransel.composeapp.generated.resources.join_trip_invited_as
import ransel.composeapp.generated.resources.role_editor
import ransel.composeapp.generated.resources.role_viewer

/**
 * Opened from an invite link or code (design `GabungTrip`, or `UndanganTidakBerlaku` when the invite fails).
 * [onSignIn] is shown to guests: the pending code is kept, so the invite comes back after signing in.
 */
@Composable
fun JoinTripScreen(
    code: String,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onJoined: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinTripViewModel = koinViewModel(key = "join-$code") { parametersOf(code) },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { if (it is JoinTripEvent.Joined) onJoined(it.tripId) }
    }
    val close = { viewModel.dismiss(); onClose() }

    val failure = state.failure
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        when {
            !state.signedIn -> FullScreenState(
                title = stringResource(Res.string.join_trip_sign_in_title),
                body = stringResource(Res.string.join_trip_sign_in_body, code),
                illustration = painterResource(Res.drawable.capybara_with_duck),
                primaryLabel = stringResource(Res.string.join_trip_sign_in),
                onPrimary = onSignIn,
                secondaryLabel = stringResource(Res.string.join_trip_later),
                onSecondary = close,
            )
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.preview == null && failure != null -> InvalidInviteContent(
                code = code,
                failure = failure,
                onRetry = viewModel::load,
                onHome = close,
            )
            state.preview != null -> JoinTripContent(
                preview = state.preview!!,
                joining = state.joining,
                // A rejected accept (invite filled up meanwhile) leaves the preview in place; show it as the invalid state.
                failure = failure,
                onJoin = viewModel::join,
                onLater = close,
                onClose = close,
            )
        }
    }
}

@Composable
fun JoinTripContent(
    preview: InvitePreviewDto,
    joining: Boolean,
    failure: ApiFailure?,
    onJoin: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormatter()
    val role = stringResource(if (preview.role == Role.VIEWER) Res.string.role_viewer else Res.string.role_editor)
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier.padding(horizontal = 4.dp).size(48.dp).clip(CircleShape)
                .clickable(role = SemanticsRole.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Symbol(Symbols.close, contentDescription = stringResource(Res.string.action_close)) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Image(painterResource(Res.drawable.capybara_with_duck), contentDescription = null, modifier = Modifier.size(200.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.join_trip_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    val invited = stringResource(Res.string.join_trip_invited_as, preview.ownerName)
                    Text(
                        buildAnnotatedString {
                            append("$invited ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(role) }
                            append(".")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Surface(
                    shape = IrisShape.lg,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TripCover(seed = preview.tripId, modifier = Modifier.size(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(preview.tripTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(
                                dates.range(preview.startDate, preview.endDate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            pluralStringResource(Res.plurals.join_trip_members, preview.memberCount, preview.memberCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (failure != null) {
                    Text(
                        invalidTitle(failure.code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = onJoin, enabled = !joining, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(Res.string.join_trip_action), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(Res.string.join_trip_later), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun invalidTitle(code: String): String = stringResource(
    when (code) {
        ErrorCode.INVITE_EXPIRED -> Res.string.invalid_invite_expired_title
        ErrorCode.INVITE_REVOKED -> Res.string.invalid_invite_revoked_title
        ErrorCode.INVITE_FULL -> Res.string.invalid_invite_full_title
        else -> Res.string.invalid_invite_unknown_title
    },
)

/** Design `UndanganTidakBerlaku`: one screen, the title and body follow the error code. */
@Composable
fun InvalidInviteContent(
    code: String,
    failure: ApiFailure,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        failure.isConnection -> ConnectionErrorState(onRetry, modifier, failure.code, onBack = onHome)
        failure.code == ErrorCode.INTERNAL_ERROR -> ServerErrorState(onRetry, modifier, failure.code, onBack = onHome)
        else -> FullScreenState(
            modifier = modifier,
            title = invalidTitle(failure.code),
            body = stringResource(
                when (failure.code) {
                    ErrorCode.INVITE_EXPIRED -> Res.string.invalid_invite_expired_body
                    ErrorCode.INVITE_REVOKED -> Res.string.invalid_invite_revoked_body
                    ErrorCode.INVITE_FULL -> Res.string.invalid_invite_full_body
                    else -> Res.string.invalid_invite_unknown_body
                },
                code,
            ),
            illustration = painterResource(Res.drawable.capybara_scanning_qr),
            errorCode = failure.code,
            // "Masukkan kode lain" needs the enter-code screen, which is not designed yet.
            primaryLabel = stringResource(Res.string.invalid_invite_home),
            onPrimary = onHome,
        )
    }
}

private val previewInvite = InvitePreviewDto(
    code = "BALI-7K2Q", tripId = "t1", tripTitle = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23),
    ownerName = "Septian", memberCount = 4, role = Role.EDITOR,
)

@Preview
@Composable
private fun JoinTripPreview() = IrisPreview {
    JoinTripContent(previewInvite, joining = false, failure = null, onJoin = {}, onLater = {}, onClose = {})
}

@Preview
@Composable
private fun InvalidInvitePreview() = IrisPreview {
    InvalidInviteContent("BALI-7K2Q", ApiFailure(ErrorCode.INVITE_EXPIRED, "expired", 410), onRetry = {}, onHome = {})
}
