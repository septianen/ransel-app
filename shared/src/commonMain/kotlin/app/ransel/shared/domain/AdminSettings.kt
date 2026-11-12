package app.ransel.shared.domain

import app.ransel.shared.model.AiBudgetMode
import app.ransel.shared.model.SettingKey

/** Karung's update message (Bahasa Indonesia), counted in characters. */
const val MAX_UPDATE_MESSAGE = 280

/** Whole-number settings and the range Karung may set them to (admin-spec "Credit settings"). */
val SETTING_RANGES: Map<SettingKey, IntRange> = mapOf(
    SettingKey.SIGNUP_GRANT to 0..1_000,
    SettingKey.DAILY_FREE to 0..100,
    SettingKey.COST_SEARCH to 0..100,
    SettingKey.COST_NEXT_BATCH to 0..100,
    SettingKey.COST_PLACE_DETAIL to 0..100,
    SettingKey.COST_RECEIPT to 0..100,
    SettingKey.SUGGESTIONS_PER_HOUR to 1..1_000,
)

/** The budget in USD: 0 (none) up to this. */
const val MAX_AI_MONTHLY_BUDGET_USD = 100_000

val AI_BUDGET_ALERT_PERCENTS = listOf(80, 90, 100)

/**
 * Checks a value typed in Karung; the server applies the same rule. Null = fine, else the `details` code:
 * NOT_A_NUMBER, OUT_OF_RANGE, NOT_ALLOWED (an alert share, mode or model outside its list),
 * NOT_A_VERSION ("1.2.3"), NOT_A_URL (an https link), TOO_LONG (the update message). A minimum newer than the
 * latest, and a missing store link while a version is set, are [androidUpdateProblems] (`MIN_AFTER_LATEST`,
 * `STORE_URL_REQUIRED`): they need both versions and the link, which one field cannot see. [llmModels] = the
 * server's allow-list.
 */
fun validateSetting(key: SettingKey, value: String, llmModels: List<String>): String? {
    val text = value.trim()
    SETTING_RANGES[key]?.let { range ->
        val n = text.toIntOrNull() ?: return "NOT_A_NUMBER"
        return if (n in range) null else "OUT_OF_RANGE"
    }
    return when (key) {
        SettingKey.AI_MONTHLY_BUDGET_USD -> {
            val micro = usdToMicro(text) ?: return "NOT_A_NUMBER"
            if (micro in 0..MAX_AI_MONTHLY_BUDGET_USD * 1_000_000L) null else "OUT_OF_RANGE"
        }
        SettingKey.AI_BUDGET_ALERT_PERCENT -> if (text.toIntOrNull() in AI_BUDGET_ALERT_PERCENTS) null else "NOT_ALLOWED"
        SettingKey.AI_BUDGET_MODE -> if (AiBudgetMode.entries.any { it.name == text }) null else "NOT_ALLOWED"
        SettingKey.LLM_MODEL -> if (text in llmModels) null else "NOT_ALLOWED"
        SettingKey.ANDROID_MIN_VERSION, SettingKey.ANDROID_LATEST_VERSION ->
            if (text.isEmpty() || isValidAppVersion(text)) null else "NOT_A_VERSION"
        SettingKey.ANDROID_UPDATE_MESSAGE -> if (text.length <= MAX_UPDATE_MESSAGE) null else "TOO_LONG"
        SettingKey.ANDROID_STORE_URL -> when {
            text.isEmpty() -> null
            text.startsWith("https://") && text.length <= 300 && ' ' !in text -> null
            else -> "NOT_A_URL"
        }
        else -> null
    }
}

/**
 * The Android version fields together. Empty minimum = no hard gate; empty latest = no soft offer; empty link is
 * allowed only while both versions are empty (a prompt needs somewhere to go). Keys are the [SettingKey]s Karung
 * shows the error under.
 */
fun androidUpdateProblems(min: String, latest: String, storeUrl: String): Map<SettingKey, String> {
    val minText = min.trim()
    val latestText = latest.trim()
    val url = storeUrl.trim()
    val problems = mutableMapOf<SettingKey, String>()
    if (minText.isNotEmpty() && !isValidAppVersion(minText)) problems[SettingKey.ANDROID_MIN_VERSION] = "NOT_A_VERSION"
    if (latestText.isNotEmpty() && !isValidAppVersion(latestText)) problems[SettingKey.ANDROID_LATEST_VERSION] = "NOT_A_VERSION"
    if (
        SettingKey.ANDROID_MIN_VERSION !in problems &&
        SettingKey.ANDROID_LATEST_VERSION !in problems &&
        isValidAppVersion(minText) &&
        isValidAppVersion(latestText) &&
        (compareAppVersions(minText, latestText) ?: 0) > 0
    ) {
        problems[SettingKey.ANDROID_MIN_VERSION] = "MIN_AFTER_LATEST"
    }
    val needsLink = minText.isNotEmpty() || latestText.isNotEmpty()
    when {
        needsLink && url.isEmpty() -> problems[SettingKey.ANDROID_STORE_URL] = "STORE_URL_REQUIRED"
        url.isNotEmpty() && !(url.startsWith("https://") && url.length <= 300 && ' ' !in url) ->
            problems[SettingKey.ANDROID_STORE_URL] = "NOT_A_URL"
    }
    return problems
}

/** "170", "170.5" or "170.50" USD (a comma works too) to micro-USD; null when it is not such a number. */
fun usdToMicro(text: String): Long? {
    val clean = text.trim().replace(',', '.')
    if (!Regex("""\d{1,9}(\.\d{1,2})?""").matches(clean)) return null
    val whole = clean.substringBefore('.').toLong()
    val cents = clean.substringAfter('.', "").padEnd(2, '0').toLong()
    return whole * 1_000_000 + cents * 10_000
}

/** Micro-USD as the setting's text: "170", or "170.50" when there are cents. */
fun microToUsdText(micro: Long): String {
    val whole = micro / 1_000_000
    val cents = (micro % 1_000_000) / 10_000
    return if (cents == 0L) "$whole" else "$whole.${cents.toString().padStart(2, '0')}"
}
