package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String) = str(key).toLong()

private class Trio(val tripId: String, val a: Session, val b: Session, val c: Session)

/** Owner A, plus B and C joined as EDITOR. Ends signed in as A. */
private suspend fun Api.trio(): Trio {
    val a = session()
    val tripId = UUID.randomUUID().toString()
    post("/trips", tripJson(tripId))
    val others = mutableListOf<Session>()
    repeat(2) {
        use(a)
        val code = post("/trips/$tripId/invites", """{"role":"EDITOR","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        others += session()
    }
    use(a)
    return Trio(tripId, a, others[0], others[1])
}

private fun json(pairs: Map<String, Long>) = pairs.entries.joinToString(",", "[", "]") { """{"memberId":"${it.key}","amountMinor":${it.value}}""" }
private fun shares(pairs: Map<String, Long>) = pairs.entries.joinToString(",", "[", "]") { """{"memberId":"${it.key}","shareMinor":${it.value}}""" }

private fun expense(
    id: String = UUID.randomUUID().toString(),
    amount: Long,
    payers: Map<String, Long>,
    splits: Map<String, Long>,
    paidBy: String? = null,
): String {
    val paid = paidBy?.let { ""","paidByMemberId":"$it"""" }.orEmpty()
    return """{"id":"$id","amountMinor":$amount,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z"$paid,"payers":${json(payers)},"splits":${shares(splits)}}"""
}

class MultiPayerRoutesTest {
    @Test
    fun severalPayersAreStoredReadBackAndCreditedInTheBalances() = apiTest {
        val g = trio()
        val id = UUID.randomUUID().toString()
        // 300.000 paid by A (200.000) and B (100.000), shared equally between the three
        val created = post(
            "/trips/${g.tripId}/expenses",
            expense(id, 300_000, mapOf(g.b.userId to 100_000, g.a.userId to 200_000), mapOf(g.a.userId to 100_000, g.b.userId to 100_000, g.c.userId to 100_000)),
        )
        assertEquals(HttpStatusCode.Created, created.status)
        val body = created.data().jsonObject
        val payers = body.getValue("payers").jsonArray.map { it.jsonObject }
        assertEquals(listOf(g.b.userId, g.a.userId), payers.map { it.str("userId") }, "the first one sent is who paid; the rest follow")
        assertEquals(g.b.userId, body.str("paidByMemberId"))

        val listed = get("/trips/${g.tripId}/expenses").data().jsonArray.single().jsonObject
        assertEquals(2, listed.getValue("payers").jsonArray.size)

        val balances = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("members").jsonArray
            .map { it.jsonObject }.associateBy { it.str("memberId") }
        assertEquals(200_000L, balances.getValue(g.a.userId).long("paidMinor"))
        assertEquals(100_000L, balances.getValue(g.b.userId).long("paidMinor"))
        assertEquals(100_000L, balances.getValue(g.a.userId).long("balanceMinor"))
        assertEquals(0L, balances.getValue(g.b.userId).long("balanceMinor"))
        assertEquals(-100_000L, balances.getValue(g.c.userId).long("balanceMinor"))
    }

    @Test
    fun payerAmountsMustAddUpToTheExpense() = apiTest {
        val g = trio()
        val response = post(
            "/trips/${g.tripId}/expenses",
            expense(amount = 300_000, payers = mapOf(g.a.userId to 200_000, g.b.userId to 50_000), splits = emptyMap()),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("PAYER_TOTAL_MISMATCH", response.errorCode())
    }

    @Test
    fun payersMustBeDifferentMembersWithAnAmount() = apiTest {
        val g = trio()
        val stranger = UUID.randomUUID().toString()

        val notMember = post("/trips/${g.tripId}/expenses", expense(amount = 100, payers = mapOf(g.a.userId to 60, stranger to 40), splits = emptyMap()))
        assertEquals(HttpStatusCode.UnprocessableEntity, notMember.status)
        assertEquals("NOT_A_MEMBER", notMember.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("payers"))

        val single = post("/trips/${g.tripId}/expenses", expense(amount = 100, payers = mapOf(g.a.userId to 100), splits = emptyMap()))
        assertEquals("NEEDS_AT_LEAST_TWO", single.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("payers"))

        val zero = post("/trips/${g.tripId}/expenses", expense(amount = 100, payers = mapOf(g.a.userId to 100, g.b.userId to 0), splits = emptyMap()))
        assertEquals("MUST_BE_POSITIVE", zero.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("payers"))
    }

    @Test
    fun paidByMustBeTheFirstPayerWhenBothAreSent() = apiTest {
        val g = trio()
        val response = post(
            "/trips/${g.tripId}/expenses",
            expense(amount = 100, payers = mapOf(g.a.userId to 60, g.b.userId to 40), splits = emptyMap(), paidBy = g.b.userId),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("MUST_BE_FIRST_PAYER", response.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("paidByMemberId"))
    }

    @Test
    fun editingBackToOnePayerRemovesThePayerRows() = apiTest {
        val g = trio()
        val id = UUID.randomUUID().toString()
        post("/trips/${g.tripId}/expenses", expense(id, 100, mapOf(g.a.userId to 60, g.b.userId to 40), emptyMap()))

        val single = """{"id":"$id","amountMinor":100,"currency":"IDR","spentAt":"2026-11-20T05:00:00Z","paidByMemberId":"${g.a.userId}"}"""
        val replaced = put("/trips/${g.tripId}/expenses/$id", single)
        assertEquals(HttpStatusCode.OK, replaced.status)
        assertEquals(0, replaced.data().jsonObject.getValue("payers").jsonArray.size)
    }
}
