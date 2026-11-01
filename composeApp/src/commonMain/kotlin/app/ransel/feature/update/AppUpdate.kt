package app.ransel.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import app.ransel.content.stringResource
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.platform.devicePlatform
import app.ransel.platform.installedAppVersion
import app.ransel.shared.dto.AppUpdateDto
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.shared.model.DevicePlatform
import app.ransel.ui.components.AppUpdateDialog
import org.koin.compose.koinInject
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.update_action
import ransel.composeapp.generated.resources.update_body_optional
import ransel.composeapp.generated.resources.update_body_required
import ransel.composeapp.generated.resources.update_later
import ransel.composeapp.generated.resources.update_title_optional
import ransel.composeapp.generated.resources.update_title_required

/**
 * Asks the server once whether this install is below the minimum Karung set. Null = show nothing: the web, no version
 * to compare, a failed call (offline, server down: nobody is locked out by a failed check) or nothing to update.
 */
suspend fun RanselApi.pendingUpdate(platform: DevicePlatform?, version: String?): AppUpdateDto? {
    if (platform == null || version == null) return null
    val update = (appUpdate(platform, version) as? ApiResult.Success)?.value ?: return null
    return update.takeIf { it.status != AppUpdateStatus.NONE && !it.storeUrl.isNullOrBlank() }
}

/**
 * The update dialog, checked once per launch when [ready] (past the splash). OPTIONAL has "Nanti" and comes back on the
 * next launch; REQUIRED cannot be closed and "Perbarui" opens the store.
 */
@Composable
fun AppUpdatePrompt(ready: Boolean) {
    val api: RanselApi = koinInject()
    val uriHandler = LocalUriHandler.current
    var update by remember { mutableStateOf<AppUpdateDto?>(null) }
    var later by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready && update == null) update = api.pendingUpdate(devicePlatform, installedAppVersion)
    }
    val pending = update ?: return
    val required = pending.status == AppUpdateStatus.REQUIRED
    if (later && !required) return
    val latest = pending.latestVersion.orEmpty()
    val current = installedAppVersion.orEmpty()
    AppUpdateDialog(
        title = stringResource(if (required) Res.string.update_title_required else Res.string.update_title_optional),
        body = if (required) stringResource(Res.string.update_body_required, latest, current)
        else stringResource(Res.string.update_body_optional, latest, current),
        updateLabel = stringResource(Res.string.update_action),
        onUpdate = { runCatching { uriHandler.openUri(pending.storeUrl.orEmpty()) } },
        laterLabel = if (required) null else stringResource(Res.string.update_later),
        onLater = if (required) null else ({ later = true }),
    )
}
