package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto

/*
 * The links of an itinerary activity (booking, Instagram, a map ...): up to [MAX_ACTIVITY_LINKS] web addresses, label
 * made from the domain, no typed title (spec "Banyak link di aktivitas", owner 13 Nov 2026). Server and apps use the
 * same rules, so what the form accepts is what the server stores.
 */

const val MAX_ACTIVITY_LINKS = 5
const val MAX_ACTIVITY_LINK_LENGTH = 500
const val ACTIVITY_LINK_LABEL_MAX = 28

private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

/**
 * The address to store, or null when [raw] is empty or not a web address. Spaces around it are dropped; an address
 * without a scheme ("booking.com/hotel") becomes https; only http and https are accepted (so `javascript:`, `file:` and
 * free text are not), with a host that has a dot (or is `localhost`), no spaces inside, at most [MAX_ACTIVITY_LINK_LENGTH].
 */
fun normalizeActivityLink(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > MAX_ACTIVITY_LINK_LENGTH || text.any { it.isWhitespace() }) return null
    val scheme = SCHEME.find(text)?.groupValues?.get(1)
    val withScheme = when {
        // "host:8080/path" has no scheme: the part before the colon looks like a host (a dot)
        scheme == null || scheme.contains('.') -> "https://$text"
        (scheme.equals("http", true) || scheme.equals("https", true)) && text.regionMatches(scheme.length + 1, "//", 0, 2) ->
            scheme.lowercase() + text.substring(scheme.length)
        else -> return null
    }
    val host = hostOf(withScheme)
    if (host.isEmpty() || (!host.contains('.') && !host.equals("localhost", true))) return null
    if (host.startsWith('.') || host.endsWith('.') || host.contains("..")) return null
    return withScheme
}

/** The host of a stored link without a leading "www." ("https://www.instagram.com/x" is "instagram.com"). */
fun activityLinkHost(url: String): String = hostOf(url).removePrefix("www.")

private fun hostOf(url: String): String {
    val rest = url.substringAfter("://", url)
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfterLast('@').substringBefore(':').lowercase()
}

/**
 * The label shown for a link, from its domain: known names for the common ones, otherwise the domain without "www.",
 * shortened with an ellipsis at [ACTIVITY_LINK_LABEL_MAX].
 */
fun activityLinkLabel(url: String): String {
    val host = activityLinkHost(url)
    val path = url.substringAfter("://", url).substringAfter('/', "").lowercase()
    val known = when {
        host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am" -> "Instagram"
        host == "booking.com" || host.endsWith(".booking.com") -> "Booking.com"
        host == "maps.app.goo.gl" || host == "goo.gl" && path.startsWith("maps") -> "Google Maps"
        (host == "google.com" || host.startsWith("google.") || host.endsWith(".google.com")) && path.startsWith("maps") -> "Google Maps"
        host == "maps.google.com" -> "Google Maps"
        host == "wa.me" || host == "api.whatsapp.com" || host == "whatsapp.com" || host.endsWith(".whatsapp.com") -> "WhatsApp"
        else -> null
    }
    val label = known ?: host
    return if (label.length <= ACTIVITY_LINK_LABEL_MAX) label else label.take(ACTIVITY_LINK_LABEL_MAX - 1) + "…"
}

/**
 * The links to keep: each one normalized ([normalizeActivityLink]), the ones that are not web addresses and the duplicates
 * dropped, in order, at most [MAX_ACTIVITY_LINKS]. What the server stores.
 */
fun cleanActivityLinks(links: List<String>): List<String> =
    links.mapNotNull(::normalizeActivityLink).distinct().take(MAX_ACTIVITY_LINKS)

/**
 * Field problems of a list the client sent (empty rows are fine, the client drops them): `links` = TOO_MANY above
 * [MAX_ACTIVITY_LINKS] non-empty ones, `links[i]` = INVALID for one that is not a web address. Duplicates are not an
 * error: the server keeps one.
 */
fun validateActivityLinks(links: List<String>): Map<String, String> = buildMap {
    if (links.count { it.isNotBlank() } > MAX_ACTIVITY_LINKS) put("links", "TOO_MANY")
    links.forEachIndexed { index, raw -> if (raw.isNotBlank() && normalizeActivityLink(raw) == null) put("links[$index]", "INVALID") }
}

/**
 * The links of an item whichever app wrote it: the list when it has one, else the old single booking link (an app from
 * before the list sends only that).
 */
fun ItineraryItemDto.activityLinks(): List<String> =
    links ?: cleanActivityLinks(listOfNotNull(bookingUrl))
