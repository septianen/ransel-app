package app.ransel.server.net

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkSafetyTest {
    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun privateAndSpecialAddressesAreNotPublic() {
        for (a in listOf(
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "224.0.0.1", "::1", "fe80::1", "fc00::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1",
        )) assertFalse(SafeAddress.isPublic(ip(a)), "$a must not be public")
    }

    @Test
    fun ordinaryPublicAddressesAreAllowed() {
        for (a in listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "2606:4700:4700::1111")) {
            assertTrue(SafeAddress.isPublic(ip(a)), "$a must be public")
        }
    }

    @Test
    fun literalHostsAreRecognisedWithoutDns() {
        assertTrue(SafeAddress.isLiteral("127.0.0.1"))
        assertTrue(SafeAddress.isLiteral("[::1]"))
        assertFalse(SafeAddress.isLiteral("example.com"))
        assertNull(SafeAddress.literal("example.com"))
    }

    private val noNetwork = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = when (hostname) {
            "internal.example.com" -> listOf(ip("10.0.0.5"))
            "mixed.example.com" -> listOf(ip("93.184.216.34"), ip("127.0.0.1"))
            else -> throw java.net.UnknownHostException(hostname)
        }
    }

    private fun refused(url: String) {
        val fetcher = SafeLinkFetcher(resolver = noNetwork)
        assertFailsWith<LinkUnreadable>(url) { runBlocking { fetcher.fetch(url) } }
    }

    @Test
    fun theFetcherRefusesAddressesThatLeadInside() {
        refused("ftp://example.com/file")
        refused("file:///etc/passwd")
        refused("http://user:pass@example.com/")
        refused("http://example.com:8080/")
        refused("http://127.0.0.1/")
        refused("http://[::1]/")
        refused("http://169.254.169.254/latest/meta-data/")
        refused("http://localhost/")
        refused("http://db.internal/")
        refused("http://internal.example.com/")
        refused("http://mixed.example.com/")
        refused("not a url")
    }

    @Test
    fun metadataComesFromOpenGraphThenJsonLd() {
        val html = """
            <html><head><title>Fallback</title>
            <meta property="og:title" content="Hotel &amp; Spa Kuta">
            <meta property="og:image" content="/img/cover.jpg">
            <meta property="og:site_name" content="Stays">
            <script type="application/ld+json">{"@type":"Hotel","name":"Other","address":{"streetAddress":"Jl. Kuta 1","addressLocality":"Kuta"},
            "aggregateRating":{"ratingValue":"4.6","reviewCount":120},"offers":{"price":"900000"}}</script>
            </head></html>
        """.trimIndent()
        val meta = LinkMetadata.parse(html, "https://stays.example.com/h/1")
        assertEquals("Hotel & Spa Kuta", meta.title)
        assertEquals("https://stays.example.com/img/cover.jpg", meta.imageUrl)
        assertEquals("Stays", meta.siteName)
        assertEquals(4.6, meta.ratingValue)
        assertEquals(120, meta.ratingCount)
        assertTrue(meta.address.orEmpty().contains("Jl. Kuta 1"))
    }

    @Test
    fun aPageWithNothingUsefulGivesNothing() {
        val meta = LinkMetadata.parse("<html><body>hi</body></html>", "https://example.com/")
        assertNull(meta.title)
        assertNull(meta.imageUrl)
    }
}
