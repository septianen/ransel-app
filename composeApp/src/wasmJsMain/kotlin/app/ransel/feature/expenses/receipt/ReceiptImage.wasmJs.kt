package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes

@Composable
actual fun rememberReceiptCamera(onImage: (PlatformFile?) -> Unit): (() -> Unit)? = null

/** The browser sends the file as it is; the type comes from its extension. */
actual suspend fun prepareReceiptImage(file: PlatformFile): ReceiptImage {
    val type = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    return ReceiptImage(file.readBytes(), type)
}
