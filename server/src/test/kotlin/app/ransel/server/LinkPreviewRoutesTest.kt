package app.ransel.server

import app.ransel.server.net.FetchedPage
import app.ransel.server.net.LinkFetcher
import app.ransel.server.net.LinkUnreadable
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeFetcher(var html: String = PAGE, var fail: Boolean = false) : LinkFetcher {
    val calls = AtomicInteger()
    override suspend fun fetch(url: String): FetchedPage {
        calls.incrementAndGet()
        if (fail) throw LinkUnreadable("down")
        return FetchedPage(url.substringBefore('#'), html)
    }
}

private const val PAGE = """<html><head><meta property="og:title" content="Villa Sunset"><meta property="og:image" content="https://img.example.com/v.jpg"></head></html>"""

class LinkPreviewRoutesTest {
    @Test
    fun returnsTitleAndPictureAndCachesTheAnswer() {
        val fetcher = FakeFetcher()
        apiTest(linkFetcher = fetcher) {
            val first = post("/link-preview", """{"url":"https://stays.example.com/villa?id=1#photos"}""")
            assertEquals(HttpStatusCode.OK, first.status)
            val data = first.data().jsonObject
            assertEquals("Villa Sunset", data.getValue("title").jsonPrimitive.content)
            assertEquals("https://img.example.com/v.jpg", data.getValue("imageUrl").jsonPrimitive.content)
            // same page, different fragment and host case: served from the cache
            val second = post("/link-preview", """{"url":"https://STAYS.example.com/villa?id=1"}""")
            assertEquals("Villa Sunset", second.data().jsonObject.getValue("title").jsonPrimitive.content)
            assertEquals(1, fetcher.calls.get())
        }
    }

    @Test
    fun unreadableLinksAre422() {
        val fetcher = FakeFetcher(fail = true)
        apiTest(linkFetcher = fetcher) {
            val r = post("/link-preview", """{"url":"https://stays.example.com/villa"}""")
            assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
            assertEquals("LINK_UNREADABLE", r.errorCode())
            fetcher.fail = false
            fetcher.html = "<html><body>nothing</body></html>"
            assertEquals("LINK_UNREADABLE", post("/link-preview", """{"url":"https://stays.example.com/empty"}""").errorCode())
        }
    }

    @Test
    fun emptyAndOversizedAddressesAreValidationErrors() = apiTest(linkFetcher = FakeFetcher()) {
        assertEquals("VALIDATION_FAILED", post("/link-preview", """{"url":" "}""").errorCode())
        assertEquals("VALIDATION_FAILED", post("/link-preview", """{"url":"https://a.example.com/${"x".repeat(2100)}"}""").errorCode())
    }

    @Test
    fun needsSignIn() = apiTest(linkFetcher = FakeFetcher()) {
        token = null
        assertEquals(HttpStatusCode.Unauthorized, post("/link-preview", """{"url":"https://a.example.com/"}""").status)
    }
}
