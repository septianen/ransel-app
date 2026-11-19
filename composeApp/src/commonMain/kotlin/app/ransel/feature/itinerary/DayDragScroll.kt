package app.ransel.feature.itinerary

/** Viewport edge band, in dp, where a held card starts auto-scrolling the day list. */
internal const val EdgeZoneDp = 56f

/** Auto-scroll speed at the pixel of the edge, in dp per second; 0 at the inner edge of [EdgeZoneDp]. */
internal const val MaxEdgeScrollDpPerSec = 960f

/**
 * How fast the itinerary list should auto-scroll while a card is held near a viewport edge.
 *
 * Speed is 0 outside the viewport and outside the edge zone, and ramps from 0 at the inner
 * boundary of the zone to [maxPxPerSec] at the pixel of the edge. Negative = scroll toward
 * the start of the list (finger near the top); positive = toward the end (finger near the bottom).
 */
internal fun edgeScrollVelocityPxPerSec(
    fingerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    zonePx: Float,
    maxPxPerSec: Float,
): Float {
    if (zonePx <= 0f || maxPxPerSec <= 0f) return 0f
    if (fingerY < viewportTop || fingerY > viewportBottom) return 0f
    val fromTop = fingerY - viewportTop
    if (fromTop < zonePx) return -maxPxPerSec * (1f - fromTop / zonePx)
    val fromBottom = viewportBottom - fingerY
    if (fromBottom < zonePx) return maxPxPerSec * (1f - fromBottom / zonePx)
    return 0f
}
