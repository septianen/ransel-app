package app.ransel.feature.expenses.receipt

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** A receipt photo ready to upload: at most [MAX_RECEIPT_BYTES], JPEG, PNG or WebP. */
class ReceiptImage(val bytes: ByteArray, val mimeType: String)

/** Same limit as the server (`ReceiptService.MAX_BYTES`). */
const val MAX_RECEIPT_BYTES = 5 * 1024 * 1024

/** Opens the camera; null where there is none to open from the app (the web picks a file instead). */
@Composable
expect fun rememberReceiptCamera(onImage: (PlatformFile?) -> Unit): (() -> Unit)?

/** Reads the picked file; on Android and iOS the photo is scaled down and recompressed so it stays well under the limit. */
expect suspend fun prepareReceiptImage(file: PlatformFile): ReceiptImage
