package app.ransel.data

import app.ransel.shared.domain.memberIdFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemberIdJsonTest {
    private val trip = "11111111-1111-1111-1111-111111111111"
    private val user = "22222222-2222-2222-2222-222222222222"

    @Test
    fun splitsAndPayersMoveFromUserIdsToMemberIds() {
        val member = memberIdFor(trip, user)
        assertEquals(
            """[{"memberId":"$member","shareMinor":50000}]""",
            userIdsToMemberIds("""[{"userId":"$user","shareMinor":50000}]""", trip),
        )
        assertEquals(
            """[{"memberId":"$member","amountMinor":200000}]""",
            userIdsToMemberIds("""[{"userId":"$user","amountMinor":200000}]""", trip),
        )
    }

    @Test
    fun nullStaysNullAndUnreadableTextStaysAsItWas() {
        assertNull(userIdsToMemberIds(null, trip))
        assertEquals("not json", userIdsToMemberIds("not json", trip))
        assertEquals("""["x"]""", userIdsToMemberIds("""["x"]""", trip))
        assertEquals("[]", userIdsToMemberIds("[]", trip))
    }

    @Test
    fun aRowThatAlreadyHasMemberIdsIsLeftAlone() {
        val json = """[{"memberId":"m1","shareMinor":1}]"""
        assertEquals(json, userIdsToMemberIds(json, trip))
    }
}
