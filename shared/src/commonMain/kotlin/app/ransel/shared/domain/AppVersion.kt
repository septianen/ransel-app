package app.ransel.shared.domain

import app.ransel.shared.model.AppUpdateStatus

/** A version as Karung stores it: "1.2.3", each part 0..9999. */
private val STRICT_VERSION = Regex("""\d{1,4}\.\d{1,4}\.\d{1,4}""")

/** True when [text] is a version Karung accepts for a minimum (no suffix). */
fun isValidAppVersion(text: String): Boolean = STRICT_VERSION.matches(text.trim())

/**
 * The numbers of an installed version: "0.1.0" or "0.1.0-debug" (anything after the third number is ignored; a
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
 * What to do for an app at [installed] when Karung sets [minimum] ("" = none) and [force]. An installed version that
 * cannot be read, or a minimum that is not a version, never prompts (a wrong setting must not lock anyone out).
 */
fun appUpdateStatus(installed: String, minimum: String, force: Boolean): AppUpdateStatus {
    if (minimum.isBlank()) return AppUpdateStatus.NONE
    val cmp = compareAppVersions(installed, minimum) ?: return AppUpdateStatus.NONE
    return when {
        cmp >= 0 -> AppUpdateStatus.NONE
        force -> AppUpdateStatus.REQUIRED
        else -> AppUpdateStatus.OPTIONAL
    }
}
