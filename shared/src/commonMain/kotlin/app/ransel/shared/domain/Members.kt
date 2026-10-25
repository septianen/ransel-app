package app.ransel.shared.domain

/* Members without an account, WhatsApp links and @mentions (TRD "Group trip features"). Same rules on forms and server. */

const val MAX_MEMBER_NAME = 40
const val MAX_COMMENT_LENGTH = 1000

/** Field name → reason code, for ApiError.details. Empty = valid. */
fun validateMemberName(name: String): Map<String, String> = buildMap {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) put("displayName", "REQUIRED")
    else if (trimmed.length > MAX_MEMBER_NAME) put("displayName", "TOO_LONG")
}

fun validateCommentBody(body: String): Map<String, String> = buildMap {
    if (body.isBlank()) put("body", "REQUIRED")
    else if (body.length > MAX_COMMENT_LENGTH) put("body", "TOO_LONG")
}

/** "Rina Putri" → "RP", "rina" → "R". Used by avatars; at most two letters. */
fun memberInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }

/**
 * Digits only, country code included and no plus: what `https://wa.me/<phone>` wants and what the server stores.
 * "0812-3456-7890" and "+62 812 3456 7890" both give "6281234567890". Null when it cannot be a phone number
 * (less than 9 or more than 15 digits, or a number without a country code that does not start with 0, 62 or 8).
 */
fun normalizePhone(raw: String): String? {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    val hasPlus = trimmed.startsWith("+")
    val full = when {
        digits.isEmpty() -> return null
        hasPlus -> digits
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("62") -> digits
        digits.startsWith("8") -> "62$digits"
        else -> return null
    }
    return full.takeIf { it.length in 9..15 }
}

/** `https://wa.me/<phone>?text=<message>`, or null when there is no number (the app opens the share sheet instead). */
fun whatsappLink(phone: String?, message: String): String? {
    val number = phone?.let(::normalizePhone) ?: return null
    return "https://wa.me/$number?text=${percentEncode(message)}"
}

/** A member a comment can mention. */
data class MentionCandidate(val memberId: String, val displayName: String)

/**
 * Member ids mentioned as "@Name" in [body] (case-insensitive, the name must end at a space, punctuation or the end).
 * The longest name wins, so "@Rina Putri" is not read as "@Rina". Each member once, in order of appearance.
 */
fun extractMentions(body: String, members: List<MentionCandidate>): List<String> {
    val byLength = members.sortedByDescending { it.displayName.length }
    val found = LinkedHashSet<String>()
    var i = 0
    while (i < body.length) {
        if (body[i] == '@' && (i == 0 || !body[i - 1].isLetterOrDigit())) {
            val match = byLength.firstOrNull { m ->
                val end = i + 1 + m.displayName.length
                end <= body.length &&
                    body.regionMatches(i + 1, m.displayName, 0, m.displayName.length, ignoreCase = true) &&
                    (end == body.length || !body[end].isLetterOrDigit())
            }
            if (match != null) {
                found += match.memberId
                i += 1 + match.displayName.length
                continue
            }
        }
        i++
    }
    return found.toList()
}
