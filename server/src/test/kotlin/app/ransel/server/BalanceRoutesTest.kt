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

private class Group(val tripId: String, val a: Session, val b: Session, val c: Session) {
    /** The member id of [s] in this trip. */
    fun m(s: Session) = s.memberId(tripId)
}

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
    val splits = shares.entries.joinToString(",", "[", "]") { """{"memberId":"${it.key}","shareMinor":${it.value}}""" }
    val rate = if (fx != null) ""","fxRateToBase":"$fx"""" else ""
    return """{"id":"$id","amountMinor":$amount,"currency":"$currency","spentAt":"2026-11-20T05:00:00Z","paidByMemberId":"$paidBy","splits":$splits$rate}"""
}

class SharedExpenseTest {
    @Test
    fun payerAndSplitUsersMustBeMembers() = apiTest {
        val g = group()
        val stranger = UUID.randomUUID().toString()

        val badPayer = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = stranger, shares = mapOf(g.m(g.a) to 100)))
        assertEquals(HttpStatusCode.UnprocessableEntity, badPayer.status)
        assertEquals("NOT_A_MEMBER", badPayer.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("paidByMemberId"))

        val badSplit = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.m(g.a), shares = mapOf(g.m(g.a) to 50, stranger to 50)))
        assertEquals("NOT_A_MEMBER", badSplit.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject.str("splits"))

        val ok = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.m(g.b), shares = mapOf(g.m(g.a) to 50, g.m(g.c) to 50)))
        assertEquals(HttpStatusCode.Created, ok.status)
        assertEquals(g.m(g.b), ok.data().jsonObject.str("paidByMemberId"))
    }

    @Test
    fun viewerCanOweAndPayButNotRecordExpenses() = apiTest {
        val g = group(role = "VIEWER")
        val ownerRecordsForViewer = post(
            "/trips/${g.tripId}/expenses",
            expense(amount = 90_000, paidBy = g.m(g.b), shares = mapOf(g.m(g.a) to 30_000, g.m(g.b) to 30_000, g.m(g.c) to 30_000)),
        )
        assertEquals(HttpStatusCode.Created, ownerRecordsForViewer.status)

        use(g.b)
        val denied = post("/trips/${g.tripId}/expenses", expense(amount = 10, paidBy = g.m(g.b), shares = emptyMap()))
        assertEquals(HttpStatusCode.Forbidden, denied.status)
    }

    @Test
    fun editingKeepsAMemberWhoHasSinceLeft() = apiTest {
        val g = group()
        val id = UUID.randomUUID().toString()
        post("/trips/${g.tripId}/expenses", expense(id, 100, g.m(g.b), mapOf(g.m(g.a) to 50, g.m(g.b) to 50)))
        delete("/trips/${g.tripId}/members/${g.m(g.b)}")

        val edited = put("/trips/${g.tripId}/expenses/$id", expense(id, 100, g.m(g.b), mapOf(g.m(g.a) to 50, g.m(g.b) to 50)))
        assertEquals(HttpStatusCode.OK, edited.status)

        // ...but they cannot be put on a brand new expense
        val fresh = post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.m(g.b), shares = emptyMap()))
        assertEquals(HttpStatusCode.UnprocessableEntity, fresh.status)
    }
}

class BalanceRoutesTest {
    private fun JsonArray.byUser() = associate { it.jsonObject.str("memberId") to it.jsonObject }

    @Test
    fun balancesAndFewestTransfersForAThreeWaySplit() = apiTest {
        val g = group()
        // A pays 100.000 split equally (remainder to the payer: 33.334 / 33.333 / 33.333)
        post("/trips/${g.tripId}/expenses", expense(amount = 100_000, paidBy = g.m(g.a), shares = mapOf(g.m(g.a) to 33_334, g.m(g.b) to 33_333, g.m(g.c) to 33_333)))
        // B pays 60.000 split equally
        use(g.b)
        post("/trips/${g.tripId}/expenses", expense(amount = 60_000, paidBy = g.m(g.b), shares = mapOf(g.m(g.a) to 20_000, g.m(g.b) to 20_000, g.m(g.c) to 20_000)))

        val balances = get("/trips/${g.tripId}/balances").data().jsonObject
        assertEquals("IDR", balances.str("baseCurrency"))
        val members = balances.getValue("members").jsonArray.byUser()
        assertEquals(46_666L, members.getValue(g.m(g.a)).long("balanceMinor"))
        assertEquals(6_667L, members.getValue(g.m(g.b)).long("balanceMinor"))
        assertEquals(-53_333L, members.getValue(g.m(g.c)).long("balanceMinor"))
        assertEquals(0L, members.values.sumOf { it.long("balanceMinor") })
        assertEquals(100_000L, members.getValue(g.m(g.a)).long("paidMinor"))
        assertEquals(53_334L, members.getValue(g.m(g.a)).long("shareMinor"))

        val transfers = balances.getValue("transfers").jsonArray.map { it.jsonObject }
        assertEquals(2, transfers.size)
        assertEquals(setOf(g.m(g.c)), transfers.map { it.str("fromMemberId") }.toSet())
        assertEquals(46_666L, transfers.single { it.str("toMemberId") == g.m(g.a) }.long("amountMinor"))
        assertEquals(6_667L, transfers.single { it.str("toMemberId") == g.m(g.b) }.long("amountMinor"))
    }

