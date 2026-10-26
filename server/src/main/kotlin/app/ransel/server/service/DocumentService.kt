package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.TripDocuments
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.CreateDocumentRequest
import app.ransel.shared.dto.DOCUMENT_MIME_TYPES
import app.ransel.shared.dto.DocumentDownloadDto
import app.ransel.shared.dto.MAX_DOCUMENT_BYTES
import app.ransel.shared.dto.MAX_TRIP_DOCUMENT_BYTES
import app.ransel.shared.dto.PatchDocumentRequest
import app.ransel.shared.dto.TripDocumentDto
import app.ransel.shared.dto.TripDocumentsDto
import app.ransel.shared.model.DocumentCategory
import app.ransel.shared.model.DocumentKind
import app.ransel.shared.model.NotificationType
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.minutes

/**
 * Trip documents (PRD P2 "Trip documents"). Files live on the VPS disk under [AppConfig.documentsDir]/<trip>/<id>
 * (owner decision 26 Oct 2026: no object storage) and are fetched through a signed link valid 15 minutes, so the app
 * and the browser can download without a bearer header. Limits: 20 MB per file, 200 MB per trip.
 */
class DocumentService(private val config: AppConfig) {
    private val root: Path = Path.of(config.documentsDir)
    // a key of its own, derived from the photo secret, so a photo signature never opens a document
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("documents|${config.photoSigningSecret}".toByteArray()), "HmacSHA256")

    fun list(userId: UUID, tripId: UUID): TripDocumentsDto {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val items = live(tripId).orderBy(TripDocuments.createdAt to SortOrder.DESC).map { it.toDto() }
            TripDocumentsDto(items, usedBytes(tripId))
        }
    }

    /** A link (JSON). */
    fun createLink(userId: UUID, tripId: UUID, body: CreateDocumentRequest): TripDocumentDto {
        requireRole(tripId, userId, Role.EDITOR)
        val id = uuid(body.id, "id")
        val url = body.url?.trim().orEmpty()
        requireValid(validate(body.title) + if (!url.matches(Regex("^https?://\\S+$")) || url.length > 2000) mapOf("url" to "INVALID_URL") else emptyMap())
        return transaction {
            val item = itineraryItem(tripId, body.itineraryItemId)
            insert(id, tripId, callerMemberId(tripId, userId), body.title, body.category, DocumentKind.LINK, url = url, item = item)
            added(tripId, userId, id)
            load(id)
        }
    }

    /** A file (multipart). [declaredMime] may be generic; the file name's extension decides then. */
    fun createFile(userId: UUID, tripId: UUID, meta: CreateDocumentRequest, fileName: String, declaredMime: String?, bytes: ByteArray): TripDocumentDto {
        requireRole(tripId, userId, Role.EDITOR)
        val id = uuid(meta.id, "id")
        requireValid(validate(meta.title))
        if (bytes.size > MAX_DOCUMENT_BYTES) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.FILE_TOO_LARGE, "Files are at most 20 MB", mapOf("maxBytes" to MAX_DOCUMENT_BYTES.toString()))
        }
        if (bytes.isEmpty()) requireValid(mapOf("file" to "REQUIRED"))
        val mime = mimeOf(declaredMime, fileName)
            ?: throw ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCode.UNSUPPORTED_FILE, "Only PDF and images can be added")
        val target = fileOf(tripId, id)
        return transaction {
            if (TripDocuments.select(TripDocuments.id).where { TripDocuments.id eq id }.any()) throw app.ransel.server.plugins.conflict("Document id already exists")
            // the trip row is locked so two uploads cannot both squeeze under the limit
            Trips.selectAll().where { Trips.id eq tripId }.forUpdate().single()
            val used = usedBytes(tripId)
            if (used + bytes.size > MAX_TRIP_DOCUMENT_BYTES) {
                throw ApiException(
                    HttpStatusCode.PayloadTooLarge, ErrorCode.STORAGE_FULL, "This trip has no room for more files",
                    mapOf("usedBytes" to used.toString(), "limitBytes" to MAX_TRIP_DOCUMENT_BYTES.toString()),
                )
            }
            val item = itineraryItem(tripId, meta.itineraryItemId)
            write(target, bytes)
            insert(
                id, tripId, callerMemberId(tripId, userId), meta.title, meta.category, DocumentKind.FILE,
                fileName = cleanName(fileName), mime = mime, size = bytes.size.toLong(), item = item,
            )
            added(tripId, userId, id)
            load(id)
        }
    }

    fun patch(userId: UUID, tripId: UUID, docId: UUID, body: PatchDocumentRequest): TripDocumentDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        body.title?.let { requireValid(validate(it)) }
        return transaction {
            val row = row(tripId, docId)
            if (row[TripDocuments.uploadedByMemberId] != callerMemberId(tripId, userId) && !role.atLeast(Role.EDITOR)) throw forbidden()
            val item = if (body.unlinkItinerary) null else itineraryItem(tripId, body.itineraryItemId)
            TripDocuments.update({ TripDocuments.id eq docId }) {
                body.title?.let { v -> it[title] = v.trim() }
                body.category?.let { v -> it[category] = v }
                if (body.unlinkItinerary) it[itineraryItemId] = null else if (item != null) it[itineraryItemId] = item
            }
            load(docId)
        }
    }

    /** The uploader or the OWNER. The file leaves the disk at once. */
    fun delete(userId: UUID, tripId: UUID, docId: UUID) {
        val role = requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val row = row(tripId, docId)
            if (row[TripDocuments.uploadedByMemberId] != callerMemberId(tripId, userId) && role != Role.OWNER) throw forbidden()
            val now = Instant.now()
            TripDocuments.update({ TripDocuments.id eq docId }) {
                it[deletedAt] = now
                it[fileRemovedAt] = now
            }
            Files.deleteIfExists(fileOf(tripId, docId))
        }
    }

    /** A link that works for 15 minutes without a bearer (path relative to the API base, like place photos). */
    fun download(userId: UUID, tripId: UUID, docId: UUID): DocumentDownloadDto {
        requireRole(tripId, userId, Role.VIEWER)
        val row = transaction { row(tripId, docId) }
        val exp = Instant.now().plusSeconds(15.minutes.inWholeSeconds)
        if (row[TripDocuments.kind] == DocumentKind.LINK) return DocumentDownloadDto(row[TripDocuments.url]!!, exp.toTimestamp())
        val sig = sign(docId, exp.epochSecond)
        return DocumentDownloadDto("${ApiRoutes.documentFile(docId.toString())}?exp=${exp.epochSecond}&sig=$sig", exp.toTimestamp())
    }

    /** A file to send: its path, type and name, or null when the link is wrong, expired or the document gone. */
    fun file(docId: UUID, exp: Long?, sig: String?): Triple<Path, String, String>? {
        if (exp == null || sig.isNullOrEmpty() || exp < Instant.now().epochSecond) return null
        val given = runCatching { Base64.getUrlDecoder().decode(sig) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(given, mac(docId, exp))) return null
        val row = transaction {
            TripDocuments.join(Trips, JoinType.INNER, TripDocuments.tripId, Trips.id).selectAll()
                .where { (TripDocuments.id eq docId) and TripDocuments.deletedAt.isNull() and Trips.deletedAt.isNull() }
                .singleOrNull()
        } ?: return null
        val path = fileOf(row[TripDocuments.tripId], docId)
        if (row[TripDocuments.kind] != DocumentKind.FILE || !Files.exists(path)) return null
        return Triple(path, row[TripDocuments.mime]!!, row[TripDocuments.fileName] ?: "document")
    }

    /** Daily: files of deleted documents and deleted trips leave the disk (owner decision: kept while the trip exists). */
    fun purge(): Int = transaction {
        val gone = TripDocuments.join(Trips, JoinType.INNER, TripDocuments.tripId, Trips.id)
            .select(TripDocuments.id, TripDocuments.tripId)
            .where { TripDocuments.fileRemovedAt.isNull() and (TripDocuments.deletedAt.isNotNull() or Trips.deletedAt.isNotNull()) }
            .map { it[TripDocuments.id] to it[TripDocuments.tripId] }
        gone.forEach { (id, trip) ->
            Files.deleteIfExists(fileOf(trip, id))
            TripDocuments.update({ TripDocuments.id eq id }) { it[fileRemovedAt] = Instant.now() }
        }
        gone.size
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun live(tripId: UUID) = TripDocuments.join(TripMembers, JoinType.INNER, TripDocuments.uploadedByMemberId, TripMembers.id)
        .join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
        .selectAll()
        .where { (TripDocuments.tripId eq tripId) and TripDocuments.deletedAt.isNull() }

    private fun load(id: UUID): TripDocumentDto =
        TripDocuments.join(TripMembers, JoinType.INNER, TripDocuments.uploadedByMemberId, TripMembers.id)
            .join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll().where { TripDocuments.id eq id }.single().toDto()

    private fun row(tripId: UUID, docId: UUID): ResultRow =
        TripDocuments.selectAll()
            .where { (TripDocuments.id eq docId) and (TripDocuments.tripId eq tripId) and TripDocuments.deletedAt.isNull() }
            .singleOrNull() ?: throw notFound("Document")

    private fun ResultRow.toDto() = TripDocumentDto(
        id = this[TripDocuments.id].toString(),
        tripId = this[TripDocuments.tripId].toString(),
        title = this[TripDocuments.title],
        category = this[TripDocuments.category],
        kind = this[TripDocuments.kind],
        url = this[TripDocuments.url],
        fileName = this[TripDocuments.fileName],
        mime = this[TripDocuments.mime],
        sizeBytes = this[TripDocuments.sizeBytes],
        itineraryItemId = this[TripDocuments.itineraryItemId]?.toString(),
        uploadedByMemberId = this[TripDocuments.uploadedByMemberId].toString(),
        uploadedByName = this[TripMembers.displayName] ?: getOrNull(Users.displayName).orEmpty(),
        createdAt = this[TripDocuments.createdAt].toTimestamp(),
    )

    private fun usedBytes(tripId: UUID): Long =
        TripDocuments.select(TripDocuments.sizeBytes.sum())
            .where { (TripDocuments.tripId eq tripId) and TripDocuments.deletedAt.isNull() and (TripDocuments.kind eq DocumentKind.FILE) }
            .single()[TripDocuments.sizeBytes.sum()] ?: 0L

    private fun insert(
        id: UUID, tripId: UUID, memberId: UUID, title: String, category: DocumentCategory, kind: DocumentKind,
        url: String? = null, fileName: String? = null, mime: String? = null, size: Long? = null, item: UUID?,
    ) {
        TripDocuments.insert {
            it[TripDocuments.id] = id
            it[TripDocuments.tripId] = tripId
            it[TripDocuments.title] = title.trim()
            it[TripDocuments.category] = category
            it[TripDocuments.kind] = kind
            it[TripDocuments.url] = url
            it[TripDocuments.fileName] = fileName
            it[TripDocuments.mime] = mime
            it[sizeBytes] = size
            it[itineraryItemId] = item
            it[uploadedByMemberId] = memberId
        }
    }

    /** An activity of this trip, or a validation error. */
    private fun itineraryItem(tripId: UUID, raw: String?): UUID? {
        raw ?: return null
        val id = runCatching { UUID.fromString(raw) }.getOrNull()
        val ok = id != null && ItineraryItems.select(ItineraryItems.id)
            .where { (ItineraryItems.id eq id) and (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }.any()
        requireValid(if (ok) emptyMap() else mapOf("itineraryItemId" to "NOT_IN_TRIP"))
        return id
    }

    /** "Raka menambah dokumen": everyone with an account but the uploader. */
    private fun added(tripId: UUID, userId: UUID, docId: UUID) {
        val title = Trips.select(Trips.title).where { Trips.id eq tripId }.single()[Trips.title]
        val doc = TripDocuments.select(TripDocuments.title).where { TripDocuments.id eq docId }.single()[TripDocuments.title]
        val actor = Users.select(Users.displayName).where { Users.id eq userId }.singleOrNull()?.get(Users.displayName).orEmpty()
        val audience = activeMemberUserIds(tripId).filter { it != userId }
        Notify.emit {
            notify(audience, NotificationType.DOCUMENT_ADDED, tripId, "document:$docId") {
                mapOf("tripTitle" to title, "actorName" to actor, "title" to doc, "documentId" to docId.toString())
            }
        }
    }

    private fun write(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".upload-", ".tmp")
        Files.write(temp, bytes)
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun fileOf(tripId: UUID, docId: UUID): Path = root.resolve(tripId.toString()).resolve(docId.toString())

    private fun mac(docId: UUID, exp: Long): ByteArray = Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("$docId|$exp".toByteArray())

    private fun sign(docId: UUID, exp: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString(mac(docId, exp))

    private fun validate(title: String): Map<String, String> = when {
        title.isBlank() -> mapOf("title" to "REQUIRED")
        title.trim().length > 80 -> mapOf("title" to "TOO_LONG")
        else -> emptyMap()
    }

    private fun uuid(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw app.ransel.server.plugins.validationFailed(mapOf(field to "INVALID_UUID"))

    companion object {
        private val BY_EXTENSION = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "heic" to "image/heic")

        /** PDF or an image, from the declared type or (when that is generic) the extension; null = refused. */
        internal fun mimeOf(declared: String?, fileName: String): String? {
            val clean = declared?.substringBefore(';')?.trim()?.lowercase()
            if (clean != null && clean in DOCUMENT_MIME_TYPES) return clean
            return BY_EXTENSION[fileName.substringAfterLast('.', "").lowercase()]
        }

        /** The file name shown and offered on download: no path, no control characters, at most 120 characters. */
        /** A STAY poll winner's booking link becomes a "Menginap" document. Call inside the decide transaction. */
        internal fun addPollLink(tripId: UUID, memberId: UUID, title: String, url: String) {
            if (!url.matches(Regex("^https?://\\S+$"))) return
            TripDocuments.insert {
                it[id] = UUID.randomUUID()
                it[TripDocuments.tripId] = tripId
                it[TripDocuments.title] = title.trim().take(80).ifBlank { "Booking" }
                it[category] = DocumentCategory.STAY
                it[kind] = DocumentKind.LINK
                it[TripDocuments.url] = url
                it[uploadedByMemberId] = memberId
            }
        }

        internal fun cleanName(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\').filter { it >= ' ' && it != '"' }.trim().take(120).ifBlank { "document" }
    }
}
