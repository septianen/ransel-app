package app.ransel.shared.domain

/*
 * Trip covers and profile photos (B19.1, F22.1). The apps shrink a picked photo to its kind's size and send it as JPEG;
 * the server writes it again at the same size and quality (dropping EXIF) and refuses a larger body.
 */

/** Largest body `PUT /trips/{id}/cover` and `PUT /me/avatar` accept. A shrunk photo is far below it. */
const val PHOTO_MAX_BYTES = 1_000_000

/** Largest width or height the server decodes; anything bigger is refused before it is read into memory. */
const val PHOTO_MAX_INPUT_SIDE = 8000

/** JPEG quality (0–100) of the apps and of the server's copy. */
const val PHOTO_JPEG_QUALITY = 70

enum class PhotoKind(val maxSide: Int) {
    /** Shown as a wide banner, cropped from the centre. */
    COVER(1280),

    /** Shown in a circle, cropped from the centre. */
    AVATAR(320),
}

/** [width] × [height] scaled down so neither side exceeds [maxSide]; a smaller photo keeps its size. */
fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxSide > 0)
    val longest = maxOf(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toDouble() / longest
    return maxOf(1, (width * scale).roundToIntHalfUp()) to maxOf(1, (height * scale).roundToIntHalfUp())
}

private fun Double.roundToIntHalfUp(): Int = kotlin.math.floor(this + 0.5).toInt()
