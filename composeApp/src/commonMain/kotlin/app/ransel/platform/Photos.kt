package app.ransel.platform

import app.ransel.shared.domain.PhotoKind
import io.github.vinceglb.filekit.PlatformFile

/**
 * A picked photo as the JPEG `PUT /trips/{id}/cover` and `PUT /me/avatar` take (B19.1): turned upright from its EXIF
 * orientation, shrunk so its long side is at most [PhotoKind.maxSide] and compressed at `PHOTO_JPEG_QUALITY`.
 * Throws when the file is not an image the platform can read.
 */
expect suspend fun preparePhoto(file: PlatformFile, kind: PhotoKind): ByteArray
