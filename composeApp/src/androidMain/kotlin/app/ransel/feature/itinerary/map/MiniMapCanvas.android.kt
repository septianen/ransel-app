package app.ransel.feature.itinerary.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.ransel.ui.motion.IrisMotion
import app.ransel.ui.motion.rememberReduceMotion
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import androidx.compose.ui.graphics.Color
import com.google.maps.android.compose.Polyline
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.ln

actual val miniMapSupported: Boolean = true

private const val PIN_DP = 28f
private const val PIN_SELECTED_DP = 36f

/**
 * The safe zone of pins (spec "Mini peta", 15 Nov 2026), in dp from the edge of the 160 dp strip: left 40, top 32, right 56,
 * bottom 64. The chip "Buka di Google Maps" (bottom right), the collapse button (top right) and the Google logo
 * (bottom left) sit in the margins. The camera fit uses this padding plus [FIT_PADDING_DP] around the pins.
 */
private const val FIT_PADDING_DP = 16
private val STRIP_CONTENT_PADDING = PaddingValues(start = 24.dp, top = 16.dp, end = 40.dp, bottom = 48.dp)
private val EXPANDED_CONTENT_PADDING = PaddingValues(start = 16.dp, top = 72.dp, end = 16.dp, bottom = 88.dp)
private const val SELECT_ANIMATION_MILLIS = 200
private const val DARK_MAP_BACKGROUND = 0xFF1D1D1D.toInt()
private const val LIGHT_MAP_BACKGROUND = 0xFFEEF3EA.toInt()

/** The straight lines: 2.5 dp, dots 6 dp apart for walking; a time pill needs 72 dp of line on screen (spec). */
private const val LINE_WIDTH_DP = 2.5f
private const val LINE_GAP_DP = 6f
private const val LABEL_MIN_SEGMENT_DP = 72f

/** The Material Symbols font as compose resources packs it into the APK (the iris module). */
private const val SYMBOLS_FONT_ASSET = "composeResources/app.ransel.iris.generated.resources/font/material_symbols_rounded.ttf"

/** SDK gestures stay off on the strip (custom two-finger handling). Full screen turns pan/zoom on. */
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

