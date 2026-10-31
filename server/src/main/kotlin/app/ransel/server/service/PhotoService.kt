package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.tripNotFound
import app.ransel.shared.api.ApiRoutes
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.PHOTO_JPEG_QUALITY
import app.ransel.shared.domain.PHOTO_MAX_BYTES
import app.ransel.shared.domain.PHOTO_MAX_INPUT_SIDE
import app.ransel.shared.domain.PhotoKind
import app.ransel.shared.domain.fitWithin
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.time.Duration.Companion.hours

/**
 * Trip covers and profile photos (B19.1). The apps send a JPEG already shrunk to its [PhotoKind]; the server decodes
 * it and writes a new JPEG (never the bytes it got), so EXIF such as the GPS position is gone and only real images are
 * stored. Each upload gets a random id; `GET /files/images/{id}` is public, the id being the secret, like a share link.
 */
class PhotoService(config: AppConfig) {
    private val root: Path = Path.of(config.imagesDir)

    fun setCover(userId: UUID, tripId: UUID, contentType: String?, bytes: ByteArray): TripDto {
        val role = requireRole(tripId, userId, Role.EDITOR)
        val url = store(contentType, bytes, PhotoKind.COVER)
        val old = transaction {
            val old = Trips.select(Trips.coverUrl).where { (Trips.id eq tripId) and Trips.deletedAt.isNull() }.singleOrNull()
                ?: throw tripNotFound()
            Trips.update({ Trips.id eq tripId }) { it[coverUrl] = url } // the trigger bumps updated_at, so sync carries it
            old[Trips.coverUrl]
        }
        remove(old)
        return transaction { TripService.load(tripId, role) }
    }

    fun clearCover(userId: UUID, tripId: UUID): TripDto {
        val role = requireRole(tripId, userId, Role.EDITOR)
        val old = transaction {
            val old = Trips.select(Trips.coverUrl).where { Trips.id eq tripId }.single()[Trips.coverUrl]
            if (old != null) Trips.update({ Trips.id eq tripId }) { it[coverUrl] = null }
            old
        }
        remove(old)
        return transaction { TripService.load(tripId, role) }
    }

    fun setAvatar(userId: UUID, contentType: String?, bytes: ByteArray): MeDto {
        val url = store(contentType, bytes, PhotoKind.AVATAR)
        return replaceAvatar(userId, url)
    }

    fun clearAvatar(userId: UUID): MeDto = replaceAvatar(userId, null)

    /** The file for `GET /files/images/{id}`, or null when there is none. */
    fun file(imageId: String): Path? {
        val id = runCatching { UUID.fromString(imageId) }.getOrNull() ?: return null
        return fileOf(id).takeIf { Files.isRegularFile(it) }
    }

    /**
     * Hourly: files no live trip or account points at leave the disk (deleted trips and accounts, uploads whose
     * request failed after the write). Files younger than an hour stay, so an upload in progress is never touched.
     */
    fun purge(now: Instant = Instant.now()): Int {
        if (!Files.isDirectory(root)) return 0
        val used = transaction {
            val covers = Trips.select(Trips.coverUrl).where { Trips.coverUrl.isNotNull() and Trips.deletedAt.isNull() }.map { it[Trips.coverUrl] }
            val avatars = Users.select(Users.avatarUrl).where { Users.avatarUrl.isNotNull() and Users.deletedAt.isNull() }.map { it[Users.avatarUrl] }
            (covers + avatars).mapNotNull { idOf(it) }.toSet()
        }
        val cutoff = now.minusSeconds(1.hours.inWholeSeconds)
        var removed = 0
        Files.list(root).use { files ->
            files.filter { it.fileName.toString().endsWith(".jpg") }.forEach { path ->
                val id = runCatching { UUID.fromString(path.fileName.toString().removeSuffix(".jpg")) }.getOrNull()
                val young = Files.getLastModifiedTime(path).toInstant().isAfter(cutoff)
                if (id != null && id !in used && !young && Files.deleteIfExists(path)) removed++
            }
        }
        return removed
    }

    private fun replaceAvatar(userId: UUID, url: String?): MeDto {
        val old = transaction {
            val row = Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.singleOrNull()
                ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "Account not found")
            Users.update({ Users.id eq userId }) { it[avatarUrl] = url }
            // the members list in sync only sends rows changed since the last pull
            TripMembers.update({ TripMembers.userId eq userId }) { it[updatedAt] = Instant.now() }
            row[Users.avatarUrl]
        }
        if (old != url) remove(old)
        return MeService.get(userId)
    }

    /** Checks, shrinks and writes the photo; returns its public path. */
    private fun store(contentType: String?, bytes: ByteArray, kind: PhotoKind): String {
        if (bytes.size > PHOTO_MAX_BYTES) {
            throw ApiException(
                HttpStatusCode.PayloadTooLarge, ErrorCode.FILE_TOO_LARGE, "Photos are at most 1 MB",
                mapOf("maxBytes" to PHOTO_MAX_BYTES.toString()),
            )
        }
        if (contentType?.lowercase() != "image/jpeg") throw unsupported()
        val jpeg = encode(scaled(decode(bytes), kind.maxSide))
        val id = UUID.randomUUID()
        Files.createDirectories(root)
        val temp = Files.createTempFile(root, "upload-", ".tmp")
        try {
            Files.write(temp, jpeg)
            Files.move(temp, fileOf(id), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        return ApiRoutes.imageFile(id.toString())
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: throw unsupported()
        input.use {
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull { r -> r.formatName.equals("jpeg", true) }
                ?: throw unsupported()
            try {
                reader.input = input
                // the header is enough to refuse a huge image before its pixels are in memory
                val (w, h) = runCatching { reader.getWidth(0) to reader.getHeight(0) }.getOrNull() ?: throw unsupported()
                if (w <= 0 || h <= 0 || w > PHOTO_MAX_INPUT_SIDE || h > PHOTO_MAX_INPUT_SIDE) throw unsupported()
                return runCatching { reader.read(0) }.getOrNull() ?: throw unsupported()
            } finally {
                reader.dispose()
            }
        }
    }

    private fun scaled(image: BufferedImage, maxSide: Int): BufferedImage {
        val (w, h) = fitWithin(image.width, image.height, maxSide)
        // always drawn again as plain RGB: CMYK or grey JPEGs come out as an ordinary photo
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(image, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun encode(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = PHOTO_JPEG_QUALITY / 100f
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    private fun remove(url: String?) {
        idOf(url)?.let { runCatching { Files.deleteIfExists(fileOf(it)) } }
    }

    private fun fileOf(id: UUID): Path = root.resolve("$id.jpg")

    private fun idOf(url: String?): UUID? =
        url?.substringAfterLast('/')?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun unsupported() =
        ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCode.UNSUPPORTED_FILE, "Photos must be JPEG images")
}
