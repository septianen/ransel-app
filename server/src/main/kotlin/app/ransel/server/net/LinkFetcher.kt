package app.ransel.server.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** A page that was fetched: the address it ended at and its text. */
class FetchedPage(val finalUrl: String, val html: String)

/** The link cannot be shown: not a public web page, too big or too slow, or not a page at all. The message is for logs. */
class LinkUnreadable(message: String) : Exception(message)

/** Fetches the page behind a link a user pasted. An interface so tests need no network. */
interface LinkFetcher {
    suspend fun fetch(url: String): FetchedPage
}

/**
 * The real fetcher, with the SSRF rules of the TRD: only http and https on ports 80 and 443, no credentials in the
 * address, the host must resolve to public addresses only (checked when the connection is made, so a name that changes
 * its answer between check and connect does not help), at most 3 redirects with every hop checked again, 3 seconds in
 * all, at most 1 MB read, and only HTML.
 */
class SafeLinkFetcher(
    private val timeoutMillis: Long = 3_000,
    private val maxBytes: Int = 1_048_576,
    private val maxRedirects: Int = 3,
    /** Name resolution; tests replace it to run without a network. */
    private val resolver: Dns = Dns.SYSTEM,
) : LinkFetcher {
    private val client = OkHttpClient.Builder()
        .dns(PublicOnlyDns(resolver))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun fetch(url: String): FetchedPage = withContext(Dispatchers.IO) {
        var current = checked(url)
        repeat(maxRedirects + 1) {
            val request = Request.Builder().url(current).header("User-Agent", USER_AGENT).header("Accept", "text/html,application/xhtml+xml").get().build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: UnknownHostException) {
                throw LinkUnreadable("host is not public or does not exist")
            } catch (e: java.io.IOException) {
                throw LinkUnreadable("could not fetch: ${e.javaClass.simpleName}")
            }
            response.use { r ->
                if (r.code in REDIRECTS) {
                    val location = r.header("Location") ?: throw LinkUnreadable("redirect without a location")
                    current = checked(runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: throw LinkUnreadable("bad redirect"))
                    return@use
                }
                if (!r.isSuccessful) throw LinkUnreadable("HTTP ${r.code}")
                val type = r.header("Content-Type").orEmpty().lowercase()
                if (!type.contains("html") && !type.contains("xml")) throw LinkUnreadable("not a web page: $type")
                val body = r.body ?: throw LinkUnreadable("empty answer")
                val bytes = body.byteStream().use { it.readNBytes(maxBytes) }
                val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                return@withContext FetchedPage(current, String(bytes, charset))
            }
        }
        throw LinkUnreadable("too many redirects")
    }

    /** Syntax and literal-address checks; a host name is checked when the connection is made ([PublicOnlyDns]). */
    private fun checked(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: throw LinkUnreadable("not a valid address")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw LinkUnreadable("only http and https")
        if (uri.userInfo != null) throw LinkUnreadable("credentials in the address")
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: throw LinkUnreadable("no host")
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) throw LinkUnreadable("port not allowed")
        SafeAddress.literal(host)?.let { if (!SafeAddress.isPublic(it)) throw LinkUnreadable("address is not public") }
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", true) || host.endsWith(".internal", true) || host.endsWith(".local", true)) {
            throw LinkUnreadable("local host name")
        }
        return uri.toString()
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        const val USER_AGENT = "RanselLinkPreview/1.0 (+https://ransel.mietek.tech)"
    }
}

/** Resolves like [delegate], but refuses a name when any of its addresses is not public. */
class PublicOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = delegate.lookup(hostname)
        if (all.isEmpty() || all.any { !SafeAddress.isPublic(it) }) throw UnknownHostException("$hostname is not a public host")
        return all
    }
}
