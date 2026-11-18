package app.ransel.feature.itinerary.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Whether this build can show the day's map. Android does (Google Maps SDK). iOS has no Xcode project in the repo yet,
 * and the web panel comes later (spike "Spike Google Maps untuk mini peta"), so the section stays hidden there.
 */
expect val miniMapSupported: Boolean

/**
 * The map itself: the numbered [pins] fitted in view, the one with [selectedId] highlighted. On the strip, one finger
 * scrolls the page and two fingers pan/zoom the map; full screen allows one-finger drag. Taps on a pin go to
 * [onPinClick]. [onLoaded] says the base map is on screen; if it never comes, the caller shows the "failed" card.
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
    /** The straight lines between pins (Android draws them; empty = none) and their colours. */
    segments: List<MapSegment> = emptyList(),
    lineStyle: MapLineStyle? = null,
    /** Pixel size of the map view, used to place the camera before the SDK is created. */
    viewportWidthPx: Int = 0,
    viewportHeightPx: Int = 0,
    gestures: MiniMapGestureMode = MiniMapGestureMode.TWO_FINGER,
    /** When this value changes to a new positive tick, the camera returns to the fit of the stops. */
    recenterTick: Int = 0,
    /** Full-screen padding and one-finger drag; the strip uses the tight safe-zone padding. */
    expanded: Boolean = false,
    /** Filled with 0..1 after the first tiles and the fit camera, so the caller can fade the map in. */
    onRevealProgress: (Float) -> Unit = {},
)
