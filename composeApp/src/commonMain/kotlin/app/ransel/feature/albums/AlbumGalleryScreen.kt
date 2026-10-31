package app.ransel.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ransel.content.stringResource
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.AlbumItemDto
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/** Design "AlbumGaleri": a public Drive folder as a grid; a photo opens [AlbumViewer]. */
@Composable
fun AlbumGalleryScreen(
    source: AlbumSource,
    title: String,
    addedByName: String,
    url: String,
    onBack: () -> Unit,
    viewModel: AlbumGalleryViewModel = koinViewModel(key = "album-${source.albumId}") { parametersOf(source) },
) {
    val state by viewModel.state.collectAsState()
    var viewing by remember { mutableStateOf<Int?>(null) }
    val uriHandler = LocalUriHandler.current
    val openDrive = { runCatching { uriHandler.openUri(url) }; Unit }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        AlbumGalleryContent(
            state = state,
            title = title,
            addedByName = addedByName,
            onBack = onBack,
            onOpenDrive = openDrive,
            onRetry = viewModel::load,
            onLoadMore = viewModel::loadMore,
            onOpenPhoto = { viewing = it },
        )
    }
    viewing?.let { start ->
        AlbumViewer(
            items = state.items,
            more = state.nextPageToken != null,
            startIndex = start,
            onLoadMore = viewModel::loadMore,
            onOpenDrive = openDrive,
            onClose = { viewing = null },
        )
    }
}

@Composable
fun AlbumGalleryContent(
    state: AlbumGalleryUiState,
    title: String,
    addedByName: String,
    onBack: () -> Unit,
    onOpenDrive: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPhoto: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Column(Modifier.padding(start = 4.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(Res.string.album_by_drive, addedByName), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onOpenDrive), contentAlignment = Alignment.Center) {
                Symbol(Symbols.openInNew, contentDescription = stringResource(Res.string.album_open_drive))
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.failure != null -> GalleryProblem(state.failure, addedByName, onRetry, onOpenDrive)
            state.items.isEmpty() -> GalleryNote(Symbols.landscape, stringResource(Res.string.album_gallery_empty))
            else -> Box(Modifier.fillMaxSize()) {
                val grid = rememberLazyGridState()
                val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 12 } }
                LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) onLoadMore() }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        Box(
                            Modifier.aspectRatio(1f).background(scheme.surfaceVariant)
                                .clickable(role = Role.Image, onClickLabel = stringResource(Res.string.album_photo, index + 1)) { onOpenPhoto(index) },
                        ) {
                            AlbumPhoto(item, thumbnail = true, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                if (state.loadingMore) LoadingMorePill(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
            }
        }
    }
}

@Composable
private fun LoadingMorePill(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(scheme.inverseSurface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = scheme.inverseOnSurface, strokeWidth = 2.dp)
        Text(stringResource(Res.string.album_loading_more), style = MaterialTheme.typography.labelLarge, color = scheme.inverseOnSurface)
    }
}

