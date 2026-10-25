package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MembersTest {
    @Test fun nameMustBeThereAndShort() {
        assertEquals(emptyMap(), validateMemberName("Rina"))
        assertEquals(mapOf("displayName" to "REQUIRED"), validateMemberName("   "))
        assertEquals(mapOf("displayName" to "TOO_LONG"), validateMemberName("a".repeat(MAX_MEMBER_NAME + 1)))
    }

    @Test fun commentBodyIsLimited() {
        assertEquals(emptyMap(), validateCommentBody("Setuju!"))
        assertEquals(mapOf("body" to "REQUIRED"), validateCommentBody(""))
        assertEquals(emptyMap(), validateCommentBody("a".repeat(MAX_COMMENT_LENGTH)))
        assertEquals(mapOf("body" to "TOO_LONG"), validateCommentBody("a".repeat(MAX_COMMENT_LENGTH + 1)))
    }

    @Test fun initialsTakeTwoLetters() {
        assertEquals("RP", memberInitials("Rina Putri"))
        assertEquals("R", memberInitials(" rina "))
        assertEquals("AB", memberInitials("Ayu Budi Cahya"))
    }

    @Test fun phonesBecomeDigitsWithCountryCode() {
        assertEquals("6281234567890", normalizePhone("0812-3456-7890"))
        assertEquals("6281234567890", normalizePhone("+62 812 3456 7890"))
        assertEquals("6281234567890", normalizePhone("6281234567890"))
        assertEquals("6281234567890", normalizePhone("81234567890"))
        assertEquals("14155550123", normalizePhone("+1 (415) 555-0123"))
        assertNull(normalizePhone("123"))
        assertNull(normalizePhone("abc"))
        assertNull(normalizePhone("7123456789")) // no country code and not a 0 / 62 / 8 start
        assertNull(normalizePhone("0812345678901234567"))
    }

    @Test fun whatsappLinkEncodesTheMessage() {
        assertEquals(
            "https://wa.me/6281234567890?text=Halo%20Rina%21%20https%3A%2F%2Fransel.mietek.tech%2Fj%2FBALI-7K2Q",
            whatsappLink("0812 3456 7890", "Halo Rina! https://ransel.mietek.tech/j/BALI-7K2Q"),
        )
        assertEquals("https://wa.me/6281234567890?text=%C3%A9", whatsappLink("081234567890", "é"))
        assertNull(whatsappLink(null, "x"))
        assertNull(whatsappLink("12", "x"))
    }

    private val members = listOf(
        MentionCandidate("m1", "Rina"),
        MentionCandidate("m2", "Rina Putri"),
        MentionCandidate("m3", "Budi"),
    )

    @Test fun longestNameWinsAndEachMemberOnce() {
        assertEquals(listOf("m2"), extractMentions("Tanya @Rina Putri ya", members))
        assertEquals(listOf("m1", "m3"), extractMentions("@rina dan @BUDI, setuju?", members))
        assertEquals(listOf("m3"), extractMentions("@Budi @budi @Budi", members))
    }

    @Test fun mentionNeedsABoundaryOnBothSides() {
        assertEquals(emptyList(), extractMentions("mail@Budi.com", members))
        assertEquals(emptyList(), extractMentions("@Budiman", members))
        assertEquals(listOf("m3"), extractMentions("(@Budi)", members))
        assertEquals(emptyList(), extractMentions("@Siapa", members))
        assertEquals(emptyList(), extractMentions("", members))
    }
}
