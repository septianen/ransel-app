package app.ransel.feature.trips

import app.ransel.shared.dto.TripDto

/**
 * "Cari trip" on the trip list: a local filter over the trips already loaded (Room on mobile, the API list
 * on the web). Case-insensitive, accents ignored ("Bogotá" matches "bogota"), and every word of the query
 * must appear in the title or the destination, in any order.
 */
fun TripDto.matchesSearch(query: String): Boolean {
    val words = foldForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = foldForSearch("$title $destination")
    return words.all { it in haystack }
}

/** Applies the search to every group; a blank query keeps the lists as they are. */
fun TripListUiState.Content.searched(query: String): TripListUiState.Content {
    if (query.isBlank()) return copy(query = "")
    fun List<TripCardData>.keep() = filter { it.trip.matchesSearch(query) }
    return copy(ongoing = ongoing.keep(), upcoming = upcoming.keep(), finished = finished.keep(), query = query.trim())
}

/** Lower case, Latin accents removed, whitespace collapsed to single spaces. */
internal fun foldForSearch(text: String): String = buildString(text.length) {
    var space = false
    for (c in text.lowercase()) {
        if (c.isWhitespace()) {
            space = true
            continue
        }
        if (space && isNotEmpty()) append(' ')
        space = false
        append(ACCENTS[c] ?: c)
    }
}

/** No `java.text.Normalizer` in common code: the Latin letters with diacritics that trip names actually use. */
private val ACCENTS: Map<Char, Char> = buildMap {
    fun map(base: Char, accented: String) = accented.forEach { put(it, base) }
    map('a', "àáâãäåāăą")
    map('c', "çćĉċč")
    map('d', "ďđ")
    map('e', "èéêëēĕėęě")
    map('g', "ĝğġģ")
    map('h', "ĥħ")
    map('i', "ìíîïĩīĭįı")
    map('j', "ĵ")
    map('k', "ķ")
    map('l', "ĺļľŀł")
    map('n', "ñńņňŉ")
    map('o', "òóôõöøōŏő")
    map('r', "ŕŗř")
    map('s', "śŝşšș")
    map('t', "ţťŧț")
    map('u', "ùúûüũūŭůűų")
    map('w', "ŵ")
    map('y', "ýÿŷ")
    map('z', "źżž")
}