private val ExpandedUiSettings = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = true,
    scrollGesturesEnabledDuringRotateOrZoom = true,
    tiltGesturesEnabled = false,
    zoomControlsEnabled = false,
    zoomGesturesEnabled = true,
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
    segments: List<MapSegment>,
    lineStyle: MapLineStyle?,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    gestures: MiniMapGestureMode,
    recenterTick: Int,
    expanded: Boolean,
    onRevealProgress: (Float) -> Unit,
    drawn: Boolean,
) {
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    // the Material Symbols font of the app, for the mode icon in a time pill (null if it cannot be read: the pill has no icon)
    val context = androidx.compose.ui.platform.LocalContext.current
    val symbolFont = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, SYMBOLS_FONT_ASSET) }.getOrNull()
    }
    val contentPadding = if (expanded) EXPANDED_CONTENT_PADDING else STRIP_CONTENT_PADDING
    val fitPad = with(density) { FIT_PADDING_DP.dp.roundToPx() }
    val padStart = with(density) { (if (expanded) 16.dp else 24.dp).roundToPx() } + fitPad
    val padTop = with(density) { (if (expanded) 72.dp else 16.dp).roundToPx() } + fitPad
    val padEnd = with(density) { (if (expanded) 16.dp else 40.dp).roundToPx() } + fitPad
    val padBottom = with(density) { (if (expanded) 88.dp else 48.dp).roundToPx() } + fitPad
    val points = remember(pins) { pins.map { GeoPoint(it.lat, it.lng) } }
    val fit = remember(points, viewportWidthPx, viewportHeightPx, padStart, padTop, padEnd, padBottom, density.density) {
        fitCamera(points, viewportWidthPx, viewportHeightPx, padStart, padTop, padEnd, padBottom, density.density)
    }
    val drag = remember(points) { dragBounds(points) }
    val fitZoom = fit?.zoom ?: MINI_MAP_SINGLE_FIT_ZOOM
    val minZoom = minZoomPreference(fitZoom)
    val cameraTargetBounds = remember(drag) { drag?.toLatLngBounds() }
    val initialPosition = remember(fit) {
        val target = fit ?: MiniMapFit(0.0, 0.0, MINI_MAP_SINGLE_FIT_ZOOM)
        CameraPosition(LatLng(target.centerLat, target.centerLng), target.zoom, 0f, 0f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = initialPosition
    }
    var loaded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val properties = remember(dark, minZoom, cameraTargetBounds) {
        MapProperties(
            mapStyleOptions = MapStyleOptions(MiniMapStyle.of(dark)),
            minZoomPreference = minZoom,
            maxZoomPreference = MINI_MAP_MAX_ZOOM,
            latLngBoundsForCameraTarget = cameraTargetBounds,
        )
    }
    val pinSignature = remember(pins) { pins.joinToString { "${it.itemId}:${it.lat}:${it.lng}" } }
    var lastFitted by remember { mutableStateOf<String?>(null) }
    val dayCameraMs = if (reduceMotion) 0 else IrisMotion.Medium2Ms

    // first composition already used [initialPosition]; later pin sets (a new day) animate to the new fit
    LaunchedEffect(loaded, pinSignature, fit) {
        val next = fit ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(next.centerLat, next.centerLng), next.zoom)
        if (lastFitted == null) {
            lastFitted = pinSignature
            return@LaunchedEffect
        }
        if (lastFitted != pinSignature) {
            lastFitted = pinSignature
            runCatching {
                if (dayCameraMs == 0) cameraPositionState.move(update)
                else cameraPositionState.animate(update, dayCameraMs)
            }
        }
    }
    LaunchedEffect(loaded, recenterTick, fit) {
        if (!loaded || recenterTick == 0) return@LaunchedEffect
        val next = fit ?: return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(next.centerLat, next.centerLng), next.zoom)
        runCatching {
            if (reduceMotion) cameraPositionState.move(update)
            else cameraPositionState.animate(update, IrisMotion.Medium2Ms)
        }
    }
    // a row was picked in the list: the map moves to its pin
    LaunchedEffect(loaded, selectedId) {
        val pin = pins.firstOrNull { it.itemId == selectedId } ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        runCatching {
            val ms = if (reduceMotion) 0 else SELECT_ANIMATION_MILLIS
            if (ms == 0) cameraPositionState.move(CameraUpdateFactory.newLatLng(LatLng(pin.lat, pin.lng)))
            else cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(pin.lat, pin.lng)), ms)
        }
    }
    LaunchedEffect(loaded) {
        if (loaded && !revealed) {
            revealed = true
            onRevealProgress(1f)
        }
    }

    val twoFingerOnly = gestures == MiniMapGestureMode.TWO_FINGER
    val viewRef = remember { mutableStateOf<CooperativeMapView?>(null) }
    val latestCamera = rememberUpdatedState(cameraPositionState)
    val latestFitZoom = rememberUpdatedState(fitZoom)
    val latestDrag = rememberUpdatedState(drag)
    LaunchedEffect(twoFingerOnly, drawn, viewRef.value) {
        viewRef.value?.twoFingerOnly = twoFingerOnly
        viewRef.value?.onTwoFingerDelta = { midX, midY, prevMidX, prevMidY, span, prevSpan ->
            applyTwoFingerCamera(latestCamera.value, latestFitZoom.value, latestDrag.value, midX, midY, prevMidX, prevMidY, span, prevSpan)
        }
        viewRef.value?.setDrawn(drawn)
    }
    val background = if (dark) DARK_MAP_BACKGROUND else LIGHT_MAP_BACKGROUND
    val optionsFactory = remember(initialPosition, background, dark) {
        {
            GoogleMapOptions()
                .camera(initialPosition)
                .backgroundColor(background)
                .mapColorScheme(if (dark) MapColorScheme.DARK else MapColorScheme.LIGHT)
        }
    }

    GoogleMap(
        modifier = modifier
            .graphicsLayer { alpha = if (drawn) 1f else 0f }
            .then(if (drawn) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }),
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        contentDescription = if (drawn) description else "",
        properties = properties,
        uiSettings = if (expanded) ExpandedUiSettings else StripUiSettings,
        mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        googleMapOptionsFactory = optionsFactory,
        mapViewFactory = { ctx, options ->
            CooperativeMapView(ctx, options).also {
                it.twoFingerOnly = twoFingerOnly
                it.setDrawn(drawn)
                viewRef.value = it
            }
        },
        onMapLoaded = {
            loaded = true
            onLoaded()
        },
    ) {
        // the straight lines under the pins, and the time pills on the ones long enough to hold them
        if (lineStyle != null) {
            // reading the camera makes the pills follow the zoom (a pill is hidden under 72 dp of line)
            val camera = cameraPositionState.position
            val projection = if (loaded) cameraPositionState.projection else null
            segments.forEach { segment ->
                val from = LatLng(segment.fromLat, segment.fromLng)
                val to = LatLng(segment.toLat, segment.toLng)
                Polyline(
                    points = listOf(from, to),
                    color = Color(lineStyle.line),
                    width = LINE_WIDTH_DP * density.density,
                    pattern = if (segment.walking) listOf(Dot(), Gap(LINE_GAP_DP * density.density)) else null,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 0f,
                )
                val label = segment.label
                val longEnough = projection?.let {
                    val a = it.toScreenLocation(from)
                    val b = it.toScreenLocation(to)
                    kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) >= LABEL_MIN_SEGMENT_DP * density.density
                } ?: false
                if (label != null && longEnough && camera.zoom > 0f) {
                    val icon = remember(label, segment.symbol, lineStyle, density.density, symbolFont) {
                        labelIcon(label, segment.symbol, symbolFont, lineStyle, density)
                    }
                    Marker(
                        state = rememberUpdatedMarkerState(position = LatLng((from.latitude + to.latitude) / 2, (from.longitude + to.longitude) / 2)),
                        icon = icon,
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = 0.5f,
                        contentDescription = null,
                        // a pill is not a control: nothing happens on a tap
                        onClick = { true },
                    )
                }
            }
        }
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

