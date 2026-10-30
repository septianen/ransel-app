package app.ransel.shared.domain

import app.ransel.shared.model.AiBudgetMode
import app.ransel.shared.model.SettingKey

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
 * NOT_A_NUMBER, OUT_OF_RANGE, NOT_ALLOWED (an alert share, mode or model outside its list). [llmModels] = the
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
        else -> null
    }
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
