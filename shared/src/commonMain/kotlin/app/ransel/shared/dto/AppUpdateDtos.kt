package app.ransel.shared.dto

import app.ransel.shared.model.AppUpdateStatus
import kotlinx.serialization.Serializable

/**
 * GET /app-update?platform=ANDROID|IOS&version=<installed VERSION_NAME>, no sign-in. The server compares the
 * installed version with the minimum and latest set in Karung (`domain/AppVersion.kt`). Android asks once per cold
 * start (not on a debug build). A failed call shows nothing, so someone offline is never locked out. The web is never
 * asked. iOS is answered [AppUpdateStatus.NONE]: this check is Android only.
 *
 * - [AppUpdateStatus.NONE]: carry on.
 * - [AppUpdateStatus.OPTIONAL]: the "Update tersedia" dialog, snoozed once per [latestVersion] for this process.
 * - [AppUpdateStatus.REQUIRED]: the full-screen gate. It cannot be dismissed.
 *
 * [latestVersion] is the version named in the fallback copy: the store latest when Karung set one, otherwise the
 * minimum. Older apps (1 Nov 2026) read this field as the only version in the dialog, so it is never left empty
 * while [status] is a prompt. [minVersion] is the hard floor (null when Karung left it empty); older apps ignore it.
 * [message] is the Bahasa Indonesia body for both screens (null or blank = the app's own sentence); older apps ignore
 * it and keep their bundled copy. [storeUrl] is set whenever [status] is not NONE.
 */
@Serializable
data class AppUpdateDto(
    val status: AppUpdateStatus,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
    val minVersion: String? = null,
    val message: String? = null,
)
