package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.content.toLong()
private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private class Group(val tripId: String, val a: Session, val b: Session, val c: Session)

/** Owner A, plus B and C joined as EDITOR. Ends signed in as A. */
private suspend fun Api.group(role: String = "EDITOR"): Group {
    val a = session()
    val tripId = UUID.randomUUID().toString()
    post("/trips", tripJson(tripId))
    val sessions = mutableListOf<Session>()
    repeat(2) {
        use(a)
        val code = post("/trips/$tripId/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""")
            .data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        sessions += session()
    }
    use(a)
    return Group(tripId, a, sessions[0], sessions[1])
}

private fun expense(
    id: String = UUID.randomUUID().toString(),
    amount: Long,
    paidBy: String,
    shares: Map<String, Long>,
    currency: String = "IDR",
    fx: String? = null,
): String {
    val splits = shares.entries.joinToString(",", "[", "]") { """{"userId":"${it.key}","shareMinor":${it.value}}""" }
    val rate = if (fx != null) ""","fxRateToBase":"$fx"""" else ""
    return """{"id":"$id","amountMinor":$amount,"currency":"$currency","spentAt":"2026-11-20T05:00:00Z","paidByUserId":"$paidBy","splits":$splits$rate}"""
}

class SharedExpenseTest {
    @Test
    fun payerAndSplitUsersMustBeMembers() = apiTest {
        val g = group()
        val stranger = UUID.randomUUID().toString()

        val badPayer = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = stranger, shares = mapOf(g.a.userId to 100)))
        assertEquals(HttpStatusCode.UnprocessableEntity, badPayer.status)
        assertEquals("NOT_A_MEMBER", badPayer.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("paidByUserId"))

        val badSplit = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.a.userId, shares = mapOf(g.a.userId to 50, stranger to 50)))
        assertEquals("NOT_A_MEMBER", badSplit.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("splits"))

        val ok = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.b.userId, shares = mapOf(g.a.userId to 50, g.c.userId to 50)))
        assertEquals(HttpStatusCode.Created, ok.status)
        assertEquals(g.b.userId, ok.data().jsonObject.str("paidByUserId"))
    }

    @Test
    fun viewerCanOweAndPayButNotRecordExpenses() = apiTest {
        val g = group(role = "VIEWER")
        val ownerRecordsForViewer = post(
            "/trips/${g.tripId}/expenses",
            expense(amount = 90_000, paidBy = g.b.userId, shares = mapOf(g.a.userId to 30_000, g.b.userId to 30_000, g.c.userId to 30_000)),
        )
        assertEquals(HttpStatusCode.Created, ownerRecordsForViewer.status)

        use(g.b)
        val denied = post("/trips/${g.tripId}/expenses", expense(amount = 10, paidBy = g.b.userId, shares = emptyMap()))
        assertEquals(HttpStatusCode.Forbidden, denied.status)
    }

    @Test
    fun editingKeepsAMemberWhoHasSinceLeft() = apiTest {
        val g = group()
        val id = UUID.randomUUID().toString()
        post("/trips/${g.tripId}/expenses", expense(id, 100, g.b.userId, mapOf(g.a.userId to 50, g.b.userId to 50)))
        delete("/trips/${g.tripId}/members/${g.b.userId}")

        val edited = put("/trips/${g.tripId}/expenses/$id", expense(id, 100, g.b.userId, mapOf(g.a.userId to 50, g.b.userId to 50)))
        assertEquals(HttpStatusCode.OK, edited.status)

        // ...but they cannot be put on a brand new expense
        val fresh = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.b.userId, shares = emptyMap()))
        assertEquals(HttpStatusCode.UnprocessableEntity, fresh.status)
    }
}

class BalanceRoutesTest {
    private fun JsonArray.byUser() = associate { it.jsonObject.str("userId") to it.jsonObject }

