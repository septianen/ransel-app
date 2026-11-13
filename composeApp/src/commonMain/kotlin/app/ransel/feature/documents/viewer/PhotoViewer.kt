package app.ransel.feature.documents.viewer

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.stringResource
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.action_close
import ransel.composeapp.generated.resources.album_viewer_next
import ransel.composeapp.generated.resources.album_viewer_previous
import ransel.composeapp.generated.resources.photo_viewer_decimal
import ransel.composeapp.generated.resources.photo_viewer_description
import ransel.composeapp.generated.resources.photo_viewer_failed
import ransel.composeapp.generated.resources.photo_viewer_hint
import ransel.composeapp.generated.resources.photo_viewer_not_saved
import ransel.composeapp.generated.resources.photo_viewer_not_saved_body
import ransel.composeapp.generated.resources.photo_viewer_position
import ransel.composeapp.generated.resources.photo_viewer_retry
import ransel.composeapp.generated.resources.photo_viewer_zoom_in
import ransel.composeapp.generated.resources.photo_viewer_zoom_out
import kotlin.math.abs

/**
 * One photo of the viewer. [heading] is the document's name (top bar), [title] and [subtitle] the bottom bar ("Rp845.000 ·
 * 21 Nov" / "Struk · ditambahkan Septian" for a receipt, the name and the adder for any other photo).
 */
data class ViewerPhoto(val id: String, val heading: String, val title: String, val subtitle: String? = null)

/** What a photo loads as: the bytes (kept on the phone once opened), no signal and nothing kept, or a failure. */
sealed interface PhotoLoad {
    class Ready(val bytes: ByteArray) : PhotoLoad
    data object Offline : PhotoLoad
    data object Failed : PhotoLoad
}

fun interface PhotoLoader {
    suspend fun load(photo: ViewerPhoto): PhotoLoad
}

/**
 * Full-screen photo viewer (spec "Viewer foto layar penuh"): always dark; pinch 1×–5×, double tap 1× / 2.5×, pan while
 * zoomed, swipe to the neighbours (the pager is locked while zoomed), one tap hides the bars, drag down at 1× closes.
 * Neighbours are loaded ahead when [prefetch] (the loader keeps them on the phone, so they open without a signal).
 * [action] is the optional outline button of the bottom bar ("Buka pengeluaran") for a photo.
 */
