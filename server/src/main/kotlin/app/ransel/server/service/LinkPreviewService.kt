package app.ransel.server.service

import app.ransel.server.db.LinkPreviews
import app.ransel.server.net.LinkFetcher
import app.ransel.server.net.LinkMetadata
import app.ransel.server.net.LinkUnreadable
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.LinkPreviewDto
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * "Tempel link" for poll options: the title and picture of the page, so nobody retypes the name of a hotel. Fetching is
 * done by [fetcher] (SSRF-safe); the answer is cached 7 days per normalised address. Prices are never read.
 */
class LinkPreviewService(private val fetcher: LinkFetcher) {
    private val log = LoggerFactory.getLogger(LinkPreviewService::class.java)

    suspend fun preview(rawUrl: String): LinkPreviewDto {
        val url = rawUrl.trim()
        if (url.isEmpty() || url.length > MAX_URL) throw validationFailed(mapOf("url" to if (url.isEmpty()) "REQUIRED" else "TOO_LONG"))
        val key = hash(normalize(url) ?: throw unreadable("not a valid address"))
        cached(key)?.let { return it }

        val page = try {
            fetcher.fetch(url)
        } catch (e: LinkUnreadable) {
            log.info("link.unreadable reason={}", e.message)
            throw unreadable(e.message.orEmpty())
        }
        val meta = LinkMetadata.parse(page.html, page.finalUrl)
        if (meta.title == null && meta.imageUrl == null) throw unreadable("the page says nothing we can show")
        val dto = LinkPreviewDto(
            url = page.finalUrl.substringBefore('#'),
            title = meta.title, imageUrl = meta.imageUrl, siteName = meta.siteName,
            address = meta.address, ratingValue = meta.ratingValue, ratingCount = meta.ratingCount,
        )
        transaction {
            LinkPreviews.upsert {
                it[urlHash] = key
                it[LinkPreviews.url] = url
                it[finalUrl] = dto.url
                it[title] = dto.title
                it[imageUrl] = dto.imageUrl
                it[siteName] = dto.siteName
                it[address] = dto.address
                it[ratingValue] = dto.ratingValue
                it[ratingCount] = dto.ratingCount
                it[fetchedAt] = Instant.now()
            }
        }
        return dto
    }

    fun purge() = transaction { LinkPreviews.deleteWhere { fetchedAt less Instant.now().minus(TTL) } }

    private fun cached(key: String): LinkPreviewDto? = transaction {
        LinkPreviews.selectAll()
            .where { (LinkPreviews.urlHash eq key) and (LinkPreviews.fetchedAt greater Instant.now().minus(TTL)) }
            .singleOrNull()
    }?.let {
        LinkPreviewDto(
            url = it[LinkPreviews.finalUrl], title = it[LinkPreviews.title], imageUrl = it[LinkPreviews.imageUrl],
            siteName = it[LinkPreviews.siteName], address = it[LinkPreviews.address],
            ratingValue = it[LinkPreviews.ratingValue], ratingCount = it[LinkPreviews.ratingCount],
        )
    }

    private fun unreadable(why: String) = ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.LINK_UNREADABLE, "This link cannot be shown")
        .also { log.debug("link.unreadable {}", why) }

    internal companion object {
        const val MAX_URL = 2000
        val TTL: Duration = Duration.ofDays(7)

        /** Lower-case scheme and host, no fragment, no trailing slash on the root: the same page asked twice hits the cache. */
        fun normalize(raw: String): String? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val port = if (uri.port == -1 || (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)) "" else ":${uri.port}"
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            return "$scheme://$host$port$path$query"
        }

        fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
