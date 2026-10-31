package app.ransel.platform

import app.ransel.shared.domain.PHOTO_JPEG_QUALITY
import app.ransel.shared.domain.PhotoKind
import app.ransel.shared.domain.fitWithin
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import kotlinx.coroutines.await
import kotlin.js.Promise

/*
 * The browser does the work: `createImageBitmap` decodes the File the picker gave (turned upright from its EXIF
 * orientation) and a canvas writes the JPEG. Only the small result is copied into Kotlin.
 */

@JsFun("(file) => createImageBitmap(file, { imageOrientation: 'from-image' })")
private external fun decodeImage(file: JsAny): Promise<JsAny>

@JsFun("(bitmap) => bitmap.width")
private external fun widthOf(bitmap: JsAny): Int

@JsFun("(bitmap) => bitmap.height")
private external fun heightOf(bitmap: JsAny): Int

@JsFun(
    """(bitmap, w, h, quality) => {
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const g = canvas.getContext('2d');
        g.imageSmoothingEnabled = true;
        g.imageSmoothingQuality = 'high';
        g.drawImage(bitmap, 0, 0, w, h);
        bitmap.close();
        return new Promise((resolve, reject) => canvas.toBlob(
            (blob) => blob ? blob.arrayBuffer().then((b) => resolve(new Int8Array(b)), reject) : reject(new Error('no jpeg')),
            'image/jpeg', quality));
    }""",
)
private external fun encodeJpeg(bitmap: JsAny, w: Int, h: Int, quality: Double): Promise<JsAny>

@JsFun("(a) => a.length")
private external fun lengthOf(array: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun byteAt(array: JsAny, index: Int): Byte

actual suspend fun preparePhoto(file: PlatformFile, kind: PhotoKind): ByteArray {
    val browserFile = (file.webFile as? WebFile.FileWrapper)?.file ?: error("Not a file")
    val bitmap = decodeImage(browserFile).await<JsAny>()
    val (w, h) = fitWithin(widthOf(bitmap), heightOf(bitmap), kind.maxSide)
    val jpeg = encodeJpeg(bitmap, w, h, PHOTO_JPEG_QUALITY / 100.0).await<JsAny>()
    return ByteArray(lengthOf(jpeg)) { byteAt(jpeg, it) }
}
