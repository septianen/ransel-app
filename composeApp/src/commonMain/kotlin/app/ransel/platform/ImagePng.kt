package app.ransel.platform

import androidx.compose.ui.graphics.ImageBitmap

/** PNG bytes of a captured composable (the recap card), to share or download. */
expect fun ImageBitmap.toPngBytes(): ByteArray
