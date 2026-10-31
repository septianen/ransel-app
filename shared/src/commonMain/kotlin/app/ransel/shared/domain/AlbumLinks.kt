package app.ransel.shared.domain

import app.ransel.shared.model.AlbumProvider

/**
 * A trip album link, read from what the member pasted. [driveFolderId] (and [resourceKey], for folders shared before
 * Google's 2021 security update) are set for a Google Drive folder only.
 */
data class AlbumLink(
    val provider: AlbumProvider,
    val driveFolderId: String? = null,
    val resourceKey: String? = null,
)

const val ALBUM_TITLE_MAX = 80
const val ALBUM_URL_MAX = 2_000

private val DRIVE_HOSTS = setOf("drive.google.com", "docs.google.com")
private val PHOTOS_HOSTS = setOf("photos.google.com", "photos.app.goo.gl", "goo.gl")
private val DRIVE_ID = Regex("^[A-Za-z0-9_-]{10,}$")
private val RESOURCE_KEY = Regex("^[A-Za-z0-9_-]+$")
private val FOLDER_PATH = Regex("^/(?:drive/)?(?:u/\\d+/)?folders/([A-Za-z0-9_-]+)")
private val URL = Regex("^https?://([^/?#:]+)(?::\\d+)?([^?#]*)(?:\\?([^#]*))?(?:#.*)?$", RegexOption.IGNORE_CASE)

/**
 * Reads an album link. Null when [url] is not an http(s) link. A Drive link that is a folder becomes DRIVE_FOLDER
 * (forms `/drive/folders/<id>`, `/drive/u/0/folders/<id>`, `/folderview?id=<id>`, `/open?id=<id>`, each with an
 * optional `resourcekey=`); a Drive file link and every other site are OTHER, shown as a card that opens outside.
 */
fun parseAlbumLink(url: String): AlbumLink? {
    val match = URL.matchEntire(url.trim()) ?: return null
    val host = match.groupValues[1].lowercase().removePrefix("www.")
    val path = match.groupValues[2]
    val query = queryOf(match.groupValues[3])
    return when {
        host in PHOTOS_HOSTS && (host != "goo.gl" || path.startsWith("/photos/")) -> AlbumLink(AlbumProvider.GOOGLE_PHOTOS)
        host in DRIVE_HOSTS -> {
            val id = FOLDER_PATH.find(path)?.groupValues?.get(1)
                ?: query["id"].takeIf { path == "/open" || path == "/folderview" || path == "/drive/folderview" }
            val resourceKey = query["resourcekey"]?.takeIf { RESOURCE_KEY.matches(it) }
            if (id != null && DRIVE_ID.matches(id)) AlbumLink(AlbumProvider.DRIVE_FOLDER, id, resourceKey)
            else AlbumLink(AlbumProvider.OTHER)
        }
        else -> AlbumLink(AlbumProvider.OTHER)
    }
}

/** The fields of a new album that fail: `url` = INVALID (not an http(s) link, or longer than [ALBUM_URL_MAX]), `title` = TOO_LONG. */
fun validateAlbum(url: String, title: String?): Map<String, String> = buildMap {
    if (url.trim().length > ALBUM_URL_MAX || parseAlbumLink(url) == null) put("url", "INVALID")
    if (title != null && title.trim().length > ALBUM_TITLE_MAX) put("title", "TOO_LONG")
}

private fun queryOf(query: String): Map<String, String> =
    query.split('&').filter { '=' in it }.associate { part ->
        val (key, value) = part.split('=', limit = 2)
        key.lowercase() to value
    }
