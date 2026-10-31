package app.ransel.server.service

import app.ransel.server.db.TripAlbums
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.drive.DriveFolders
import app.ransel.server.drive.DriveImage
import app.ransel.server.drive.DrivePage
import app.ransel.server.drive.unavailable
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.parseAlbumLink
import app.ransel.shared.domain.validateAlbum
import app.ransel.shared.dto.AlbumItemDto
import app.ransel.shared.dto.AlbumItemsDto
import app.ransel.shared.dto.CreateAlbumRequest
import app.ransel.shared.dto.TripAlbumDto
import app.ransel.shared.dto.TripAlbumsDto
import app.ransel.shared.model.AlbumProvider
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Trip albums: links to photos the members keep in their own Google Drive or elsewhere. RANSEL stores only the link; a
 * public Drive folder is listed through [drive] and its images load straight from Google, so no photo passes through
 * this server. Every member sees the albums, EDITOR+ adds them, the member who added one or the OWNER removes it.
 *
 * Folder listings are cached in memory for [cacheTtl] (per folder and page) so a gallery opened by the whole group
 * costs one Drive call; a removed or newly private folder shows up within that time.
 */
class AlbumService(
    private val drive: DriveFolders,
    private val cacheTtl: Duration = Duration.ofMinutes(5),
    private val now: () -> Instant = Instant::now,
) {
    private data class PageKey(val folderId: String, val resourceKey: String?, val pageToken: String?)
    private class Cached(val at: Instant, val page: DrivePage)
    private val cache = ConcurrentHashMap<PageKey, Cached>()

    fun list(userId: UUID, tripId: UUID): TripAlbumsDto {
        requireRole(tripId, userId, Role.VIEWER)
        return TripAlbumsDto(transaction { forTrip(tripId) })
    }

    /** A Drive folder is asked once before it is saved, so a private one is refused with ALBUM_NOT_PUBLIC right away. */
    suspend fun create(userId: UUID, tripId: UUID, body: CreateAlbumRequest): TripAlbumDto {
        requireRole(tripId, userId, Role.EDITOR)
        val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: throw validationFailed(mapOf("id" to "INVALID_UUID"))
        val title = body.title?.trim()?.takeIf { it.isNotEmpty() }
        requireValid(validateAlbum(body.url, title))
        val url = body.url.trim()
        val link = parseAlbumLink(url)!!
        if (link.provider == AlbumProvider.DRIVE_FOLDER) askDrive { drive.requireFolder(link.driveFolderId!!, link.resourceKey) }
        return transaction {
            // the same id again (a retried POST) gives back what was saved
            TripAlbums.selectAll().where { TripAlbums.id eq id }.singleOrNull()?.let { existing ->
                if (existing[TripAlbums.tripId] != tripId) throw ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "This id is taken")
                return@transaction load(id)
            }
            TripAlbums.insert {
                it[TripAlbums.id] = id
                it[TripAlbums.tripId] = tripId
                it[TripAlbums.url] = url
                it[provider] = link.provider
                it[driveFolderId] = link.driveFolderId
                it[resourceKey] = link.resourceKey
                it[TripAlbums.title] = title
                it[addedByMemberId] = callerMemberId(tripId, userId)
            }
            load(id)
        }
    }

    fun delete(userId: UUID, tripId: UUID, albumId: UUID) {
        val role = requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val row = row(tripId, albumId)
            if (row[TripAlbums.addedByMemberId] != callerMemberId(tripId, userId) && role != Role.OWNER) throw forbidden()
            TripAlbums.update({ TripAlbums.id eq albumId }) { it[deletedAt] = Instant.now() }
        }
    }

    suspend fun items(userId: UUID, tripId: UUID, albumId: UUID, pageToken: String?): AlbumItemsDto {
        requireRole(tripId, userId, Role.VIEWER)
        return itemsOf(tripId, albumId, pageToken)
    }

    /** The guest page: [tripId] comes from the link code (see `GuestService.tripOf`). */
    suspend fun guestItems(tripId: UUID, albumId: UUID, pageToken: String?): AlbumItemsDto = itemsOf(tripId, albumId, pageToken)

    private suspend fun itemsOf(tripId: UUID, albumId: UUID, pageToken: String?): AlbumItemsDto {
        val row = transaction { TripAlbums.selectAll().where { (TripAlbums.id eq albumId) and (TripAlbums.tripId eq tripId) and TripAlbums.deletedAt.isNull() }.singleOrNull() }
        val folderId = row?.get(TripAlbums.driveFolderId)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.ALBUM_NOT_FOUND, "No Drive album here")
        val token = pageToken?.takeIf { it.isNotBlank() }
        if (token != null && token.length > 512) throw validationFailed(mapOf("pageToken" to "INVALID"))
        val page = page(PageKey(folderId, row[TripAlbums.resourceKey], token))
        return AlbumItemsDto(page.images.map { it.toDto() }, page.nextPageToken)
    }

    private suspend fun page(key: PageKey): DrivePage {
        val at = now()
        cache[key]?.takeIf { Duration.between(it.at, at) < cacheTtl }?.let { return it.page }
        val page = askDrive { drive.images(key.folderId, key.resourceKey, key.pageToken) }
        if (cache.size >= MAX_CACHED_PAGES) cache.entries.removeIf { Duration.between(it.value.at, at) >= cacheTtl }
        if (cache.size >= MAX_CACHED_PAGES) cache.clear()
        cache[key] = Cached(at, page)
        return page
    }

    /** Drive failures arrive as ApiExceptions; anything else (no network, a bad answer) is ALBUM_UNAVAILABLE. */
    private suspend fun <T> askDrive(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        throw e
    } catch (e: Exception) {
        throw unavailable("Drive could not be asked: ${e.javaClass.simpleName}")
    }

    private fun row(tripId: UUID, albumId: UUID): ResultRow =
        TripAlbums.selectAll().where { (TripAlbums.id eq albumId) and (TripAlbums.tripId eq tripId) and TripAlbums.deletedAt.isNull() }
            .singleOrNull() ?: throw notFound("Album")

    companion object {
        private const val MAX_CACHED_PAGES = 1_000

        /** The trip's albums, newest first (inside a transaction; the guest page uses it too). */
        internal fun forTrip(tripId: UUID): List<TripAlbumDto> = joined()
            .where { (TripAlbums.tripId eq tripId) and TripAlbums.deletedAt.isNull() }
            .orderBy(TripAlbums.createdAt to SortOrder.DESC)
            .map { it.toDto() }

        private fun load(id: UUID): TripAlbumDto = joined().where { TripAlbums.id eq id }.single().toDto()

        private fun joined() = TripAlbums.join(TripMembers, JoinType.INNER, TripAlbums.addedByMemberId, TripMembers.id)
            .join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll()

        private fun ResultRow.toDto() = TripAlbumDto(
            id = this[TripAlbums.id].toString(),
            tripId = this[TripAlbums.tripId].toString(),
            url = this[TripAlbums.url],
            provider = this[TripAlbums.provider],
            title = this[TripAlbums.title],
            addedByMemberId = this[TripAlbums.addedByMemberId].toString(),
            addedByName = this[TripMembers.displayName] ?: getOrNull(Users.displayName).orEmpty(),
            createdAt = this[TripAlbums.createdAt].toTimestamp(),
        )

        /**
         * Google's image host serves a public Drive file at any width with `=w<px>` and needs no cookie or key.
         * Unverified until the spike with a real folder (docs/tasks/backend.md B20.1): if it fails on the web this is
         * the one place to change.
         */
        internal fun imageUrl(fileId: String, width: Int) = "https://lh3.googleusercontent.com/d/$fileId=w$width"

        private fun DriveImage.toDto(): AlbumItemDto {
            // a quarter turn swaps the sides the app should reserve
            val turned = rotation % 2 == 1
            return AlbumItemDto(
                id = id,
                name = name,
                width = if (turned) height else width,
                height = if (turned) width else height,
                thumbnailUrl = imageUrl(id, THUMBNAIL_WIDTH),
                imageUrl = imageUrl(id, FULL_WIDTH),
            )
        }

        private const val THUMBNAIL_WIDTH = 400
        private const val FULL_WIDTH = 2048
    }
}