    @Test
    fun balancesAndFewestTransfersForAThreeWaySplit() = apiTest {
        val g = group()
        // A pays 100.000 split equally (remainder to the payer: 33.334 / 33.333 / 33.333)
        post("/trips/${g.tripId}/expenses", expense(amount = 100_000, paidBy = g.a.userId, shares = mapOf(g.a.userId to 33_334, g.b.userId to 33_333, g.c.userId to 33_333)))
        // B pays 60.000 split equally
        use(g.b)
        post("/trips/${g.tripId}/expenses", expense(amount = 60_000, paidBy = g.b.userId, shares = mapOf(g.a.userId to 20_000, g.b.userId to 20_000, g.c.userId to 20_000)))

        val balances = get("/trips/${g.tripId}/balances").data().jsonObject
        assertEquals("IDR", balances.str("baseCurrency"))
        val members = balances.getValue("members").jsonArray.byUser()
        assertEquals(46_666L, members.getValue(g.a.userId).long("balanceMinor"))
        assertEquals(6_667L, members.getValue(g.b.userId).long("balanceMinor"))
        assertEquals(-53_333L, members.getValue(g.c.userId).long("balanceMinor"))
        assertEquals(0L, members.values.sumOf { it.long("balanceMinor") })
        assertEquals(100_000L, members.getValue(g.a.userId).long("paidMinor"))
        assertEquals(53_334L, members.getValue(g.a.userId).long("shareMinor"))

        val transfers = balances.getValue("transfers").jsonArray.map { it.jsonObject }
        assertEquals(2, transfers.size)
        assertEquals(setOf(g.c.userId), transfers.map { it.str("fromUserId") }.toSet())
        assertEquals(46_666L, transfers.single { it.str("toUserId") == g.a.userId }.long("amountMinor"))
        assertEquals(6_667L, transfers.single { it.str("toUserId") == g.b.userId }.long("amountMinor"))
    }

    @Test
    fun settlementsUpdateTheBalances() = apiTest {
        val g = group()
        post("/trips/${g.tripId}/expenses", expense(amount = 90_000, paidBy = g.a.userId, shares = mapOf(g.a.userId to 30_000, g.b.userId to 30_000, g.c.userId to 30_000)))

        use(g.c)
        val settled = post(
            "/trips/${g.tripId}/settlements",
            """{"id":"${UUID.randomUUID()}","fromUserId":"${g.c.userId}","toUserId":"${g.a.userId}","amountMinor":30000}""",
        )
        assertEquals(HttpStatusCode.Created, settled.status)

        val members = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("members").jsonArray.byUser()
        assertEquals(0L, members.getValue(g.c.userId).long("balanceMinor"))
        assertEquals(30_000L, members.getValue(g.a.userId).long("balanceMinor"))
        assertEquals(1, get("/trips/${g.tripId}/settlements").data().jsonArray.size)
        val transfers = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("transfers").jsonArray
        assertEquals(1, transfers.size) // only B still owes A
    }

    @Test
    fun settlementValidation() = apiTest {
        val g = group()
        fun body(id: String = UUID.randomUUID().toString(), from: String, to: String, amount: Long = 1000) =
            """{"id":"$id","fromUserId":"$from","toUserId":"$to","amountMinor":$amount}"""

        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.a.userId, to = g.a.userId)).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.a.userId, to = g.b.userId, amount = 0)).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.a.userId, to = UUID.randomUUID().toString())).status)

        val id = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/${g.tripId}/settlements", body(id, g.a.userId, g.b.userId)).status)
        assertEquals(HttpStatusCode.Conflict, post("/trips/${g.tripId}/settlements", body(id, g.a.userId, g.b.userId)).status)
    }

    @Test
    fun foreignCurrencyIsConvertedAndStillSumsToZero() = apiTest {
        val g = group()
        // USD 10.00 at 1 USD = 16.000 IDR, shared 6.00 / 4.00 -> 96.000 / 64.000
        post("/trips/${g.tripId}/expenses", expense(amount = 1_000, paidBy = g.a.userId, shares = mapOf(g.a.userId to 600, g.b.userId to 400), currency = "USD", fx = "16000"))
        // an awkward rate: rounding must not leak money
        post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.b.userId, shares = mapOf(g.a.userId to 33, g.b.userId to 33, g.c.userId to 34), currency = "USD", fx = "16333.3333"))

        val members = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("members").jsonArray.byUser()
        assertEquals(0L, members.values.sumOf { it.long("balanceMinor") })
        assertEquals(160_000L, members.getValue(g.a.userId).long("paidMinor"))
    }

    @Test
    fun settlementsTravelThroughSync() = apiTest {
        val g = group()
        post(
            "/trips/${g.tripId}/settlements",
            """{"id":"${UUID.randomUUID()}","fromUserId":"${g.b.userId}","toUserId":"${g.a.userId}","amountMinor":500}""",
        )
        val changes = get("/sync").data().jsonObject.getValue("changes").jsonObject
        assertEquals(1, changes.getValue("settlements").jsonArray.size)
        assertEquals(3, changes.getValue("members").jsonArray.size)
    }
}
