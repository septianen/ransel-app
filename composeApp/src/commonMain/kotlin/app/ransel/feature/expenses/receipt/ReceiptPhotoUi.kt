package app.ransel.feature.expenses.receipt

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.repository.PendingReceipt
import app.ransel.data.repository.ReceiptUploadStatus
import app.ransel.feature.documents.viewer.DocumentPhotoSource
import app.ransel.feature.documents.viewer.PhotoLoad
import app.ransel.feature.documents.viewer.PhotoLoader
import app.ransel.feature.documents.viewer.PhotoViewer
import app.ransel.feature.documents.viewer.ViewerPhoto
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.dashedBorder
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.photo_camera
import ransel.composeapp.generated.resources.photo_gallery
import ransel.composeapp.generated.resources.receipt_photo_attach
import ransel.composeapp.generated.resources.receipt_photo_description
import ransel.composeapp.generated.resources.receipt_photo_failed
import ransel.composeapp.generated.resources.receipt_photo_full_body
import ransel.composeapp.generated.resources.receipt_photo_full_manage
import ransel.composeapp.generated.resources.receipt_photo_full_skip
import ransel.composeapp.generated.resources.receipt_photo_full_title
import ransel.composeapp.generated.resources.receipt_photo_in_documents
import ransel.composeapp.generated.resources.receipt_photo_replace
import ransel.composeapp.generated.resources.receipt_photo_saved_in
import ransel.composeapp.generated.resources.receipt_photo_source_hint
import ransel.composeapp.generated.resources.receipt_photo_title
import ransel.composeapp.generated.resources.receipt_photo_unreadable
import ransel.composeapp.generated.resources.receipt_photo_view
import ransel.composeapp.generated.resources.receipt_photo_waiting
import ransel.composeapp.generated.resources.trip_optional

/** A receipt photo picked or scanned, with the title its document gets ("Warung Made", "Struk 21 Nov"). */
class ReceiptAttachment(val image: ReceiptImage, val title: String)

/** What the "Foto struk" section of the expense form shows. */
sealed interface ReceiptPhotoUi {
    data object None : ReceiptPhotoUi
    /** Picked in this form: goes up with the expense when it is saved. */
    class Picked(val image: ReceiptImage) : ReceiptPhotoUi
    /** On the server, as the document [documentId]. */
    class Saved(val documentId: String) : ReceiptPhotoUi
    /** Kept on the phone: waiting, failed, or refused because the storage is full. */
    class Queued(val photo: PendingReceipt) : ReceiptPhotoUi
}

/** A small decoded copy of a photo for a thumbnail; null while loading or when it cannot be shown. */
@Composable
fun rememberThumbnail(key: Any, load: suspend () -> PhotoLoad?): ImageBitmap? =
    produceState<ImageBitmap?>(null, key) {
        value = (load() as? PhotoLoad.Ready)?.bytes?.let { bytes ->
            withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        }
    }.value

/** The thumbnail of a photo, or a receipt icon on a grey tile while it loads or cannot be shown. */
@Composable
fun PhotoThumbnail(image: ImageBitmap?, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, shape: Shape, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(width, height).clip(shape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Symbol(Symbols.receiptLong, size = 24.dp, tint = scheme.onSurfaceVariant)
    }
}

