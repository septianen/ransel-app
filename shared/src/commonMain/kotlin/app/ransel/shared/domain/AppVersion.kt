package app.ransel.shared.domain

import app.ransel.shared.model.AppUpdateStatus

/** A version as Karung stores it: "1.2.3", each part 0..9999. */
private val STRICT_VERSION = Regex("""\d{1,4}\.\d{1,4}\.\d{1,4}""")

/** The Play listing this app opens when `market://` has no handler and Karung left no https link. */
const val PLAY_STORE_MARKET = "market://details?id=app.ransel"

/** Https fallback after [PLAY_STORE_MARKET]. Also the env default for `ANDROID_STORE_URL`. */
const val PLAY_STORE_HTTPS = "https://play.google.com/store/apps/details?id=app.ransel"

/** True when [text] is a version Karung accepts for a minimum or a latest (no suffix). */
fun isValidAppVersion(text: String): Boolean = STRICT_VERSION.matches(text.trim())

/**
 * The numbers of an installed version: "1.0.0" or "1.0.0-debug" (anything after the third number is ignored; a
 * missing part counts as 0). Null when the text does not start with a number.
 */
fun parseAppVersion(text: String): List<Int>? {
    val core = Regex("""^\d{1,4}(\.\d{1,4}){0,2}""").find(text.trim())?.value ?: return null
    val parts = core.split('.').map { it.toInt() }
    return parts + List(3 - parts.size) { 0 }
}

/** Negative when [a] is older than [b], 0 when equal, positive when newer. Unparsable text counts as unknown: null. */
fun compareAppVersions(a: String, b: String): Int? {
    val x = parseAppVersion(a) ?: return null
    val y = parseAppVersion(b) ?: return null
    return x.indices.firstNotNullOfOrNull { i -> (x[i] - y[i]).takeIf { it != 0 } } ?: 0
}

/**
 * What to do for an app at [installed] (`VERSION_NAME`) when Karung sets [minimum] and [latest] ("" = that floor is
 * off). Compared with [compareAppVersions]. An installed version that cannot be read never prompts.
 *
 * - Below a set minimum: [AppUpdateStatus.REQUIRED] (the full-screen gate). A blank latest does not soften it.
 * - At least the minimum (or no minimum) and below a set latest: [AppUpdateStatus.OPTIONAL].
 * - Otherwise: [AppUpdateStatus.NONE].
 *
 * A blank store link is decided by the caller: a prompt with nowhere to go must not be shown.
 */
fun appUpdateStatus(installed: String, minimum: String, latest: String): AppUpdateStatus {
    if (parseAppVersion(installed) == null) return AppUpdateStatus.NONE
    val min = minimum.trim()
    val lat = latest.trim()
    val belowMin = isValidAppVersion(min) && (compareAppVersions(installed, min)?.let { it < 0 } == true)
    if (belowMin) return AppUpdateStatus.REQUIRED
    if (!isValidAppVersion(lat)) return AppUpdateStatus.NONE
    val cmp = compareAppVersions(installed, lat) ?: return AppUpdateStatus.NONE
    return if (cmp < 0) AppUpdateStatus.OPTIONAL else AppUpdateStatus.NONE
}

/**
 * [status] after a soft snooze. [snoozedLatest] is the `latestVersion` hidden for the rest of this process; a
 * different latest shows the offer again. [AppUpdateStatus.REQUIRED] is never snoozed.
 */
fun promptedUpdate(status: AppUpdateStatus, latestVersion: String?, snoozedLatest: String?): AppUpdateStatus {
    if (status == AppUpdateStatus.OPTIONAL && !latestVersion.isNullOrBlank() && latestVersion == snoozedLatest) {
        return AppUpdateStatus.NONE
    }
    return status
}

/**
 * Https link to open when `market://` has no handler: Karung's link when it is an https URL, otherwise
 * [PLAY_STORE_HTTPS].
 */
fun playStoreHttps(karungUrl: String?): String {
    val text = karungUrl?.trim().orEmpty()
    return if (text.startsWith("https://") && text.length <= 300 && ' ' !in text) text else PLAY_STORE_HTTPS
}