@Composable
fun PhotoViewer(
    photos: List<ViewerPhoto>,
    startIndex: Int,
    loader: PhotoLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    prefetch: Boolean = true,
    showHint: Boolean = false,
    onHintShown: () -> Unit = {},
    actionLabel: String? = null,
    action: ((ViewerPhoto) -> (() -> Unit)?)? = null,
) {
    if (photos.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var barsShown by remember { mutableStateOf(true) }
    var badge by remember { mutableStateOf(false) }
    var dim by remember { mutableFloatStateOf(0f) }
    val zoomed = zoom > 1.01f
    LaunchedEffect(pager.settledPage) { zoom = 1f }
    LaunchedEffect(Unit) { if (showHint) onHintShown() }
    // a badge for about a second after the zoom changed
    LaunchedEffect(zoom) {
        if (zoom > 1.01f) {
            badge = true
            delay(1000)
            badge = false
        } else badge = false
    }
    if (prefetch) LaunchedEffect(pager.settledPage) {
        listOf(pager.settledPage - 1, pager.settledPage + 1).forEach { i -> photos.getOrNull(i)?.let { loader.load(it) } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dim))) {
            HorizontalPager(pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { photos[it].id }) { page ->
                PhotoPage(
                    photo = photos[page],
                    position = page + 1,
                    count = photos.size,
                    loader = loader,
                    active = page == pager.settledPage,
                    onZoom = { zoom = it },
                    onTap = { barsShown = !barsShown },
                    onDrag = { dim = it },
                    onDismiss = onClose,
                    onNext = { if (page < photos.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) } },
                    onPrevious = { if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } },
                )
            }
            val current = photos[pager.currentPage]
            if (barsShown && !zoomed) {
                TopBar(pager.currentPage + 1, photos.size, current.heading, onClose, Modifier.align(Alignment.TopCenter))
                BottomBar(current, actionLabel, action?.invoke(current), showHint, Modifier.align(Alignment.BottomCenter))
            }
            if (badge) {
                Text(
                    Zoom.label(zoom, decimal = stringResource(Res.string.photo_viewer_decimal).first()),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TopBar(position: Int, count: Int, heading: String, onClose: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding().heightIn(min = 72.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
            Symbol(Symbols.close, tint = Color.White, contentDescription = stringResource(Res.string.action_close))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(stringResource(Res.string.photo_viewer_position, position, count), style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(heading, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BottomBar(photo: ViewerPhoto, actionLabel: String?, onAction: (() -> Unit)?, hint: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 32.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(photo.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                photo.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
        if (hint) {
            Text(
                stringResource(Res.string.photo_viewer_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PhotoPage(
    photo: ViewerPhoto,
    position: Int,
    count: Int,
    loader: PhotoLoader,
    active: Boolean,
    onZoom: (Float) -> Unit,
    onTap: () -> Unit,
    onDrag: (dim: Float) -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(photo.id, attempt) { mutableStateOf<PageState>(PageState.Loading) }
    LaunchedEffect(photo.id, attempt) {
        state = when (val result = loader.load(photo)) {
            is PhotoLoad.Ready -> withContext(Dispatchers.Default) { runCatching { result.bytes.decodeToImageBitmap() }.getOrNull() }
                ?.let { PageState.Shown(it) } ?: PageState.Problem(offline = false)
            PhotoLoad.Offline -> PageState.Problem(offline = true)
            PhotoLoad.Failed -> PageState.Problem(offline = false)
        }
    }
    when (val s = state) {
        PageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(44.dp), color = MaterialTheme.colorScheme.primary)
        }
        is PageState.Problem -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Symbol(Symbols.cloudOff, size = 44.dp, tint = Color.White.copy(alpha = 0.8f))
            Text(
                stringResource(if (s.offline) Res.string.photo_viewer_not_saved else Res.string.photo_viewer_failed),
                modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.photo_viewer_not_saved_body),
                modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
            )
            TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp)) {
                Text(stringResource(Res.string.photo_viewer_retry), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        is PageState.Shown -> ZoomablePhoto(s.image, photo, position, count, active, onZoom, onTap, onDrag, onDismiss, onNext, onPrevious)
    }
}

private sealed interface PageState {
    data object Loading : PageState
    class Shown(val image: ImageBitmap) : PageState
    class Problem(val offline: Boolean) : PageState
}

@Composable
private fun ZoomablePhoto(
    image: ImageBitmap,
    photo: ViewerPhoto,
    position: Int,
    count: Int,
    active: Boolean,
    onZoom: (Float) -> Unit,
    onTap: () -> Unit,
    onDrag: (dim: Float) -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(scale, active) { if (active) onZoom(scale) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero; dragY = 0f } }
    val description = stringResource(Res.string.photo_viewer_description, position, count, photo.heading)
    val zoomInLabel = stringResource(Res.string.photo_viewer_zoom_in)
    val zoomOutLabel = stringResource(Res.string.photo_viewer_zoom_out)
    val nextLabel = stringResource(Res.string.album_viewer_next)
    val previousLabel = stringResource(Res.string.album_viewer_previous)
    val closeLabel = stringResource(Res.string.action_close)
    var box by remember { mutableStateOf(Size.Zero) }
    val fitted = Zoom.fitted(box, Size(image.width.toFloat(), image.height.toFloat()))

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val fromScale = scale
        val fromOffset = offset
        scope.launch {
            animate(0f, 1f) { t, _ ->
                scale = fromScale + (targetScale - fromScale) * t
                offset = fromOffset + (targetOffset - fromOffset) * t
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(zoomInLabel) { animateTo(DOUBLE_TAP_ZOOM, Offset.Zero); true },
                    CustomAccessibilityAction(zoomOutLabel) { animateTo(MIN_ZOOM, Offset.Zero); true },
                    CustomAccessibilityAction(nextLabel) { onNext(); true },
                    CustomAccessibilityAction(previousLabel) { onPrevious(); true },
                    CustomAccessibilityAction(closeLabel) { onDismiss(); true },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val (s, o) = Zoom.doubleTap(scale, tap - centre, box, fitted)
                        animateTo(s, o)
                    },
                )
            }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var dismissing = false
                    do {
                        val event = awaitPointerEvent()
                        val down = event.changes.count { it.pressed }
                        if (down >= 2 || scale > 1.01f) {
                            // pinch and pan: only here the viewer takes the touches, so a plain swipe still reaches the pager
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val (s, o) = Zoom.pinch(scale, offset, event.calculateZoom(), event.calculateCentroid() - centre, event.calculatePan(), box, fitted)
                            scale = s
                            offset = o
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (down == 1) {
                            val change = event.changes.first()
                            total += change.positionChange()
                            if (!dismissing && abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.5f && total.y > 0) dismissing = true
                            if (dismissing) {
                                dragY = (dragY + change.positionChange().y).coerceAtLeast(0f)
                                onDrag((dragY / (size.height * 0.5f)).coerceIn(0f, 1f) * 0.8f)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dismissing) {
                        if (dragY > size.height * 0.18f) onDismiss() else {
                            val from = dragY
                            scope.launch { animate(from, 0f) { v, _ -> dragY = v; onDrag((v / (size.height * 0.5f)).coerceIn(0f, 1f) * 0.8f) } }
                        }
                    }
                    // released under 1×: back to 1×
                    if (scale < 1f) animateTo(1f, Offset.Zero)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dragY
                },
        )
    }
}
