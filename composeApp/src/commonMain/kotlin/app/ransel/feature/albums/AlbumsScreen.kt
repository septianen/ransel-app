package app.ransel.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.data.repository.TripRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.ALBUM_TITLE_MAX
import app.ransel.shared.domain.parseAlbumLink
import app.ransel.shared.dto.AlbumItemDto
import app.ransel.shared.dto.TripAlbumDto
import app.ransel.shared.model.AlbumProvider
import app.ransel.shared.model.Role as TripRole
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*
import kotlin.time.Instant

/**
 * Design "AlbumTrip": the trip's albums. A public Drive folder is a card with its first photos that opens the gallery;
 * Google Photos and other links open outside the app. EDITOR+ add with the + button ([startAdd] opens the sheet first,
 * from the Ringkasan + sheet); the member who added an album, or the OWNER, removes it.
 */
@Composable
fun AlbumsScreen(
    tripId: String,
    startAdd: Boolean,
    onBack: () -> Unit,
    onOpenGallery: (TripAlbumDto) -> Unit,
    viewModel: AlbumsViewModel = koinViewModel(key = "albums-$tripId") { parametersOf(tripId) },
    trips: TripRepository = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val trip by trips.observeTrip(tripId).collectAsState(initial = null)
    var adding by remember { mutableStateOf(startAdd) }
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                AlbumsEvent.Added -> { adding = false; snackbar.showSnackbar(getString(Res.string.album_added)) }
                AlbumsEvent.Deleted -> snackbar.showSnackbar(getString(Res.string.album_deleted))
                is AlbumsEvent.Problem -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(getString(albumProblemText(event.code)))
                }
            }
        }
    }
    val current = trip
    val canAdd = current?.myRole?.atLeast(TripRole.EDITOR) == true
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        AlbumsContent(
            state = state,
            tripTitle = current?.title.orEmpty(),
            canAdd = canAdd,
            myMemberId = viewModel.myMemberId,
            isOwner = current?.myRole == TripRole.OWNER,
            onBack = onBack,
            onOpen = { album -> if (album.provider == AlbumProvider.DRIVE_FOLDER) onOpenGallery(album) else runCatching { uriHandler.openUri(album.url) } },
            onDelete = viewModel::delete,
            preview = { album -> DriveAlbumPreview(AlbumSource(album.id, tripId = tripId)) },
        )
        if (canAdd && !state.fromDevice) {
            IconFab(
                Symbols.add,
                label = stringResource(Res.string.album_add),
                onClick = { viewModel.clearProblem(); adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { UndoSnackbar(message = it.visuals.message) }
    }
    if (adding && canAdd) {
        AddAlbumSheet(
            saving = state.saving,
            problem = state.addProblem,
            onSave = viewModel::add,
            onEdit = viewModel::clearProblem,
            onDismiss = { adding = false },
        )
    }
}

/** The first photos of a Drive album, from its own gallery ViewModel (the server caches the folder for everyone). */
@Composable
private fun DriveAlbumPreview(source: AlbumSource) {
    val gallery: AlbumGalleryViewModel = koinViewModel(key = "album-preview-${source.albumId}") { parametersOf(source) }
    val state by gallery.state.collectAsState()
    PhotoStrip(state.items, more = state.nextPageToken != null)
}

fun albumProblemText(code: String): StringResource = when (code) {
    AlbumsViewModel.URL -> Res.string.album_error_url
    AlbumsViewModel.TITLE -> Res.string.album_error_title
    ErrorCode.ALBUM_NOT_FOUND -> Res.string.album_error_not_found
    ErrorCode.ALBUM_UNAVAILABLE -> Res.string.album_error_unavailable
    ErrorCode.FORBIDDEN -> Res.string.album_error_forbidden
    else -> Res.string.album_error_generic
}

@Composable
fun AlbumsContent(
    state: AlbumsUiState,
    tripTitle: String,
    canAdd: Boolean,
    myMemberId: String?,
    isOwner: Boolean,
    onBack: () -> Unit,
    onOpen: (TripAlbumDto) -> Unit,
    onDelete: (TripAlbumDto) -> Unit,
    preview: @Composable (TripAlbumDto) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(stringResource(Res.string.albums_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.albums_count, tripTitle, state.albums.size), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.albums.isEmpty() -> FullScreenState(
                title = stringResource(Res.string.albums_empty_title),
                body = stringResource(if (canAdd) Res.string.albums_empty_body else Res.string.albums_empty_body_viewer),
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.albums.forEach { album ->
                    AlbumCard(
                        album = album,
                        canDelete = !state.fromDevice && (isOwner || album.addedByMemberId == myMemberId),
                        onOpen = { onOpen(album) },
                        onDelete = { onDelete(album) },
                        preview = { preview(album) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(if (state.fromDevice) Symbols.cloudOff else Symbols.info, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(
                        stringResource(if (state.fromDevice) Res.string.albums_offline_list else Res.string.albums_note),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The album's name, or one for its provider when it has none. */
@Composable
fun TripAlbumDto.displayTitle(): String = title ?: stringResource(
    when (provider) {
        AlbumProvider.DRIVE_FOLDER -> Res.string.album_default_drive
        AlbumProvider.GOOGLE_PHOTOS -> Res.string.album_default_photos
        AlbumProvider.OTHER -> Res.string.album_default_other
    },
)

/** "icloud.com" from a link, for the card of an outside album. */
fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').removePrefix("www.").lowercase()

/** The tile colour and icon of an album's provider. The icon subset has no photo_library or folder_shared. */
@Composable
internal fun providerTile(provider: AlbumProvider): Pair<Color, String> = when (provider) {
    AlbumProvider.DRIVE_FOLDER -> IrisTheme.accents.green to Symbols.landscape
    AlbumProvider.GOOGLE_PHOTOS -> IrisTheme.accents.blue to Symbols.photoCamera
    AlbumProvider.OTHER -> IrisTheme.accents.brown to Symbols.link
}

@Composable
private fun AlbumCard(album: TripAlbumDto, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit, preview: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drive = album.provider == AlbumProvider.DRIVE_FOLDER
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val title = album.displayTitle()
    val (tile, symbol) = providerTile(album.provider)
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.lg)
            .background(if (drive) scheme.surfaceContainerLowest else scheme.surfaceContainerLow)
            .then(if (drive) Modifier.border(1.dp, scheme.outlineVariant, IrisShape.lg) else Modifier)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        if (drive) preview()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(IrisShape.md).background(tile), contentAlignment = Alignment.Center) {
                Symbol(symbol, filled = true, tint = IrisTheme.accents.onAccent)
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (album.provider) {
                        AlbumProvider.DRIVE_FOLDER -> stringResource(Res.string.album_by_drive, album.addedByName)
                        AlbumProvider.GOOGLE_PHOTOS -> stringResource(Res.string.album_by_photos, album.addedByName)
                        AlbumProvider.OTHER -> stringResource(Res.string.album_by_other, hostOf(album.url), album.addedByName)
                    },
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!drive) Symbol(Symbols.openInNew, size = 22.dp, tint = scheme.onSurfaceVariant)
            if (canDelete) {
                Box {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
                        Symbol(Symbols.moreVert, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.album_more, title))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.album_delete), color = scheme.error) },
                            leadingIcon = { Symbol(Symbols.delete, tint = scheme.error) },
                            onClick = { menu = false; confirm = true },
                        )
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(Res.string.album_delete_confirm, title)) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(stringResource(Res.string.album_delete), color = scheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Four photos in a row, the last one "+N" when there are more (design "AlbumTrip"). */
@Composable
internal fun PhotoStrip(items: List<AlbumItemDto>, more: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { index ->
            val item = items.getOrNull(index)
            Box(Modifier.weight(1f).fillMaxHeight().background(scheme.surfaceVariant)) {
                if (item != null) AlbumPhoto(item, thumbnail = true, modifier = Modifier.fillMaxSize())
                val rest = items.size - 4
                if (index == 3 && (rest > 0 || (more && items.size >= 4))) {
                    Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (more) Res.string.album_more_photos_plus else Res.string.album_more_photos, rest.coerceAtLeast(0)),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = IrisTheme.accents.onAccent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Tambah album" (designs "TambahAlbum", "TambahAlbumPrivat"): the link is recognised as it is typed, with the warning
 * that anyone holding the link sees the photos. A Drive folder that is not shared publicly comes back as
 * ALBUM_NOT_PUBLIC with the steps to share it.
 */
@Composable
fun AddAlbumSheet(
    saving: Boolean,
    problem: String?,
    onSave: (url: String, title: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AdaptiveSheet(onDismiss = onDismiss) {
        AddAlbumForm(
            url = url,
            title = title,
            saving = saving,
            problem = problem,
            onUrl = { url = it.trim(); if (problem != null) onEdit() },
            onTitle = { title = it.take(ALBUM_TITLE_MAX) },
            onSave = { onSave(url, title) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AddAlbumForm(
    url: String,
    title: String,
    saving: Boolean,
    problem: String?,
    onUrl: (String) -> Unit,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val link = parseAlbumLink(url)
    val notPublic = problem == ErrorCode.ALBUM_NOT_PUBLIC
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.album_add), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
            }
        }
        IrisTextField(
            label = stringResource(Res.string.album_link_label),
            value = url,
            onValueChange = onUrl,
            placeholder = "https://drive.google.com/drive/folders/…",
            error = problem?.takeIf { it == AlbumsViewModel.URL || it == ErrorCode.ALBUM_NOT_FOUND }?.let { stringResource(albumProblemText(it)) },
        )
        if (notPublic) {
            NotPublicCard(onOpenDrive = { runCatching { uriHandler.openUri(url) } })
        } else if (link != null) {
            val (tile, symbol) = providerTile(link.provider)
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Symbol(symbol, filled = true, tint = tile)
                Column {
                    Text(
                        stringResource(
                            when (link.provider) {
                                AlbumProvider.DRIVE_FOLDER -> Res.string.album_kind_drive
                                AlbumProvider.GOOGLE_PHOTOS -> Res.string.album_kind_photos
                                AlbumProvider.OTHER -> Res.string.album_kind_other
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            when (link.provider) {
                                AlbumProvider.DRIVE_FOLDER -> Res.string.album_kind_drive_hint
                                AlbumProvider.GOOGLE_PHOTOS -> Res.string.album_kind_photos_hint
                                AlbumProvider.OTHER -> Res.string.album_kind_other_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        IrisTextField(
            label = stringResource(Res.string.album_title_label),
            labelSuffix = stringResource(Res.string.album_title_optional),
            value = title,
            onValueChange = onTitle,
            error = problem?.takeIf { it == AlbumsViewModel.TITLE }?.let { stringResource(albumProblemText(it)) },
        )
        if (!notPublic) {
            Row(
                Modifier.fillMaxWidth().clip(IrisShape.md).background(IrisTheme.accents.yellow.copy(alpha = 0.18f)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Symbol(Symbols.visibility, size = 20.dp, filled = true, tint = scheme.onSurface)
                Text(stringResource(Res.string.album_privacy), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
        }
        problem?.takeIf { it != AlbumsViewModel.URL && it != AlbumsViewModel.TITLE && it != ErrorCode.ALBUM_NOT_FOUND && !notPublic }?.let {
            Text(stringResource(albumProblemText(it)), style = MaterialTheme.typography.bodySmall, color = scheme.error)
        }
        LoadingButton(
            onClick = onSave,
            loading = saving,
            enabled = link != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(if (notPublic) Res.string.album_retry else Res.string.album_save)) }
    }
}

@Composable
private fun NotPublicCard(onOpenDrive: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.errorContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Symbols.lock, filled = true, tint = scheme.onErrorContainer)
            Text(stringResource(Res.string.album_not_public_title), style = MaterialTheme.typography.titleSmall, color = scheme.onErrorContainer)
        }
        Text(stringResource(Res.string.album_not_public_body), style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
        listOf(Res.string.album_not_public_step1, Res.string.album_not_public_step2, Res.string.album_not_public_step3).forEachIndexed { i, step ->
            Text("${i + 1}. " + stringResource(step), style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
        }
        Text(stringResource(Res.string.album_not_public_note), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
        TextButton(onClick = onOpenDrive) {
            Symbol(Symbols.openInNew, size = 18.dp)
            Text(stringResource(Res.string.album_open_drive_folder), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private val previewAt = Instant.parse("2026-10-31T00:00:00Z")
private val previewAlbums = listOf(
    TripAlbumDto("a1", "t", "https://drive.google.com/drive/folders/1AbCdEfGhIjK", AlbumProvider.DRIVE_FOLDER, "Foto Bali", "m1", "Septian", previewAt),
    TripAlbumDto("a2", "t", "https://photos.app.goo.gl/AbC", AlbumProvider.GOOGLE_PHOTOS, null, "m2", "Dinda", previewAt),
    TripAlbumDto("a3", "t", "https://www.icloud.com/sharedalbum/#B0", AlbumProvider.OTHER, "Video GoPro snorkeling", "m3", "Raka", previewAt),
)

@Preview
@Composable
private fun AlbumsContentPreview() = IrisPreview {
    AlbumsContent(
        state = AlbumsUiState(loading = false, albums = previewAlbums),
        tripTitle = "Bali 4 hari", canAdd = true, myMemberId = "m1", isOwner = true,
        onBack = {}, onOpen = {}, onDelete = {},
        preview = { PhotoStrip(emptyList(), more = false) },
    )
}

@Preview
@Composable
private fun AlbumsEmptyPreview() = IrisPreview {
    AlbumsContent(
        state = AlbumsUiState(loading = false), tripTitle = "Bali 4 hari", canAdd = false, myMemberId = null, isOwner = false,
        onBack = {}, onOpen = {}, onDelete = {},
    )
}

@Preview
@Composable
private fun AddAlbumFormPreview() = IrisPreview {
    Box(Modifier.padding(16.dp)) {
        AddAlbumForm("https://drive.google.com/drive/folders/1AbCdEfGhIjK", "Foto Bali", saving = false, problem = null, onUrl = {}, onTitle = {}, onSave = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun AddAlbumNotPublicPreview() = IrisPreview {
    Box(Modifier.padding(16.dp)) {
        AddAlbumForm("https://drive.google.com/drive/folders/1AbCdEfGhIjK", "", saving = false, problem = ErrorCode.ALBUM_NOT_PUBLIC, onUrl = {}, onTitle = {}, onSave = {}, onDismiss = {})
    }
}
