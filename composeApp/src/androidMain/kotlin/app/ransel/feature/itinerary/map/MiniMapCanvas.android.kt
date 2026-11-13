package app.ransel.feature.itinerary.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

actual val miniMapSupported: Boolean = true

private const val PIN_DP = 28f
private const val PIN_SELECTED_DP = 36f
private const val SINGLE_PIN_ZOOM = 15f
private const val FIT_PADDING_DP = 32
private const val SELECT_ANIMATION_MILLIS = 200

/** No pan, zoom, rotate, tilt, toolbar or compass: the strip only takes taps on pins, so it never holds back the list's scroll. */
private val StripUiSettings = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = false,
    scrollGesturesEnabledDuringRotateOrZoom = false,
    tiltGesturesEnabled = false,
    zoomControlsEnabled = false,
    zoomGesturesEnabled = false,
)

/**
 * Google Maps SDK through maps-compose, without a Map ID: classic markers and JSON styling keep the native map in the
 * free "Maps SDK" SKU (Advanced Markers and cloud styling would need a Map ID and be billed as Dynamic Maps).
 */
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
) {
    val density = LocalDensity.current
    val cameraPositionState = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    val properties = remember(dark) { MapProperties(mapStyleOptions = MapStyleOptions(MiniMapStyle.of(dark))) }
    val fitPaddingPx = with(density) { FIT_PADDING_DP.dp.roundToPx() }

    // fit every pin (one pin: a close zoom) once the base map is there, and again when the day's pins change
    LaunchedEffect(loaded, pins) {
        if (!loaded || pins.isEmpty()) return@LaunchedEffect
        runCatching {
            val update = if (pins.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].lat, pins[0].lng), SINGLE_PIN_ZOOM)
            } else {
                val bounds = LatLngBounds.builder().apply { pins.forEach { include(LatLng(it.lat, it.lng)) } }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, fitPaddingPx)
            }
            cameraPositionState.move(update)
        }
    }
    // a row was picked in the list: the map moves to its pin
    LaunchedEffect(loaded, selectedId) {
        val pin = pins.firstOrNull { it.itemId == selectedId } ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(pin.lat, pin.lng)), SELECT_ANIMATION_MILLIS)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = description,
        properties = properties,
        uiSettings = StripUiSettings,
        // the dark map is our own JSON style (spec), not the SDK's system color scheme
        mapColorScheme = null,
        onMapLoaded = {
            loaded = true
            onLoaded()
        },
    ) {
        pins.forEach { pin ->
            val selected = pin.itemId == selectedId
            // bitmaps are made here, inside the map's content, where the Maps SDK is ready (not before)
            val icon = remember(pin.number, pin.color, pin.labelColor, selected, ringColor, density.density) { pinIcon(pin, selected, ringColor, density) }
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(pin.lat, pin.lng)),
                icon = icon,
                anchor = Offset(0.5f, 0.5f),
                zIndex = if (selected) 1f else 0f,
                contentDescription = null,
                onClick = {
                    onPinClick(pin.itemId)
                    // true = handled: no info window, no default camera move
                    true
                },
            )
        }
    }
}

/** A round numbered pin: category color, white ring, the number on top; the selected one is bigger with a green ring. */
private fun pinIcon(pin: MapPin, selected: Boolean, ringColor: Int, density: Density): BitmapDescriptor {
    val d = density.density
    val size = ((if (selected) PIN_SELECTED_DP else PIN_DP) * d).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (selected) {
        // halo (the ring color at 22 %) and the outer ring in the theme's primary
        paint.color = (ringColor and 0x00FFFFFF) or (0x38 shl 24)
        canvas.drawCircle(center, center, center, paint)
        paint.color = ringColor
        canvas.drawCircle(center, center, center - 4 * d, paint)
    }
    val ring = 2 * d
    val radius = if (selected) center - 7 * d else center - 1 * d
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, radius, paint)
    paint.color = pin.color
    canvas.drawCircle(center, center, radius - ring, paint)

    paint.color = pin.labelColor
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 13 * d
    val baseline = center - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(pin.number.toString(), center, baseline, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
