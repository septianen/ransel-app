package app.ransel.shared.domain

/**
 * The title of an item made from an idea: the activity only (owner, 23 Nov 2026). The model sometimes writes the region
 * into the activity ("Marine wildlife photography · Taman Nasional Komodo, NTT"); the region belongs in the place line.
 */
fun ideaActivityTitle(activity: String, region: String): String {
    val trimmed = activity.trim()
    val parts = trimmed.split(" · ", " - ", " – ")
    if (parts.size < 2) return trimmed
    val head = parts.first().trim()
    val tail = trimmed.removePrefix(parts.first()).trimStart(' ', '·', '-', '–').trim().lowercase()
    val area = region.trim().lowercase()
    // only a tail that is the region (or its first part) is cut; "Snorkeling - pemula" stays as it is
    val regionHead = area.substringBefore(',').trim()
    return if (head.isNotEmpty() && area.isNotEmpty() && (tail == area || tail.startsWith(regionHead))) head else trimmed
}
