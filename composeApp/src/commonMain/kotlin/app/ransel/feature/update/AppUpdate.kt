package app.ransel.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.platform.devicePlatform
import app.ransel.platform.installedAppVersion
import app.ransel.platform.isDebugBuild
import app.ransel.platform.rememberPlayStoreOpener
import app.ransel.shared.domain.promptedUpdate
import app.ransel.shared.dto.AppUpdateDto
import app.ransel.shared.model.AppUpdateStatus
import app.ransel.shared.model.DevicePlatform
import app.ransel.ui.components.ForceUpdateScreen
import app.ransel.ui.components.SoftUpdateDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Android release builds only. A debug build (`app.ransel.debug`) and iOS and the web are never asked. */
fun shouldAskForUpdate(platform: DevicePlatform?, debug: Boolean): Boolean =
    platform == DevicePlatform.ANDROID && !debug

/** One check of `GET /app-update`. [UpdateCheck.Failed] is a transport or server error: show nothing, or keep the gate. */
sealed interface UpdateCheck {
    data class Ready(val update: AppUpdateDto) : UpdateCheck
    data object Failed : UpdateCheck
}

suspend fun RanselApi.checkForUpdate(version: String): UpdateCheck =
    when (val result = appUpdate(DevicePlatform.ANDROID, version)) {
        is ApiResult.Success -> UpdateCheck.Ready(result.value)
        is ApiResult.Failure -> UpdateCheck.Failed
    }

/**
 * The latest version hidden by Nanti. Snapshot state for this process, not `remember`: writing it recomposes and
 * closes the dialog, and a rebuilt screen (rotation, `key(language)`) still sees the snooze. A new process starts empty.
 */
internal object UpdateSnooze {
    var latestVersion by mutableStateOf<String?>(null)
}

/**
 * The notification explainer is a dialog window, so it paints over the full-screen gate. It stays down while the
 * update is [AppUpdateStatus.REQUIRED]. A soft offer and no update leave it free to show.
 */
fun notificationSheetAllowed(update: AppUpdateStatus?): Boolean = update != AppUpdateStatus.REQUIRED

/**
 * What to put on screen. NONE, a missing store link, and an OPTIONAL snoozed for this [snoozedLatest] show nothing.
 * REQUIRED is never snoozed. Pass [UpdateSnooze.latestVersion]: a new process, or a new latest, asks again.
 */
fun visibleUpdate(update: AppUpdateDto?, snoozedLatest: String?): AppUpdateStatus? {
    if (update == null || update.storeUrl.isNullOrBlank()) return null
    val status = promptedUpdate(update.status, update.latestVersion, snoozedLatest)
    return status.takeIf { it != AppUpdateStatus.NONE }
}

/**
 * Checked once after the splash. Below the minimum: a full-screen gate. Between minimum and latest: "Update tersedia"
 * once per latest for this process. A failed check shows nothing and does not enforce a cached minimum. Coming back
 * from the store re-checks; a failure then keeps the gate that is already up.
 */
@Composable
fun AppUpdatePrompt(ready: Boolean, onStatus: (AppUpdateStatus?) -> Unit = {}) {
    val api: RanselApi = koinInject()
    val openStore = rememberPlayStoreOpener()
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf<AppUpdateDto?>(null) }
    var opening by remember { mutableStateOf(false) }
    var storeError by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (!ready || started) return@LaunchedEffect
        started = true
        if (!shouldAskForUpdate(devicePlatform, isDebugBuild)) return@LaunchedEffect
        val version = installedAppVersion ?: return@LaunchedEffect
        when (val check = api.checkForUpdate(version)) {
            is UpdateCheck.Ready -> held = check.update
            UpdateCheck.Failed -> held = null
        }
    }

    val update = held
    val status = visibleUpdate(update, UpdateSnooze.latestVersion)
    SideEffect { onStatus(status) }
    if (update == null || status == null) return
    val versionName = update.latestVersion.orEmpty()
    val current = installedAppVersion.orEmpty()
    val message = update.message

    fun open() {
        storeError = false
        val opened = openStore(update.storeUrl)
        opening = opened
        if (!opened) storeError = true
    }

    fun returned() {
        opening = false
        val version = installedAppVersion ?: return
        scope.launch {
            when (val check = api.checkForUpdate(version)) {
                is UpdateCheck.Ready -> held = check.update
                UpdateCheck.Failed -> Unit
            }
        }
    }

    fun later() {
        UpdateSnooze.latestVersion = versionName
        opening = false
        storeError = false
    }

    when (status) {
        AppUpdateStatus.REQUIRED -> ForceUpdateScreen(
            versionName = versionName,
            currentVersion = current,
            message = message,
            opening = opening,
            storeError = storeError,
            onUpdate = ::open,
            onReturned = ::returned,
        )
        AppUpdateStatus.OPTIONAL -> SoftUpdateDialog(
            versionName = versionName,
            currentVersion = current,
            message = message,
            opening = opening,
            storeError = storeError,
            onUpdate = ::open,
            onLater = ::later,
            onReturned = ::returned,
        )
        AppUpdateStatus.NONE -> Unit
    }
}
