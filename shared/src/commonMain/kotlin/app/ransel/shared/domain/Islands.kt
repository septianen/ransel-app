package app.ransel.shared.domain

/*
 * Which big island of Indonesia a point is on, for the interview's "beda pulau" rule (plan "Wawancara Buat trip", W2).
 * Coarse on purpose: a handful of hand-drawn outlines, no map data, so it runs offline in the apps and gives the same
 * answer on the server. Small islands (Batam, Bangka, Nias, Ternate, the Gilis ...) are not listed and answer null, which
 * the caller treats as "do not know" and falls back to distance alone. Nothing here is a border: do not use it to show
 * a place's island to the user.
 */

enum class Island {
    SUMATERA, JAWA, BALI, LOMBOK, SUMBAWA, FLORES, SUMBA, TIMOR, KALIMANTAN, SULAWESI, PAPUA, HALMAHERA, SERAM,
}

private class Outline(val island: Island, val points: List<Pair<Double, Double>>)

/** (lat, lng) corners. The first outline that contains a point wins, so a small island is listed before the big one next to it. */
private val OUTLINES = listOf(
    // Bali with Nusa Penida and Lembongan; its west edge sits on the Bali Strait, Java's east edge a little further
    Outline(
        Island.BALI,
        listOf(8.05 to 114.40, 8.10 to 115.00, 8.25 to 115.70, 8.80 to 115.75, 8.90 to 115.50, 8.85 to 115.00, 8.75 to 114.55, 8.20 to 114.40)
            .map { -it.first to it.second },
    ),
    Outline(Island.LOMBOK, listOf(-8.2 to 115.8, -8.2 to 116.75, -9.0 to 116.75, -9.0 to 115.8)),
    Outline(Island.SUMBAWA, listOf(-8.0 to 116.8, -8.0 to 119.2, -9.1 to 119.2, -9.1 to 116.8)),
    Outline(Island.FLORES, listOf(-8.1 to 119.7, -8.1 to 123.2, -8.9 to 123.2, -8.9 to 119.7)),
    Outline(Island.SUMBA, listOf(-9.2 to 118.9, -9.2 to 120.4, -10.4 to 120.4, -10.4 to 118.9)),
    Outline(Island.TIMOR, listOf(-8.25 to 123.4, -8.25 to 127.4, -10.5 to 127.4, -10.5 to 123.4)),
    Outline(
        Island.JAWA,
        listOf(
            -5.9 to 105.9, -5.9 to 107.0, -6.1 to 108.3, -6.6 to 109.2, -6.65 to 110.0, -6.4 to 110.9, -6.6 to 111.4,
            -6.8 to 112.2, -6.8 to 113.0, -6.75 to 114.0, -7.2 to 114.3, -7.8 to 114.45, -8.3 to 114.45, -8.75 to 114.45,
            -8.8 to 113.5, -8.5 to 112.0, -8.4 to 111.0, -8.2 to 110.0, -8.0 to 109.0, -7.8 to 108.0, -7.5 to 107.0,
            -7.2 to 106.4, -6.9 to 105.7, -6.8 to 105.2, -6.4 to 105.4, -6.1 to 105.7,
        ),
    ),
    Outline(
        Island.SUMATERA,
        listOf(
            5.95 to 95.2, 5.55 to 95.6, 5.0 to 97.4, 4.0 to 98.35, 3.45 to 99.3, 2.3 to 100.75, 1.65 to 101.55,
            0.35 to 103.0, -0.3 to 103.5, -1.2 to 104.25, -2.4 to 105.05, -3.3 to 105.8, -4.3 to 106.0, -5.3 to 105.95,
            -5.9 to 105.85, -5.95 to 105.4, -5.9 to 104.7, -5.2 to 103.9, -3.8 to 102.2, -2.6 to 101.0, -0.95 to 100.2,
            0.5 to 99.1, 1.74 to 98.6, 3.5 to 96.5, 4.15 to 95.95, 5.55 to 95.2,
        ),
    ),
    Outline(
        Island.KALIMANTAN,
        listOf(
            2.1 to 109.0, 3.6 to 111.0, 4.4 to 113.5, 5.0 to 115.2, 4.4 to 117.8, 2.0 to 118.2, 1.0 to 119.0,
            -0.5 to 117.9, -1.3 to 116.9, -2.9 to 116.3, -4.2 to 114.6, -3.6 to 112.0, -3.1 to 111.2, -2.3 to 110.0,
            -1.0 to 109.0, 0.5 to 108.9,
        ),
    ),
    Outline(
        Island.SULAWESI,
        listOf(
            0.8 to 120.0, 1.2 to 121.0, 1.15 to 124.0, 1.8 to 125.1, 1.2 to 125.2, 0.45 to 124.2, 0.35 to 123.0,
            -0.9 to 123.5, -1.9 to 122.4, -2.5 to 121.5, -3.4 to 121.9, -4.0 to 122.9, -4.8 to 122.9, -4.0 to 121.6,
            -3.5 to 121.1, -3.0 to 120.4, -4.5 to 120.5, -5.6 to 120.5, -5.7 to 119.5, -5.1 to 119.35, -3.6 to 119.3,
            -2.5 to 118.8, -1.5 to 119.3, -0.5 to 119.7, 0.2 to 119.7,
        ),
    ),
    Outline(Island.HALMAHERA, listOf(2.5 to 127.4, 2.5 to 129.0, -0.9 to 129.0, -0.9 to 127.4)),
    Outline(Island.SERAM, listOf(-2.7 to 128.0, -2.7 to 130.9, -3.9 to 130.9, -3.9 to 128.0)),
    Outline(Island.PAPUA, listOf(0.0 to 130.9, 0.0 to 141.0, -9.2 to 141.0, -9.2 to 130.9)),
)

/** The big island [lat], [lng] is on, or null for a small island, open sea or a point outside Indonesia's islands. */
fun islandOf(lat: Double, lng: Double): Island? =
    OUTLINES.firstOrNull { inside(lat, lng, it.points) }?.island

/** Ray casting: how many outline edges a ray towards +lng crosses; odd = inside. */
private fun inside(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
    var crossings = 0
    for (i in polygon.indices) {
        val (aLat, aLng) = polygon[i]
        val (bLat, bLng) = polygon[(i + 1) % polygon.size]
        if ((aLat > lat) != (bLat > lat)) {
            val crossingLng = aLng + (lat - aLat) / (bLat - aLat) * (bLng - aLng)
            if (lng < crossingLng) crossings++
        }
    }
    return crossings % 2 == 1
}
