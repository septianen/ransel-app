package app.ransel.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.shared.dto.TripAlbumDto
import app.ransel.shared.model.AlbumProvider
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*

/**
 * Design "WebTamuAlbum": the albums on the guest page. A Drive album shows six photos (the last "+N") that open the
 * viewer; other links are cards that open outside. Guests see; they never add.
 */
@Composable
fun GuestAlbumsSection(code: String, albums: List<TripAlbumDto>, modifier: Modifier = Modifier) {
    if (albums.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.albums_title), style = MaterialTheme.typography.titleLarge)
        albums.forEach { album ->
            if (album.provider == AlbumProvider.DRIVE_FOLDER) GuestDriveAlbum(code, album)
            else OutsideAlbumRow(album) { runCatching { uriHandler.openUri(album.url) } }
        }
    }
}

@Composable
private fun GuestDriveAlbum(code: String, album: TripAlbumDto) {
    val gallery: AlbumGalleryViewModel = koinViewModel(key = "guest-album-${album.id}") { parametersOf(AlbumSource(album.id, guestCode = code)) }
    val state by gallery.state.collectAsState()
    var viewing by remember { mutableStateOf<Int?>(null) }
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(album.displayTitle(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.album_kind_drive), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        when {
            state.failure != null -> Text(
                stringResource(Res.string.album_gallery_unavailable_title),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.clip(IrisShape.md).clickable(role = Role.Button, onClick = gallery::load).padding(vertical = 8.dp),
            )
            !state.loading && state.items.isEmpty() -> Text(stringResource(Res.string.album_gallery_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            else -> Column(Modifier.fillMaxWidth().clip(IrisShape.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (0 until 3).forEach { col ->
                            val index = row * 3 + col
                            val item = state.items.getOrNull(index)
                            val rest = state.items.size - 6
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).background(scheme.surfaceVariant)
                                    .then(if (item != null) Modifier.clickable(role = Role.Image, onClickLabel = stringResource(Res.string.album_photo, index + 1)) { viewing = index } else Modifier),
                            ) {
                                if (item != null) AlbumPhoto(item, thumbnail = true, modifier = Modifier.fillMaxSize())
                                if (index == 5 && (rest > 0 || state.nextPageToken != null)) {
                                    Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                        Text(
                                            stringResource(if (state.nextPageToken != null) Res.string.album_more_photos_plus else Res.string.album_more_photos, rest.coerceAtLeast(0)),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = IrisTheme.accents.onAccent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { start ->
        AlbumViewer(
            items = state.items,
            more = state.nextPageToken != null,
            startIndex = start,
            onLoadMore = gallery::loadMore,
            onOpenDrive = { runCatching { uriHandler.openUri(album.url) } },
            onClose = { viewing = null },
        )
    }
}

@Composable
private fun OutsideAlbumRow(album: TripAlbumDto, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (tile, symbol) = providerTile(album.provider)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(IrisShape.lg).background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(tile), contentAlignment = Alignment.Center) {
            Symbol(symbol, filled = true, tint = IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(album.displayTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (album.provider == AlbumProvider.GOOGLE_PHOTOS) stringResource(Res.string.album_kind_photos_hint) else hostOf(album.url),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        Symbol(Symbols.openInNew, size = 22.dp, tint = scheme.onSurfaceVariant)
    }
}
