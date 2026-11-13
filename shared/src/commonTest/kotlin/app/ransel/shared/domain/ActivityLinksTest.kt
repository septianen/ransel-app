package app.ransel.shared.domain

import app.ransel.shared.dto.ItineraryItemDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ActivityLinksTest {
    @Test
    fun aWebAddressIsKeptAndOneWithoutASchemeBecomesHttps() {
        assertEquals("https://www.booking.com/hotel/id/x.html?aid=1", normalizeActivityLink("  https://www.booking.com/hotel/id/x.html?aid=1 "))
        assertEquals("http://example.com", normalizeActivityLink("http://example.com"))
        assertEquals("https://booking.com/hotel", normalizeActivityLink("booking.com/hotel"))
        assertEquals("https://instagram.com/bajo.dive", normalizeActivityLink("instagram.com/bajo.dive"))
        assertEquals("https://example.com:8080/a", normalizeActivityLink("example.com:8080/a"), "host:port is not a scheme")
        assertEquals("https://Example.com/Path", normalizeActivityLink("HTTPS://Example.com/Path"), "only the scheme is lowercased")
    }

    @Test
    fun whatIsNotAWebAddressIsRefused() {
        assertNull(normalizeActivityLink(""))
        assertNull(normalizeActivityLink("   "))
        assertNull(normalizeActivityLink("javascript:alert(1)"))
        assertNull(normalizeActivityLink("file:///etc/passwd"))
        assertNull(normalizeActivityLink("ftp://example.com"))
        assertNull(normalizeActivityLink("mailto:a@b.com"))
        assertNull(normalizeActivityLink("tel:+62812"))
        assertNull(normalizeActivityLink("tanya ibu dulu"), "free text with spaces")
        assertNull(normalizeActivityLink("hotel"), "no dot: not a host")
        assertNull(normalizeActivityLink("https://"))
        assertNull(normalizeActivityLink("https://bad..com"))
        assertNull(normalizeActivityLink("https:/example.com"), "one slash is not a web address")
        assertNull(normalizeActivityLink("https://example.com/" + "a".repeat(MAX_ACTIVITY_LINK_LENGTH)))
    }

    @Test
    fun theLabelComesFromTheDomain() {
        assertEquals("Instagram", activityLinkLabel("https://www.instagram.com/p/abc"))
        assertEquals("Instagram", activityLinkLabel("https://instagram.com/x"))
        assertEquals("Booking.com", activityLinkLabel("https://www.booking.com/hotel/x"))
        assertEquals("Booking.com", activityLinkLabel("https://secure.booking.com/x"))
        assertEquals("Google Maps", activityLinkLabel("https://maps.app.goo.gl/abc123"))
        assertEquals("Google Maps", activityLinkLabel("https://www.google.com/maps/place/Kuta"))
        assertEquals("WhatsApp", activityLinkLabel("https://wa.me/62812"))
        assertEquals("tiket.com", activityLinkLabel("https://www.tiket.com/hotel"))
        assertEquals("example.com", activityLinkLabel("https://user:pw@example.com:8080/x"))
        // a long domain is cut with an ellipsis at 28 characters
        val long = activityLinkLabel("https://a-really-long-domain-name-for-a-villa.example.com/x")
        assertEquals(ACTIVITY_LINK_LABEL_MAX, long.length)
        assertEquals('…', long.last())
        // google.com that is not Maps keeps its domain
        assertEquals("google.com", activityLinkLabel("https://www.google.com/search?q=x"))
    }

    @Test
    fun cleaningKeepsOrderDropsBadAndDuplicatesAndStopsAtFive() {
        val cleaned = cleanActivityLinks(listOf("booking.com/a", "nonsense", "https://booking.com/a", "instagram.com/x", ""))
        assertEquals(listOf("https://booking.com/a", "https://instagram.com/x"), cleaned)
        val many = (1..8).map { "site$it.com" }
        assertEquals(MAX_ACTIVITY_LINKS, cleanActivityLinks(many).size)
        assertEquals("https://site1.com", cleanActivityLinks(many).first())
    }

    @Test
    fun validationNamesTheRowsAndTheCountButEmptyRowsAndDuplicatesAreFine() {
        assertEquals(emptyMap(), validateActivityLinks(listOf("booking.com/a", "", "booking.com/a")))
        assertEquals(mapOf("links[1]" to "INVALID"), validateActivityLinks(listOf("booking.com/a", "javascript:x")))
        assertEquals(mapOf("links" to "TOO_MANY"), validateActivityLinks((1..6).map { "site$it.com" }))
        assertEquals(emptyMap(), validateActivityLinks((1..5).map { "site$it.com" } + listOf("", "  ")), "empty rows do not count")
    }

    @Test
    fun anItemFromAnOldAppHasItsBookingLinkAsTheOnlyLink() {
        val stamp = Instant.parse("2026-11-13T09:00:00Z")
        fun item(booking: String?, links: List<String>?) = ItineraryItemDto(
            id = "a", tripId = "t", title = "x", bookingUrl = booking, links = links, createdAt = stamp, updatedAt = stamp,
        )
        assertEquals(listOf("https://booking.com/a"), item("booking.com/a", null).activityLinks())
        assertEquals(emptyList(), item(null, null).activityLinks())
        assertEquals(emptyList(), item("not a link", null).activityLinks())
        assertEquals(listOf("https://a.com", "https://b.com"), item("https://a.com", listOf("https://a.com", "https://b.com")).activityLinks(), "a list wins")
        assertEquals(emptyList(), item("https://a.com", emptyList()).activityLinks(), "an empty list is a list: no links")
    }
}
