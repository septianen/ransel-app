package app.ransel.feature.interview

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.platform.LocationFix
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.cityDisplayName
import app.ransel.shared.dto.PlaceKind
import app.ransel.shared.dto.TripOriginDto

/** What "Gunakan lokasi saat ini" found: the city, a refused permission, or a failure (no fix, no answer from the places service). */
sealed interface OriginFound {
    data class City(val origin: TripOriginDto) : OriginFound
    data object Denied : OriginFound
    data object Failed : OriginFound
}

/**
 * One coarse fix turned into a city by /places/reverse, for W1 and for "Kota asal" of Edit trip. Only the city's name and centre
 * are kept, never the fix itself (spec). The widest level of the answer is the city or regency; the ones before it are districts.
 */
suspend fun findOriginCity(api: RanselApi, request: suspend () -> LocationFix): OriginFound =
    when (val fix = request()) {
        is LocationFix.Failed -> if (fix.code == ErrorCode.LOCATION_DENIED) OriginFound.Denied else OriginFound.Failed
        is LocationFix.Found -> when (val result = api.reverseGeocode(fix.lat, fix.lng)) {
            is ApiResult.Success -> {
                val area = result.value.areas.lastOrNull { it.kind == PlaceKind.CITY || it.kind == PlaceKind.REGENCY }
                    ?: result.value.areas.lastOrNull()
                if (area == null) {
                    OriginFound.Failed
                } else {
                    val place = area.placeId?.let { (api.place(it, null) as? ApiResult.Success)?.value }
                    OriginFound.City(TripOriginDto(cityDisplayName(area.name), place?.lat, place?.lng, area.placeId, place?.countryCode))
                }
            }
            is ApiResult.Failure -> OriginFound.Failed
        }
    }
