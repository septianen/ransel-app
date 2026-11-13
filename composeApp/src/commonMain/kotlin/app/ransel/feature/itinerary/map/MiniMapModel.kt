package app.ransel.feature.itinerary.map

import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.model.Category

/**
 * A numbered pin of the day's mini map (spec "Mini peta di Itinerary"). [number] counts only the activities that have a
 * place on the map, in timeline order, so numbers never skip. Colors are ARGB ints so the platform map code needs no
 * Compose types: [color] is the category accent, [labelColor] the number on it.
 */
data class MapPin(
    val itemId: String,
    val number: Int,
    val lat: Double,
    val lng: Double,
    val category: Category,
    val color: Int,
    val labelColor: Int,
)

/** An activity is on the map when it has both coordinates (the contract keeps them as a pair). */
fun ItineraryItemDto.hasMapPoint(): Boolean = lat != null && lng != null

/** The pins of a day: [items] in timeline order, numbered among the ones that have a point. */
fun mapPinsOf(items: List<ItineraryItemDto>, colorsOf: (Category) -> Pair<Int, Int>): List<MapPin> =
    items.filter { it.hasMapPoint() }.mapIndexed { index, item ->
        val (color, label) = colorsOf(item.category)
        MapPin(item.id, index + 1, item.lat!!, item.lng!!, item.category, color, label)
    }

/** How many activities of the day have no place on the map yet ("n aktivitas belum punya lokasi"). */
fun unlocatedCount(items: List<ItineraryItemDto>): Int = items.count { !it.hasMapPoint() }

/** The first activity of the day without a point: "Isi lokasi" opens its form with the place picker. */
fun firstUnlocated(items: List<ItineraryItemDto>): ItineraryItemDto? = items.firstOrNull { !it.hasMapPoint() }

/**
 * The pin "Buka di Google Maps" opens: the highlighted one, else the first of the day (owner and Benn, 12 Nov 2026).
 * A place, not a route: navigation is a PRD non-goal.
 */
fun pinToOpen(pins: List<MapPin>, selectedId: String?): MapPin? = pins.firstOrNull { it.itemId == selectedId } ?: pins.firstOrNull()

/** Google Maps URL for one point (https://developers.google.com/maps/documentation/urls/get-started "Search"). */
fun googleMapsUrl(pin: MapPin): String = "https://www.google.com/maps/search/?api=1&query=${pin.lat},${pin.lng}"

/** What the map shows while it loads before it gives up and shows the "failed" card (spec state 5 to 6). */
const val MINI_MAP_LOAD_TIMEOUT_MILLIS = 8_000L

/** Preference key (per device, not per trip) for the folded bar. */
const val MINI_MAP_COLLAPSED_KEY = "mini_map_collapsed"
