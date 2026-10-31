package app.ransel.platform

import app.ransel.shared.domain.PHOTO_JPEG_QUALITY
import app.ransel.shared.domain.PhotoKind
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage

/** UIImage draws the photo upright from its EXIF orientation, so FileKit's compression is enough here. */
actual suspend fun preparePhoto(file: PlatformFile, kind: PhotoKind): ByteArray =
    FileKit.compressImage(file, imageFormat = ImageFormat.JPEG, quality = PHOTO_JPEG_QUALITY, maxWidth = kind.maxSide, maxHeight = kind.maxSide)
