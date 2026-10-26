package app.ransel.shared.domain

/** Names a guest writes on the guest page ("Nama saya tidak ada"). */
const val MAX_GUEST_NAME = 40

/** null = fine; else REQUIRED or TOO_LONG. */
fun guestNameProblem(name: String): String? = when {
    name.isBlank() -> "REQUIRED"
    name.trim().length > MAX_GUEST_NAME -> "TOO_LONG"
    else -> null
}

/** "  rina   putri" and "Rina Putri" are the same person on the guest page. */
fun normalizeGuestName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/** The member among [members] (id to name) whose name is the same as [name], if any. */
fun sameNameMember(name: String, members: List<Pair<String, String>>): String? {
    val wanted = normalizeGuestName(name)
    return members.firstOrNull { normalizeGuestName(it.second) == wanted }?.first
}

/** A WhatsApp link that lets the user pick the chat ("Bagikan ke WhatsApp"); RANSEL sends nothing itself. */
fun whatsappShareLink(message: String): String = "https://wa.me/?text=${percentEncode(message)}"
