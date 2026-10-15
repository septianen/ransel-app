package app.ransel.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.random.Random

/* Rules that must give the same answer on the form and on the server. */

data class PasswordCheck(val minLength: Boolean, val hasLetter: Boolean, val hasDigit: Boolean) {
    val isValid get() = minLength && hasLetter && hasDigit
}

/** Sign up / Kata sandi baru: at least 8 characters, one letter and one number (ticked live on screen). */
fun checkPassword(password: String) = PasswordCheck(
    minLength = password.length >= 8,
    hasLetter = password.any { it.isLetter() },
    hasDigit = password.any { it.isDigit() },
)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
fun isValidEmail(email: String) = EMAIL.matches(email.trim())

private val CURRENCY = Regex("^[A-Z]{3}$")
fun isValidCurrency(code: String) = CURRENCY.matches(code)

/** Field name → reason code, for ApiError.details. Empty = valid. */
fun validateTrip(title: String, startDate: LocalDate, endDate: LocalDate, baseCurrency: String, budgetMinor: Long?): Map<String, String> =
    buildMap {
        if (title.isBlank()) put("title", "REQUIRED")
        if (endDate < startDate) put("endDate", "MUST_BE_AFTER_START")
        if (!isValidCurrency(baseCurrency)) put("baseCurrency", "INVALID_CURRENCY")
        if (budgetMinor != null && budgetMinor < 0) put("budgetMinor", "MUST_NOT_BE_NEGATIVE")
    }

// ── Invite codes ────────────────────────────────────────────────────────────

/** No look-alikes: 0/O, 1/I/L removed. */
private const val INVITE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** "Bali 4 hari" → "BALI-7K2Q". Prefix = first word, letters only, max 8, fallback "TRIP". */
fun generateInviteCode(tripTitle: String, random: Random = Random.Default): String {
    val prefix = tripTitle.trim().split(Regex("\\s+")).firstOrNull()
        ?.filter { it in 'a'..'z' || it in 'A'..'Z' }?.uppercase()?.take(8)
        ?.takeIf { it.length >= 2 } ?: "TRIP"
    val suffix = (1..4).map { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }.joinToString("")
    return "$prefix-$suffix"
}

/** Public invite link. The host is the web app's domain (owner decision 2026-10-15). */
fun inviteUrl(code: String) = "https://ransel.mietek.tech/j/$code"

/** Daily Explore quota (TRD "Quota"). */
const val AI_DAILY_QUOTA = 10
