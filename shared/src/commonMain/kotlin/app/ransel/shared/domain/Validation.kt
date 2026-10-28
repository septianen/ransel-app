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

/** Field name → reason code, for ApiError.details. Empty = valid. Both dates null is an undated trip; one alone is not. */
fun validateTrip(title: String, startDate: LocalDate?, endDate: LocalDate?, baseCurrency: String, budgetMinor: Long?): Map<String, String> =
    buildMap {
        if (title.isBlank()) put("title", "REQUIRED")
        if (startDate == null && endDate != null) put("startDate", "REQUIRED")
        if (endDate == null && startDate != null) put("endDate", "REQUIRED")
        if (startDate != null && endDate != null && endDate < startDate) put("endDate", "MUST_BE_AFTER_START")
        if (!isValidCurrency(baseCurrency)) put("baseCurrency", "INVALID_CURRENCY")
        if (budgetMinor != null && budgetMinor < 0) put("budgetMinor", "MUST_NOT_BE_NEGATIVE")
    }

// ── Invite codes ────────────────────────────────────────────────────────────

/** No look-alikes: 0/O, 1/I/L removed. */
private const val INVITE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** Random part of a new code: 31^8 ≈ 8.5 × 10^11, too many to guess even with the prefix known. */
const val INVITE_SUFFIX_LENGTH = 8

/**
 * "Bali 4 hari" → "BALI-7K2QM9XA". Prefix = first word, letters only, max 8, fallback "TRIP". The prefix is easy to
 * guess, so only the suffix protects the trip: the server passes a secure [random]. Codes made before 28 Oct 2026
 * have a 4-character suffix and stay valid; nothing parses the length.
 */
fun generateInviteCode(tripTitle: String, random: Random = Random.Default): String {
    val prefix = tripTitle.trim().split(Regex("\\s+")).firstOrNull()
        ?.filter { it in 'a'..'z' || it in 'A'..'Z' }?.uppercase()?.take(8)
        ?.takeIf { it.length >= 2 } ?: "TRIP"
    val suffix = (1..INVITE_SUFFIX_LENGTH).map { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }.joinToString("")
    return "$prefix-$suffix"
}

/** Public invite link. The host is the web app's domain (owner decision 2026-10-15). */
fun inviteUrl(code: String) = "https://ransel.mietek.tech/j/$code"

