package app.ransel.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MemberIdsTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test fun sha256MatchesTheKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(sha256(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(sha256("abc".encodeToByteArray())))
        // 56 bytes: the padding needs a second block
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
    }

    @Test fun memberIdLooksLikeAUuid() {
        val id = memberIdFor("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")
        assertEquals(36, id.length)
        assertEquals('5', id[14])
        assertEquals(true, id[19] in "89ab")
    }

    @Test fun memberIdIsStableAndPerTripAndUser() {
        val trip = "11111111-1111-1111-1111-111111111111"
        val user = "22222222-2222-2222-2222-222222222222"
        assertEquals(memberIdFor(trip, user), memberIdFor(trip.uppercase(), user.uppercase()))
        assertNotEquals(memberIdFor(trip, user), memberIdFor(trip, LOCAL_USER_ID_FOR_TEST))
        assertNotEquals(memberIdFor(trip, user), memberIdFor("33333333-3333-3333-3333-333333333333", user))
    }

    private companion object {
        const val LOCAL_USER_ID_FOR_TEST = "00000000-0000-0000-0000-000000000001"
    }
}
