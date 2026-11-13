package app.ransel.feature.itinerary.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// No map on this platform yet: the section is not shown (miniMapSupported = false).
actual val miniMapSupported: Boolean = false

@Composable
actual fun MiniMapCanvas(
    pins: List<MapPin>,
    selectedId: String?,
    dark: Boolean,
    ringColor: Int,
    description: String,
    onPinClick: (itemId: String) -> Unit,
    onLoaded: () -> Unit,
    modifier: Modifier,
) = Unit
