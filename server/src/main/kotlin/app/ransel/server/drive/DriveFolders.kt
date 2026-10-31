package app.ransel.server.drive

import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One image of a Drive folder, as Drive describes it. Width and height are as stored; [rotation] is in quarter turns. */
data class DriveImage(val id: String, val name: String, val width: Int?, val height: Int?, val rotation: Int = 0)

data class DrivePage(val images: List<DriveImage>, val nextPageToken: String?)

/**
 * Reads public Google Drive folders. Failures are [ApiException]s with an ALBUM_* code: ALBUM_NOT_PUBLIC when Drive
 * will not show the folder to an API key (a private folder looks the same as a missing one), ALBUM_NOT_FOUND when the
 * link is not a folder, ALBUM_UNAVAILABLE when Drive cannot be asked (quota, outage, no key).
 */
interface DriveFolders {
    /** Throws unless [folderId] is a folder anyone with the link can see. */
    suspend fun requireFolder(folderId: String, resourceKey: String?)

    /** Up to 100 images of the folder, oldest first. */
    suspend fun images(folderId: String, resourceKey: String?, pageToken: String?): DrivePage
}

/** No `GOOGLE_DRIVE_API_KEY`: albums save, the gallery cannot load. */
object NotConfiguredDrive : DriveFolders {
    override suspend fun requireFolder(folderId: String, resourceKey: String?) = Unit
    override suspend fun images(folderId: String, resourceKey: String?, pageToken: String?): DrivePage = throw unavailable("Drive is not configured")
}

/**
 * Drive API v3 with an API key (no OAuth: no user data scope, so no Google verification). The key goes in a header,
 * never in a URL, and nothing of it reaches the app. Folders shared before Google's 2021 security update also need
 * their resource key, sent in `X-Goog-Drive-Resource-Keys`.
 */
class GoogleDriveFolders(
    private val http: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://www.googleapis.com/drive/v3",
) : DriveFolders {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requireFolder(folderId: String, resourceKey: String?) {
        val response = http.get("$baseUrl/files/$folderId") {
            auth(folderId, resourceKey)
            parameter("fields", "id,mimeType")
            parameter("supportsAllDrives", true)
        }
        failOn(response)
        val file = json.decodeFromString<FileJson>(response.bodyAsText())
        if (file.mimeType != FOLDER_MIME) throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.ALBUM_NOT_FOUND, "The link is not a Drive folder")
    }

    override suspend fun images(folderId: String, resourceKey: String?, pageToken: String?): DrivePage {
        val response = http.get("$baseUrl/files") {
            auth(folderId, resourceKey)
            parameter("q", "'$folderId' in parents and trashed = false and mimeType contains 'image/'")
            parameter("fields", "nextPageToken,files(id,name,imageMediaMetadata(width,height,rotation))")
            parameter("orderBy", "createdTime")
            parameter("pageSize", PAGE_SIZE)
            parameter("supportsAllDrives", true)
            parameter("includeItemsFromAllDrives", true)
            if (pageToken != null) parameter("pageToken", pageToken)
        }
        failOn(response)
        val list = json.decodeFromString<ListJson>(response.bodyAsText())
        return DrivePage(
            list.files.map { DriveImage(it.id, it.name, it.imageMediaMetadata?.width, it.imageMediaMetadata?.height, it.imageMediaMetadata?.rotation ?: 0) },
            list.nextPageToken?.takeIf { it.isNotBlank() },
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(folderId: String, resourceKey: String?) {
        header("X-Goog-Api-Key", apiKey)
        if (resourceKey != null) header("X-Goog-Drive-Resource-Keys", "$folderId/$resourceKey")
    }

    private suspend fun failOn(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val reason = runCatching { json.decodeFromString<ErrorJson>(response.bodyAsText()).error.errors.firstOrNull()?.reason }.getOrNull()
        throw when {
            response.status == HttpStatusCode.NotFound ->
                ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.ALBUM_NOT_PUBLIC, "The Drive folder is not shared with anyone who has the link")
            response.status == HttpStatusCode.Forbidden && reason in NO_ACCESS ->
                ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.ALBUM_NOT_PUBLIC, "The Drive folder is not shared with anyone who has the link")
            else -> unavailable("Drive answered ${response.status.value}${reason?.let { " ($it)" }.orEmpty()}")
        }
    }

    @Serializable private data class FileJson(val id: String = "", val mimeType: String = "")
    @Serializable private data class ListJson(val files: List<ImageJson> = emptyList(), val nextPageToken: String? = null)
    @Serializable private data class ImageJson(val id: String, val name: String = "", val imageMediaMetadata: MetaJson? = null)
    @Serializable private data class MetaJson(val width: Int? = null, val height: Int? = null, val rotation: Int? = null)
    @Serializable private data class ErrorJson(val error: ErrorBody = ErrorBody())
    @Serializable private data class ErrorBody(val errors: List<ErrorItem> = emptyList())
    @Serializable private data class ErrorItem(val reason: String? = null)

    private companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val PAGE_SIZE = 100
        /** 403 reasons that mean "not shared publicly"; quota and key problems (rateLimitExceeded, ...) are ALBUM_UNAVAILABLE. */
        val NO_ACCESS = setOf("forbidden", "insufficientFilePermissions", "appNotAuthorizedToFile", "domainPolicy")
    }
}

internal fun unavailable(message: String) = ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.ALBUM_UNAVAILABLE, message)