    @Test
    fun settlementsUpdateTheBalances() = apiTest {
        val g = group()
        post("/trips/${g.tripId}/expenses", expense(amount = 90_000, paidBy = g.m(g.a), shares = mapOf(g.m(g.a) to 30_000, g.m(g.b) to 30_000, g.m(g.c) to 30_000)))

        use(g.c)
        val settled = post(
            "/trips/${g.tripId}/settlements",
            """{"id":"${UUID.randomUUID()}","fromMemberId":"${g.m(g.c)}","toMemberId":"${g.m(g.a)}","amountMinor":30000}""",
        )
        assertEquals(HttpStatusCode.Created, settled.status)

        val members = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("members").jsonArray.byUser()
        assertEquals(0L, members.getValue(g.m(g.c)).long("balanceMinor"))
        assertEquals(30_000L, members.getValue(g.m(g.a)).long("balanceMinor"))
        assertEquals(1, get("/trips/${g.tripId}/settlements").data().jsonArray.size)
        val transfers = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("transfers").jsonArray
        assertEquals(1, transfers.size) // only B still owes A
    }

    @Test
    fun settlementValidation() = apiTest {
        val g = group()
        fun body(id: String = UUID.randomUUID().toString(), from: String, to: String, amount: Long = 1000) =
            """{"id":"$id","fromMemberId":"$from","toMemberId":"$to","amountMinor":$amount}"""

        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.m(g.a), to = g.m(g.a))).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.m(g.a), to = g.m(g.b), amount = 0)).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, post("/trips/${g.tripId}/settlements", body(from = g.m(g.a), to = UUID.randomUUID().toString())).status)

        val id = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post("/trips/${g.tripId}/settlements", body(id, g.m(g.a), g.m(g.b))).status)
        assertEquals(HttpStatusCode.Conflict, post("/trips/${g.tripId}/settlements", body(id, g.m(g.a), g.m(g.b))).status)
    }

    @Test
    fun foreignCurrencyIsConvertedAndStillSumsToZero() = apiTest {
        val g = group()
        // USD 10.00 at 1 USD = 16.000 IDR, shared 6.00 / 4.00 -> 96.000 / 64.000
        post("/trips/${g.tripId}/expenses", expense(amount = 1_000, paidBy = g.m(g.a), shares = mapOf(g.m(g.a) to 600, g.m(g.b) to 400), currency = "USD", fx = "16000"))
        // an awkward rate: rounding must not leak money
        post("/trips/${g.tripId}/expenses", expense(amount = 100, paidBy = g.m(g.b), shares = mapOf(g.m(g.a) to 33, g.m(g.b) to 33, g.m(g.c) to 34), currency = "USD", fx = "16333.3333"))

        val members = get("/trips/${g.tripId}/balances").data().jsonObject.getValue("members").jsonArray.byUser()
        assertEquals(0L, members.values.sumOf { it.long("balanceMinor") })
        assertEquals(160_000L, members.getValue(g.m(g.a)).long("paidMinor"))
    }

    @Test
    fun settlementsTravelThroughSync() = apiTest {
        val g = group()
        post(
            "/trips/${g.tripId}/settlements",
            """{"id":"${UUID.randomUUID()}","fromMemberId":"${g.m(g.b)}","toMemberId":"${g.m(g.a)}","amountMinor":500}""",
        )
        val changes = get("/sync").data().jsonObject.getValue("changes").jsonObject
        assertEquals(1, changes.getValue("settlements").jsonArray.size)
        assertEquals(3, changes.getValue("members").jsonArray.size)
    }

    @Test
    fun syncCannotRewriteOrRemoveSomeoneElsesSettlement() = apiTest {
        val g = group()
        val id = UUID.randomUUID().toString()
        post("/trips/${g.tripId}/settlements", """{"id":"$id","fromMemberId":"${g.m(g.b)}","toMemberId":"${g.m(g.a)}","amountMinor":500}""")

        fun pushed(amount: Long = 500, deletedAt: String? = null): String {
            val deleted = if (deletedAt != null) "\"$deletedAt\"" else "null"
            val row = """{"id":"$id","tripId":"${g.tripId}","fromMemberId":"${g.m(g.b)}","toMemberId":"${g.m(g.a)}",""" +
                """"amountMinor":$amount,"settledAt":"2026-11-20T05:00:00Z","status":"CONFIRMED",""" +
                """"createdAt":"2026-11-20T05:00:00Z","updatedAt":"2099-01-01T00:00:00Z","deletedAt":$deleted}"""
            return """{"changes":{"settlements":[$row]}}"""
        }
        suspend fun rejectionOf(body: String) =
            post("/sync", body).data().jsonObject.getValue("rejected").jsonArray.singleOrNull()?.jsonObject?.str("code")

        // C is neither payer nor receiver: no new amount, no removal.
        use(g.c)
        assertEquals("FORBIDDEN", rejectionOf(pushed(amount = 1)))
        assertEquals("FORBIDDEN", rejectionOf(pushed(deletedAt = "2099-01-01T00:00:00Z")))
        // The payer may remove it, but not change the amount either.
        use(g.b)
        assertEquals("FORBIDDEN", rejectionOf(pushed(amount = 1)))
        assertEquals(null, rejectionOf(pushed(deletedAt = "2099-01-01T00:00:00Z")))
        assertEquals(0, get("/trips/${g.tripId}/settlements").data().jsonArray.size)
    }
}