private fun GeoRect.toLatLngBounds(): LatLngBounds =
    LatLngBounds(LatLng(south, west), LatLng(north, east))

private fun applyTwoFingerCamera(
    camera: com.google.maps.android.compose.CameraPositionState,
    fitZoom: Float,
    drag: GeoRect?,
    midX: Float,
    midY: Float,
    prevMidX: Float,
    prevMidY: Float,
    span: Float,
    prevSpan: Float,
) {
    val projection = camera.projection ?: return
    val previous = projection.fromScreenLocation(Point(prevMidX.toInt(), prevMidY.toInt()))
    val current = projection.fromScreenLocation(Point(midX.toInt(), midY.toInt()))
    val target = camera.position.target
    var lat = target.latitude + (previous.latitude - current.latitude)
    var lng = target.longitude + (previous.longitude - current.longitude)
    if (drag != null) {
        val clamped = clampToRect(lat, lng, drag)
        lat = clamped.lat
        lng = clamped.lng
    }
    val zoomDelta = (ln(span.toDouble()) - ln(prevSpan.toDouble())) / LN2
    val zoom = clampZoom(camera.position.zoom + zoomDelta.toFloat(), fitZoom)
    camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
}

private val LN2 = ln(2.0)

/** The time pill at the middle of a line: surface fill, a thin outline-variant border, the time in on-surface (24 dp high). */
private fun labelIcon(text: String, symbol: String?, symbolFont: Typeface?, style: MapLineStyle, density: Density): BitmapDescriptor {
    val d = density.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 12 * d
    // 10 dp on each side, the 14 dp icon and 4 dp between it and the time
    val iconWidth = if (symbol != null && symbolFont != null) (14 + 4) * d else 0f
    val width = (paint.measureText(text) + iconWidth + 20 * d).toInt()
    val height = (24 * d).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = android.graphics.RectF(0.5f * d, 0.5f * d, width - 0.5f * d, height - 0.5f * d)
    val radius = height / 2f
    paint.style = Paint.Style.FILL
    paint.color = style.pillFill
    canvas.drawRoundRect(rect, radius, radius, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1 * d
    paint.color = style.pillBorder
    canvas.drawRoundRect(rect, radius, radius, paint)
    paint.style = Paint.Style.FILL
    paint.color = style.pillText
    paint.textAlign = Paint.Align.LEFT
    val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(text, 10 * d + iconWidth, baseline, paint)
    if (symbol != null && symbolFont != null) {
        // the glyph is square (14 dp) and centred on the line of the text
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
        glyph.typeface = symbolFont
        glyph.textSize = 14 * d
        glyph.color = style.pillText
        glyph.textAlign = Paint.Align.LEFT
        canvas.drawText(symbol, 10 * d, height / 2f - (glyph.descent() + glyph.ascent()) / 2f, glyph)
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
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
