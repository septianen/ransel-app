package app.ransel.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import app.ransel.shared.domain.PHOTO_JPEG_QUALITY
import app.ransel.shared.domain.PhotoKind
import app.ransel.shared.domain.fitWithin
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Not `FileKit.compressImage`: it does not read the EXIF orientation, so a photo from many phone cameras would come out
 * sideways. Decoded at a power-of-two sample size first, so a 50 MP photo never sits in memory at full size.
 */
actual suspend fun preparePhoto(file: PlatformFile, kind: PhotoKind): ByteArray = withContext(Dispatchers.Default) {
    val bytes = file.readBytes()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= kind.maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Not an image")
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val (w, h) = fitWithin(decoded.width, decoded.height, kind.maxSide)
    val matrix = Matrix().apply {
        postScale(w / decoded.width.toFloat(), h / decoded.height.toFloat())
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }.toByteArray()
}
