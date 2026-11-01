package app.ransel.shared.dto

import app.ransel.shared.model.AppUpdateStatus
import kotlinx.serialization.Serializable

/**
 * GET /app-update?platform=ANDROID|IOS&version=<installed version>, no sign-in. The server compares the installed
 * version with the minimum set in Karung (`domain/AppVersion.kt`). The apps ask once per launch and show nothing when
 * the call fails, so a person offline is never locked out.
 *
 * - [AppUpdateStatus.NONE]: carry on. [AppUpdateStatus.OPTIONAL]: a dialog the person can dismiss.
 *   [AppUpdateStatus.REQUIRED]: a dialog that cannot be dismissed.
 * - [latestVersion] = the minimum set in Karung, shown in the dialog; null when [status] is NONE.
 * - [storeUrl] = where "Perbarui" goes. It is set whenever [status] is not NONE: with no link set for the platform the
 *   server answers NONE, because a dialog nobody can act on would lock people out.
 */
@Serializable
data class AppUpdateDto(
    val status: AppUpdateStatus,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
)
