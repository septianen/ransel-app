package app.ransel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.data.remote.ApiFailure
import app.ransel.feature.expenses.receipt.rememberReceiptCamera
import app.ransel.platform.preparePhoto
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PhotoKind
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.photo_camera
import ransel.composeapp.generated.resources.photo_error_failed
import ransel.composeapp.generated.resources.photo_error_offline
import ransel.composeapp.generated.resources.photo_error_too_large
import ransel.composeapp.generated.resources.photo_error_unreadable
import ransel.composeapp.generated.resources.photo_gallery
import ransel.composeapp.generated.resources.photo_remove

/** Why a cover or profile photo did not change (B19.1); shown under the photo. */
enum class PhotoProblem { OFFLINE, TOO_LARGE, UNREADABLE, FAILED }

fun ApiFailure.photoProblem(): PhotoProblem = when (code) {
    ErrorCode.NETWORK_UNAVAILABLE, ErrorCode.NETWORK_TIMEOUT -> PhotoProblem.OFFLINE
    ErrorCode.FILE_TOO_LARGE -> PhotoProblem.TOO_LARGE
    ErrorCode.UNSUPPORTED_FILE -> PhotoProblem.UNREADABLE
    else -> PhotoProblem.FAILED
}

@Composable
fun PhotoProblem.text(): String = stringResource(
    when (this) {
        PhotoProblem.OFFLINE -> Res.string.photo_error_offline
        PhotoProblem.TOO_LARGE -> Res.string.photo_error_too_large
        PhotoProblem.UNREADABLE -> Res.string.photo_error_unreadable
        PhotoProblem.FAILED -> Res.string.photo_error_failed
    },
)

/**
 * "Ganti foto": camera (mobile) or gallery, then the photo shrunk for [kind] (`platform/Photos.kt`) goes to [onPicked].
 * A file the device cannot read as an image goes to [onUnreadable]. [onRemove] null = there is no photo to remove.
 * Built from the Foto struk chooser (not designed yet).
 */
@Composable
fun PhotoSourceSheet(
    title: String,
    kind: PhotoKind,
    onPicked: (ByteArray) -> Unit,
    onUnreadable: () -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onFile: (PlatformFile?) -> Unit = { file ->
        // the sheet stays until the photo is ready: closing it would cancel this scope
        if (file != null) scope.launch {
            val jpeg = try {
                preparePhoto(file, kind)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            onDismiss()
            if (jpeg != null) onPicked(jpeg) else onUnreadable()
        }
    }
    val camera = rememberReceiptCamera(onFile)
    val gallery = rememberFilePickerLauncher(type = FileKitType.Image, onResult = onFile)
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
            if (camera != null) {
                Button(onClick = camera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Symbol(Symbols.photoCamera, size = 20.dp)
                    Text(stringResource(Res.string.photo_camera), Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = { gallery.launch() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Symbol(Symbols.addPhotoAlternate, size = 20.dp)
                Text(stringResource(Res.string.photo_gallery), Modifier.padding(start = 8.dp))
            }
            if (onRemove != null) {
                TextButton(
                    onClick = { onDismiss(); onRemove() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Symbol(Symbols.delete, size = 20.dp)
                    Text(stringResource(Res.string.photo_remove), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
