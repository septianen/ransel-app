package app.ransel.feature.itinerary.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Whether this build can show the day's map. Android does (Google Maps SDK). iOS has no Xcode project in the repo yet,
 * and the web panel comes later (spike "Spike Google Maps untuk mini peta"), so the section stays hidden there.
 */
expect val miniMapSupported: Boolean

/**
 * The map itself: the numbered [pins] fitted in view, the one with [selectedId] highlighted, no pan or zoom (the strip
 * must not hold back the list's scroll; "Buka di Google Maps" is for exploring). Taps on a pin go to [onPinClick].
 * [onLoaded] says the base map is on screen; if it never comes, the caller shows the "failed" card.
 * [description] is read by screen readers as one element ("Peta hari ini, 4 lokasi"). [ringColor] (ARGB, the theme's
 * primary) is the ring and halo of the selected pin.
 */
@Composable
expect fun MiniMapCanvas(
    pins: List<MapPin>,
    selectedId: String?,
    dark: Boolean,
    ringColor: Int,
    description: String,
    onPinClick: (itemId: String) -> Unit,
    onLoaded: () -> Unit,
    modifier: Modifier = Modifier,
)
