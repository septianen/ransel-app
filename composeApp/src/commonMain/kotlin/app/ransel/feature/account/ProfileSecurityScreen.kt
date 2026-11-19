package app.ransel.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.auth.SessionState
import app.ransel.data.remote.apiImageUrl
import app.ransel.shared.domain.PhotoKind
import app.ransel.ui.components.PhotoSourceSheet
import app.ransel.ui.components.text
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import coil3.compose.AsyncImage
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.account_danger_zone
import ransel.composeapp.generated.resources.account_delete
import ransel.composeapp.generated.resources.account_email
import ransel.composeapp.generated.resources.account_email_readonly
import ransel.composeapp.generated.resources.account_photo_change
import ransel.composeapp.generated.resources.account_photo_name
import ransel.composeapp.generated.resources.account_photo_sheet_title
import ransel.composeapp.generated.resources.account_profile_security
import ransel.composeapp.generated.resources.change_password_title

@Composable
fun ProfileSecurityScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val session by viewModel.sessionState.collectAsState()
    val user = (session as? SessionState.SignedIn)?.user
    val photo by viewModel.photo.collectAsState()
    val nameState by viewModel.name.collectAsState()
    var photoSheet by rememberSaveable { mutableStateOf(false) }
    var nameSheet by rememberSaveable { mutableStateOf(false) }

    ProfileSecurityContent(
        name = user?.displayName.orEmpty(),
        email = user?.email.orEmpty(),
        avatarUrl = user?.avatarUrl,
        photoBusy = photo.busy,
        photoProblem = photo.problem?.text(),
        onBack = onBack,
        onPhotoAndName = {
            viewModel.resetName()
            nameSheet = true
        },
        onChangePassword = onChangePassword,
        onDeleteAccount = onDeleteAccount,
        modifier = modifier,
    )

    if (user != null && nameSheet) {
        if (nameState.saved) LaunchedEffect(Unit) {
            nameSheet = false
            viewModel.resetName()
        }
        PhotoAndNameSheet(
            name = user.displayName,
            avatarUrl = user.avatarUrl,
            photoBusy = photo.busy,
            nameState = nameState,
            onChangePhoto = { photoSheet = true },
            onSaveName = viewModel::saveName,
            onDismiss = { nameSheet = false },
        )
    }
    if (user != null && photoSheet) {
        PhotoSourceSheet(
            title = stringResource(Res.string.account_photo_sheet_title),
            kind = PhotoKind.AVATAR,
            onPicked = viewModel::changePhoto,
            onUnreadable = viewModel::photoUnreadable,
            onRemove = if (user.avatarUrl != null) viewModel::removePhoto else null,
            onDismiss = { photoSheet = false },
        )
    }
}

@Composable
internal fun ProfileSecurityContent(
    name: String,
    email: String,
    avatarUrl: String?,
    photoBusy: Boolean,
    photoProblem: String?,
    onBack: () -> Unit,
    onPhotoAndName: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccountSubScreenTopBar(stringResource(Res.string.account_profile_security), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp),
        ) {
            AccountGroupCard {
                AccountSettingRow(Symbols.photoCamera, stringResource(Res.string.account_photo_name), onClick = onPhotoAndName)
                AccountSettingRow(
                    Symbols.mail,
                    stringResource(Res.string.account_email),
                    subtitle = email,
                    value = stringResource(Res.string.account_email_readonly),
                    onClick = null,
                    showDivider = true,
                )
                AccountSettingRow(Symbols.lock, stringResource(Res.string.change_password_title), onClick = onChangePassword, showDivider = true)
            }
            photoProblem?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            AccountGroupLabel(stringResource(Res.string.account_danger_zone))
            AccountGroupCard {
                AccountSettingRow(
                    Symbols.delete,
                    stringResource(Res.string.account_delete),
                    onClick = onDeleteAccount,
                    destructive = true,
                )
            }
        }
    }
}

/**
 * Photo & name opens the existing name sheet; the photo picker is offered from the same tap by stacking
 * [PhotoSourceSheet] after dismiss, so both current actions stay available without a new destination.
 */
@Composable
internal fun PhotoNameHeader(name: String, avatarUrl: String?, busy: Boolean, onChangePhoto: () -> Unit) {
    val changeLabel = stringResource(Res.string.account_photo_change)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = changeLabel, onClick = onChangePhoto)
                .semantics { contentDescription = changeLabel },
        ) {
            Box(
                Modifier.size(48.dp).align(Alignment.Center).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                apiImageUrl(avatarUrl)?.let { url ->
                    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                if (busy) {
                    Box(
                        Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Symbol(Symbols.photoCamera, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
    }
}

@Preview
@Composable
private fun ProfileSecurityPreview() = IrisPreview {
    ProfileSecurityContent(
        name = "Dahyun",
        email = "test@gmail.com",
        avatarUrl = null,
        photoBusy = false,
        photoProblem = null,
        onBack = {},
        onPhotoAndName = {},
        onChangePassword = {},
        onDeleteAccount = {},
    )
}
