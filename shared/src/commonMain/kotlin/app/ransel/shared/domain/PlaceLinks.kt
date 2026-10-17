package app.ransel.shared.domain

/**
 * Link that opens a place in Google Maps (and lets the user check the hours there). Needs no API key:
 * https://developers.google.com/maps/documentation/urls/get-started#search-action
 */
fun googleMapsPlaceUrl(name: String, placeId: String): String =
    "https://www.google.com/maps/search/?api=1&query=${percentEncode(name)}&query_place_id=${percentEncode(placeId)}"

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
private const val HEX = "0123456789ABCDEF"

/** RFC 3986 percent-encoding of the UTF-8 bytes of [value]. */
internal fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        if (b < 128 && UNRESERVED.indexOf(b.toChar()) >= 0) {
            append(b.toChar())
        } else {
            append('%')
            append(HEX[b shr 4])
            append(HEX[b and 0x0F])
        }
    }
}