/** Design "AlbumGaleriError": a folder made private or gone, or Drive out of reach for a moment. */
@Composable
private fun GalleryProblem(code: String, addedByName: String, onRetry: () -> Unit, onOpenDrive: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val gone = code == ErrorCode.ALBUM_NOT_PUBLIC || code == ErrorCode.ALBUM_NOT_FOUND
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(72.dp).clip(IrisShape.xl).background(if (gone) scheme.errorContainer else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(if (gone) Symbols.lock else Symbols.cloudOff, size = 36.dp, filled = gone, tint = if (gone) scheme.onErrorContainer else scheme.onSurfaceVariant)
        }
        Text(
            stringResource(if (gone) Res.string.album_gallery_private_title else Res.string.album_gallery_unavailable_title),
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        Text(
            if (gone) stringResource(Res.string.album_gallery_private_body, addedByName) else stringResource(Res.string.album_gallery_unavailable_body),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(Res.string.album_retry)) }
            OutlinedButton(onClick = onOpenDrive, modifier = Modifier.heightIn(min = 44.dp)) {
                Symbol(Symbols.openInNew, size = 18.dp)
                Text(stringResource(Res.string.album_open_drive), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (!gone && code != ErrorCode.ALBUM_UNAVAILABLE) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GalleryNote(symbol: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Symbol(symbol, tint = scheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

/** One Drive photo, loaded straight from Google: the grid size, or the large one for the viewer. */
@Composable
fun AlbumPhoto(item: AlbumItemDto, thumbnail: Boolean, modifier: Modifier = Modifier) {
    AsyncImage(
        model = if (thumbnail) item.thumbnailUrl else item.imageUrl,
        contentDescription = if (thumbnail) null else item.name,
        contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * Design "AlbumViewer": one photo at a time on black; swipe or the arrows for the next, pinch to zoom. Nearing the end
 * of what is loaded asks for the next page.
 */
@Composable
fun AlbumViewer(
    items: List<AlbumItemDto>,
    more: Boolean,
    startIndex: Int,
    onLoadMore: () -> Unit,
    onOpenDrive: (() -> Unit)?,
    onClose: () -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage, items.size) { if (pager.currentPage >= items.size - 5) onLoadMore() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, modifier = Modifier.fillMaxSize(), key = { items[it].id }) { page ->
                ZoomablePhoto(items[page])
            }
            val white = Color.White
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = 0.45f)).heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                    Symbol(Symbols.close, tint = white, contentDescription = stringResource(Res.string.album_viewer_close))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        stringResource(if (more) Res.string.album_viewer_position_more else Res.string.album_viewer_position, pager.currentPage + 1, items.size),
                        style = MaterialTheme.typography.titleSmall, color = white,
                    )
                    Text(items[pager.currentPage].name, style = MaterialTheme.typography.bodySmall, color = white.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onOpenDrive != null) {
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onOpenDrive), contentAlignment = Alignment.Center) {
                        Symbol(Symbols.openInNew, tint = white, contentDescription = stringResource(Res.string.album_open_drive))
                    }
                }
            }
            // arrows for a mouse or a keyboard: swiping is the phone's way
            if (pager.currentPage > 0) {
                ViewerArrow(Symbols.chevronLeft, stringResource(Res.string.album_viewer_previous), Modifier.align(Alignment.CenterStart)) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }
            }
            if (pager.currentPage < items.lastIndex) {
                ViewerArrow(Symbols.chevronRight, stringResource(Res.string.album_viewer_next), Modifier.align(Alignment.CenterEnd)) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
    }
}

@Composable
private fun ViewerArrow(symbol: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(8.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(symbol, tint = Color.White, contentDescription = label)
    }
}

/** Pinch to zoom (1–4×) and pan while zoomed; back to fit when the page changes. */
@Composable
private fun ZoomablePhoto(item: AlbumItemDto) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        if (scale == 1f) {
            offsetX = 0f; offsetY = 0f
        } else {
            offsetX += pan.x; offsetY += pan.y
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AlbumPhoto(
            item,
            thumbnail = false,
            modifier = Modifier.fillMaxSize()
                // the pager keeps the swipe while the photo is not zoomed in
                .transformable(transform, canPan = { scale > 1f })
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
        )
    }
}

@Preview
@Composable
private fun AlbumGalleryProblemPreview() = IrisPreview {
    AlbumGalleryContent(
        state = AlbumGalleryUiState(loading = false, failure = ErrorCode.ALBUM_NOT_PUBLIC),
        title = "Foto Bali", addedByName = "Septian",
        onBack = {}, onOpenDrive = {}, onRetry = {}, onLoadMore = {}, onOpenPhoto = {},
    )
}

@Preview
@Composable
private fun AlbumGalleryUnavailablePreview() = IrisPreview {
    AlbumGalleryContent(
        state = AlbumGalleryUiState(loading = false, failure = ErrorCode.ALBUM_UNAVAILABLE),
        title = "Foto Bali", addedByName = "Septian",
        onBack = {}, onOpenDrive = {}, onRetry = {}, onLoadMore = {}, onOpenPhoto = {},
    )
}

@Preview
@Composable
private fun AlbumGalleryEmptyPreview() = IrisPreview {
    AlbumGalleryContent(
        state = AlbumGalleryUiState(loading = false),
        title = "Foto Bali", addedByName = "Septian",
        onBack = {}, onOpenDrive = {}, onRetry = {}, onLoadMore = {}, onOpenPhoto = {},
    )
}
