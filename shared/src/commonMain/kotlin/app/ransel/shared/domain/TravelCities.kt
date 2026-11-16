package app.ransel.shared.domain

import app.ransel.shared.dto.TripOriginDto

/*
 * Which cities have a train station, rent motorbikes, or have public transport on Google Maps (plan "Wawancara Buat trip":
 * "Semua daftar kota ... dikelola di Karung"). Karung has no such lists yet, so these bundled ones stand in: the interview
 * must work offline for guests anyway, and Karung's lists will replace them through [CityLists]. Names are matched without
 * case, accents and punctuation, as whole words ("Kota Bandung" and "Kabupaten Bandung Barat" both contain "bandung").
 */

/** The three lists, each a set of normalized city or area names. */
data class CityLists(
    val stationCities: Set<String>,
    val motorcycleRentalCities: Set<String>,
    val publicTransportCities: Set<String>,
)

private fun names(vararg items: String): Set<String> = items.map(::normalizeSearch).toSet()

/** Cities with a passenger train station (Java and the main Sumatra lines). */
private val STATION_CITIES = names(
    "Jakarta", "Bogor", "Depok", "Bekasi", "Tangerang", "Cikarang", "Bandung", "Cimahi", "Subang", "Purwakarta", "Cirebon",
    "Sukabumi", "Cianjur", "Garut", "Tasikmalaya", "Banjar", "Purwokerto", "Cilacap", "Kebumen", "Tegal", "Pekalongan", "Semarang",
    "Solo", "Surakarta", "Yogyakarta", "Klaten", "Madiun", "Kediri", "Blitar", "Malang", "Surabaya", "Sidoarjo", "Mojokerto",
    "Jember", "Probolinggo", "Banyuwangi", "Medan", "Binjai", "Palembang", "Lubuklinggau", "Bandar Lampung", "Padang",
)

/** Where motorbikes are rented to tourists: Bali, Yogyakarta, Lombok (plan "Perjalanan antar tempat", initial list). */
private val MOTORCYCLE_RENTAL_CITIES = names(
    "Bali", "Denpasar", "Badung", "Gianyar", "Ubud", "Kuta", "Seminyak", "Canggu", "Sanur", "Uluwatu", "Nusa Dua", "Nusa Penida",
    "Yogyakarta", "Jogja", "Sleman", "Bantul", "Lombok", "Mataram", "Senggigi", "Kuta Lombok",
)

/** Cities whose public transport shows up in Google Maps (Jakarta: MRT, KRL, TransJakarta). Jogja and Bali are not confirmed. */
private val PUBLIC_TRANSPORT_CITIES = names("Jakarta", "Daerah Khusus Ibukota Jakarta")

val BUNDLED_CITY_LISTS = CityLists(STATION_CITIES, MOTORCYCLE_RENTAL_CITIES, PUBLIC_TRANSPORT_CITIES)

/** True when [name] ("Kota Bandung", "Bandung, Jawa Barat") contains one of [list] as whole words. */
fun cityIn(list: Set<String>, name: String?): Boolean {
    if (name.isNullOrBlank()) return false
    val padded = " " + normalizeSearch(name.substringBefore(',')) + " "
    return list.any { padded.contains(" $it ") }
}

/** What the lists say about a trip from [origin] to [destinationName]. */
fun interviewCityFacts(origin: TripOriginDto?, destinationName: String?, lists: CityLists = BUNDLED_CITY_LISTS) = InterviewCityFacts(
    trainBetweenCities = cityIn(lists.stationCities, origin?.name) && cityIn(lists.stationCities, destinationName),
    motorcycleRentalCity = cityIn(lists.motorcycleRentalCities, destinationName),
    publicTransportCity = cityIn(lists.publicTransportCities, destinationName),
)
