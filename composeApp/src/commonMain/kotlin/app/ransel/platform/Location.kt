package app.ransel.platform

import androidx.compose.runtime.Composable

/** One location fix, or why there is none (`ErrorCode.LOCATION_DENIED`, `LOCATION_TIMEOUT`). */
sealed interface LocationFix {
    data class Found(val lat: Double, val lng: Double) : LocationFix
    data class Failed(val code: String) : LocationFix
}

/**
 * "Gunakan lokasi saat ini" (TRD "Location"): [request] asks for the permission only when it is called, then takes a
 * single, coarse fix within 10 s; never a background location. The fix is not stored anywhere. [openSettings] opens
 * the app's system settings after a refusal; null where the platform has none to open (the web).
 */
class LocationProvider(
    val request: suspend () -> LocationFix,
    val openSettings: (() -> Unit)?,
)

@Composable
expect fun rememberLocationProvider(): LocationProvider

/** How long a single fix may take. */
const val LOCATION_TIMEOUT_MILLIS = 10_000L