/** Screen 1: under the banner of "Cek hasil struk". The photo is stored when Simpan is pressed, with no extra choice. */
@Composable
fun ReceiptScanCard(image: ReceiptImage, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var viewing by remember { mutableStateOf(false) }
    val bitmap = rememberThumbnail(image) { PhotoLoad.Ready(image.bytes) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 96.dp).clip(IrisShape.lg).background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoThumbnail(bitmap, 56.dp, 72.dp, IrisShape.sm)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.receipt_photo_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(stringResource(Res.string.receipt_photo_saved_in), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            TextButton(onClick = { viewing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.receipt_photo_view), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (viewing) ReceiptPhotoViewer(image, onClose = { viewing = false })
}

/** The viewer for one photo that is not a document (yet): bytes on the phone. */
@Composable
fun ReceiptPhotoViewer(image: ReceiptImage, onClose: () -> Unit) {
    PhotoViewer(
        photos = listOf(ViewerPhoto("local", heading = stringResource(Res.string.receipt_photo_title), title = stringResource(Res.string.receipt_photo_title))),
        startIndex = 0,
        loader = PhotoLoader { PhotoLoad.Ready(image.bytes) },
        onClose = onClose,
        prefetch = false,
    )
}

/** The viewer for the photo of a document. */
@Composable
fun DocumentPhotoViewer(documentId: String, title: String, source: DocumentPhotoSource, onClose: () -> Unit) {
    PhotoViewer(
        photos = listOf(ViewerPhoto(documentId, heading = title, title = title)),
        startIndex = 0,
        loader = PhotoLoader { source.load(documentId, DocumentPhotoSource.receiptName(documentId)) },
        onClose = onClose,
        prefetch = false,
    )
}

/**
 * Screens 2, 3, 7 and 8: "Foto struk" in the expense form. Nothing yet = a dashed box ("Lampirkan foto struk", "(opsional)");
 * a photo = a card with its thumbnail, where it is ("Tersimpan di Dokumen trip" / "Menunggu upload" / "Gagal diunggah ·
 * Coba lagi") and "Ganti foto"; a full storage = what to do about it. The status is always a text, not only an icon.
 */
@Composable
fun ReceiptPhotoSection(
    state: ReceiptPhotoUi,
    title: String,
    source: DocumentPhotoSource?,
    onPick: (ReceiptImage) -> Unit,
    onUnreadable: () -> Unit,
    onRetry: () -> Unit,
    onDiscardQueued: () -> Unit,
    modifier: Modifier = Modifier,
    onManageDocuments: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var choosing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    val bitmap = when (state) {
        ReceiptPhotoUi.None -> null
        is ReceiptPhotoUi.Picked -> rememberThumbnail(state.image) { PhotoLoad.Ready(state.image.bytes) }
        is ReceiptPhotoUi.Queued -> rememberThumbnail(state.photo) { PhotoLoad.Ready(state.photo.bytes) }
        is ReceiptPhotoUi.Saved -> rememberThumbnail(state.documentId) { source?.load(state.documentId, DocumentPhotoSource.receiptName(state.documentId)) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(stringResource(Res.string.receipt_photo_title), style = MaterialTheme.typography.labelLarge)
            if (state == ReceiptPhotoUi.None) {
                Text(" " + stringResource(Res.string.trip_optional), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
        }
        if (state == ReceiptPhotoUi.None) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).clip(IrisShape.lg).dashedBorder(scheme.outline).clickable(role = Role.Button) { choosing = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(Symbols.photoCamera, size = 24.dp, tint = scheme.primary)
                Text(
                    stringResource(Res.string.receipt_photo_attach),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.primary,
                )
            }
        } else {
            val queued = (state as? ReceiptPhotoUi.Queued)?.photo
            val statusText = when {
                queued == null && state is ReceiptPhotoUi.Picked -> stringResource(Res.string.receipt_photo_saved_in)
                queued == null -> stringResource(Res.string.receipt_photo_in_documents)
                queued.status == ReceiptUploadStatus.WAITING -> stringResource(Res.string.receipt_photo_waiting)
                queued.status == ReceiptUploadStatus.FAILED -> stringResource(Res.string.receipt_photo_failed)
                else -> stringResource(Res.string.receipt_photo_full_title)
            }
            val description = stringResource(Res.string.receipt_photo_description, title)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).clip(IrisShape.lg).border(1.dp, scheme.outlineVariant, IrisShape.lg).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoThumbnail(
                    bitmap, 64.dp, 80.dp, IrisShape.sm,
                    Modifier.clickable(role = Role.Button) { viewing = true }.semantics { contentDescription = description },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        // "Gagal diunggah · Coba lagi": the line itself is the retry
                        Modifier.then(if (queued?.status == ReceiptUploadStatus.FAILED) Modifier.heightIn(min = 36.dp).clickable(role = Role.Button, onClick = onRetry) else Modifier),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (queued != null) Symbol(Symbols.cloudOff, size = 14.dp, tint = scheme.onSurfaceVariant)
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (queued?.status == ReceiptUploadStatus.FAILED || queued?.status == ReceiptUploadStatus.FULL) scheme.error else scheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.heightIn(min = 36.dp)) {
                        Text(stringResource(Res.string.receipt_photo_replace), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (queued?.status == ReceiptUploadStatus.FULL) {
                Column(Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.errorContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.receipt_photo_full_title), style = MaterialTheme.typography.titleSmall, color = scheme.onErrorContainer)
                    Text(stringResource(Res.string.receipt_photo_full_body), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (onManageDocuments != null) TextButton(onClick = onManageDocuments, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.receipt_photo_full_manage)) }
                        TextButton(onClick = onDiscardQueued, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.receipt_photo_full_skip)) }
                    }
                }
            }
        }
    }
    if (choosing) ReceiptSourceSheet(onPicked = onPick, onUnreadable = onUnreadable, onDismiss = { choosing = false })
    if (viewing) when (state) {
        is ReceiptPhotoUi.Picked -> ReceiptPhotoViewer(state.image) { viewing = false }
        is ReceiptPhotoUi.Queued -> ReceiptPhotoViewer(ReceiptImage(state.photo.bytes, state.photo.mimeType)) { viewing = false }
        is ReceiptPhotoUi.Saved -> if (source != null) DocumentPhotoViewer(state.documentId, title, source) { viewing = false } else viewing = false
        ReceiptPhotoUi.None -> viewing = false
    }
}

/** Screen 3's sheet: take a photo or choose one; the photo is scaled down like a scanned receipt. */
@Composable
private fun ReceiptSourceSheet(onPicked: (ReceiptImage) -> Unit, onUnreadable: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val onFile: (PlatformFile?) -> Unit = { file ->
        // the sheet stays until the photo is ready: closing it would cancel this scope
        if (file != null) scope.launch {
            val image = runCatching { prepareReceiptImage(file) }.getOrNull()
            onDismiss()
            if (image != null) onPicked(image) else onUnreadable()
        }
    }
    val camera = rememberReceiptCamera(onFile)
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image, onResult = onFile)
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.receipt_photo_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.receipt_photo_source_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (camera != null) {
                androidx.compose.material3.Button(onClick = camera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Symbol(Symbols.photoCamera, size = 20.dp)
                    Text(stringResource(Res.string.photo_camera), Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = { gallery.launch() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.addPhotoAlternate, size = 20.dp)
                Text(stringResource(Res.string.photo_gallery), Modifier.padding(start = 8.dp))
            }
        }
    }
}
