package app.ransel.feature.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ransel.data.remote.apiOrigin
import app.ransel.shared.api.ApiRoutes
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/** Absolute URL of a place photo (the API gives a signed path relative to `ApiRoutes.BASE`); null without a photo. */
internal fun placePhotoUrl(photoUrl: String?): String? {
    val path = photoUrl?.takeIf { it.isNotBlank() } ?: return null
    if (path.startsWith("http")) return path
    return apiOrigin().trimEnd('/') + ApiRoutes.BASE + "/" + path.trimStart('/')
}

/**
 * The signed URL changes with every server response (its expiry moves), so Coil's default cache key, the URL, would
 * miss every time. A key per place lets the swipe card and the detail page share one cached image: opening the
 * detail does not ask Google for the photo again, and every miss would be a billed request.
 */
internal fun placePhotoRequest(context: PlatformContext, photoUrl: String?, placeId: String): ImageRequest? {
    val url = placePhotoUrl(photoUrl) ?: return null
    val key = "place-photo:$placeId"
    return ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key).build()
}

@Composable
internal fun rememberPlacePhotoRequest(photoUrl: String?, placeId: String): ImageRequest? {
    val context = LocalPlatformContext.current
    return remember(photoUrl, placeId, context) { placePhotoRequest(context, photoUrl, placeId) }
}
