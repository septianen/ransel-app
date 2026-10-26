package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher

@Composable
actual fun rememberReceiptCamera(onImage: (PlatformFile?) -> Unit): (() -> Unit)? {
    val launcher = rememberCameraPickerLauncher(onResult = onImage)
    return { launcher.launch() }
}

/** Long side 2000 px at JPEG 80 keeps small print readable and a photo well under 1 MB. */
actual suspend fun prepareReceiptImage(file: PlatformFile): ReceiptImage =
    ReceiptImage(
        FileKit.compressImage(file, imageFormat = ImageFormat.JPEG, quality = 80, maxWidth = 2000, maxHeight = 2000),
        "image/jpeg",
    )
