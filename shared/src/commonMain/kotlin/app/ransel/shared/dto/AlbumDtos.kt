package app.ransel.shared.dto

import app.ransel.shared.model.AlbumProvider
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Trip albums: links to photos the members keep elsewhere. RANSEL stores only the link. A public Google Drive folder
 * ("Anyone with the link") is listed by the server with its Drive API key and shown as a gallery; the image bytes load
 * straight from Google. Every other link (Google Photos, iCloud, ...) is a card that opens outside the app. Every member
 * sees them; EDITOR+ adds them; the member who added one or the OWNER deletes it. Online only (not in /sync).
 */

@Serializable
data class TripAlbumDto(
    val id: String,
    val tripId: String,
    val url: String,
    val provider: AlbumProvider,
    /** Null = the app shows a name for the provider ("Album Google Drive"). */
    val title: String? = null,
    val addedByMemberId: String,
    val addedByName: String,
    val createdAt: Timestamp,
)

/** GET /trips/{id}/albums: newest first. */
@Serializable
data class TripAlbumsDto(val items: List<TripAlbumDto>)

/** POST /trips/{id}/albums. The server reads the provider from [url] (`domain/AlbumLinks.kt`). */
@Serializable
data class CreateAlbumRequest(
    val id: String,
    val url: String,
    val title: String? = null,
)

/**
 * One photo of a Drive folder. [thumbnailUrl] (about 400 px wide) and [imageUrl] (up to 2048 px) are Google URLs
 * the app loads directly; they work without signing in while the folder stays public.
 */
@Serializable
data class AlbumItemDto(
    val id: String,
    val name: String,
    val width: Int? = null,
    val height: Int? = null,
    /** When the photo was taken, from its metadata, if Drive knows. */
    val takenAt: Timestamp? = null,
    val thumbnailUrl: String,
    val imageUrl: String,
)

/** GET .../albums/{albumId}/items: up to 100 photos, oldest first; pass [nextPageToken] back as `pageToken` for more. */
@Serializable
data class AlbumItemsDto(
    val items: List<AlbumItemDto>,
    val nextPageToken: String? = null,
)
