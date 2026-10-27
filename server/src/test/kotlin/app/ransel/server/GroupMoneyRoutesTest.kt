package app.ransel.server

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.InterestSuggestions
import app.ransel.server.ai.LlmCandidate
import app.ransel.server.ai.LlmClient
import app.ransel.server.ai.LlmQuery
import app.ransel.server.ai.LlmReceipt
import app.ransel.server.ai.LlmReceiptLine
import app.ransel.server.ai.PlaceDetails
import app.ransel.server.ai.PlacePhoto
import app.ransel.server.ai.PlacesClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String) = str(key).toLong()

/** Owner A with B (EDITOR) and C (VIEWER) joined. Ends signed in as A. */
private class MoneyGroup(val tripId: String, val a: Session, val b: Session, val c: Session) {
    val ma get() = a.memberId(tripId)
    val mb get() = b.memberId(tripId)
    val mc get() = c.memberId(tripId)
}

private suspend fun Api.group(): MoneyGroup {
    val a = session()
    val tripId = UUID.randomUUID().toString()
    post("/trips", tripJson(tripId))
    val others = mutableListOf<Session>()
    for (role in listOf("EDITOR", "VIEWER")) {
        use(a)
        val code = post("/trips/$tripId/invites", """{"role":"$role","expiresAt":"2099-01-01T00:00:00Z"}""").data().jsonObject.str("code")
        signUp()
        assertEquals(HttpStatusCode.OK, post("/invites/$code/accept").status)
        others += session()
    }
    use(a)
    return MoneyGroup(tripId, a, others[0], others[1])
}

private fun shares(pairs: Map<String, Long>) = pairs.entries.joinToString(",", "[", "]") { """{"memberId":"${it.key}","shareMinor":${it.value}}""" }

private fun expenseJson(
    amount: Long,
    splits: Map<String, Long> = emptyMap(),
    paidBy: String? = null,
    extra: String = "",
    id: String = UUID.randomUUID().toString(),
): String {
    val payer = paidBy?.let { ""","paidByMemberId":"$it"""" }.orEmpty()
    return """{"id":"$id","amountMinor":$amount,"currency":"IDR","spentAt":"2026-11-21T12:00:00Z"$payer,"splits":${shares(splits)}$extra}"""
}

private suspend fun Api.balances(tripId: String): Map<String, Long> =
    get("/trips/$tripId/balances").data().jsonObject.getValue("members").jsonArray
        .map { it.jsonObject }.associate { it.str("memberId") to it.long("balanceMinor") }

private suspend fun Api.inboxOf(session: Session): List<JsonObject> {
    val back = session()
    use(session)
    val items = get("/notifications").data().jsonObject.getValue("items").jsonArray.map { it.jsonObject }
    use(back)
    return items
}

private class ReceiptLlm(var receipt: LlmReceipt?) : LlmClient {
    override val modelName = "fake"
    var calls = 0
    override suspend fun suggest(query: LlmQuery): List<LlmCandidate> = emptyList()
    override suspend fun suggestInterests(destination: String, month: String, language: String) = InterestSuggestions(emptyList(), 30.0, 90.0)
    override suspend fun readReceipt(image: ByteArray, mimeType: String): LlmReceipt {
        calls++
        return receipt ?: LlmReceipt(total = null)
    }
}

private object NoPlaces : PlacesClient {
    override suspend fun findPlaceId(name: String, destination: String): String? = null
    override suspend fun details(placeId: String, withPhotoCredit: Boolean): PlaceDetails? = null
    override suspend fun photo(placeId: String): PlacePhoto? = null
}

private suspend fun Api.uploadReceipt(tripId: String, bytes: ByteArray, type: ContentType = ContentType.Image.JPEG): HttpResponse =
    client.post("/api/v1/receipts/parse?tripId=$tripId") {
        token?.let { bearerAuth(it) }
        contentType(type)
        setBody(bytes)
    }

class GroupMoneyRoutesTest {
    @Test
    fun aClaimCountsOnlyOnceTheReceiverConfirms() = apiTest {
        val g = group()
        post("/trips/${g.tripId}/expenses", expenseJson(200_000, mapOf(g.ma to 100_000L, g.mc to 100_000L)))
        assertEquals(-100_000L, balances(g.tripId).getValue(g.mc))

        use(g.c)
        val claimId = UUID.randomUUID().toString()
        val claim = post("/trips/${g.tripId}/settlements/claim", """{"id":"$claimId","toMemberId":"${g.ma}","amountMinor":100000}""")
        assertEquals(HttpStatusCode.Created, claim.status, claim.bodyAsText())
        assertEquals("CLAIMED_PAID", claim.data().jsonObject.str("status"))
        assertEquals(g.mc, claim.data().jsonObject.str("fromMemberId"))
        assertEquals(-100_000L, balances(g.tripId).getValue(g.mc), "a claim alone does not settle anything")

        val asked = inboxOf(g.a).single { it.str("type") == "SETTLEMENT_MARKED" }.getValue("params").jsonObject
        assertEquals("CLAIMED", asked.str("stage"))
        assertEquals(claimId, asked.str("settlementId"))

        use(g.b)
        assertEquals(HttpStatusCode.Forbidden, post("/trips/${g.tripId}/settlements/$claimId/confirm").status)
        use(g.a)
        val confirmed = post("/trips/${g.tripId}/settlements/$claimId/confirm")
        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("CONFIRMED", confirmed.data().jsonObject.str("status"))
        assertTrue(balances(g.tripId).values.all { it == 0L })
        assertEquals("SETTLEMENT_NOT_CLAIMED", post("/trips/${g.tripId}/settlements/$claimId/confirm").errorCode())
        assertTrue(inboxOf(g.c).any { it.str("type") == "SETTLEMENT_MARKED" && it.getValue("params").jsonObject.str("stage") == "CONFIRMED" })
    }

    @Test
    fun aReceiverRejectsAClaimThatNeverArrived() = apiTest {
        val g = group()
        post("/trips/${g.tripId}/expenses", expenseJson(200_000, mapOf(g.ma to 100_000L, g.mc to 100_000L)))
        use(g.c)
        val claimId = UUID.randomUUID().toString()
        post("/trips/${g.tripId}/settlements/claim", """{"id":"$claimId","toMemberId":"${g.ma}","amountMinor":100000}""")
        use(g.b)
        assertEquals(HttpStatusCode.Forbidden, delete("/trips/${g.tripId}/settlements/$claimId").status)
        use(g.a)
        assertEquals(HttpStatusCode.NoContent, delete("/trips/${g.tripId}/settlements/$claimId").status)
        assertTrue(get("/trips/${g.tripId}/settlements").data().jsonArray.isEmpty())
    }

    @Test
    fun payInfoIsOnlyForTheOneWhoOwes() = apiTest {
        val g = group()
        val bad = post("/me/payment-methods", """{"kind":"BANK","provider":"","accountNumber":"12ab","holderName":"Septian"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        val bca = post("/me/payment-methods", """{"kind":"BANK","provider":"BCA","accountNumber":"1234 5678 90","holderName":"Septian Nugraha"}""")
        assertEquals(HttpStatusCode.Created, bca.status, bca.bodyAsText())
        assertEquals("true", bca.data().jsonObject.str("isPrimary"), "the first method is the primary one")
        val gopay = post("/me/payment-methods", """{"kind":"EWALLET","provider":"GoPay","accountNumber":"0812-3456-7890","holderName":"Septian","isPrimary":true}""")
        val mine = get("/me/payment-methods").data().jsonArray.map { it.jsonObject }
        assertEquals(listOf("GoPay", "BCA"), mine.map { it.str("provider") })
        assertEquals(listOf("true", "false"), mine.map { it.str("isPrimary") })

        post("/trips/${g.tripId}/expenses", expenseJson(300_000, mapOf(g.ma to 100_000L, g.mb to 100_000L, g.mc to 100_000L)))

        use(g.c)
        val info = get("/trips/${g.tripId}/settlements/${g.mc}/${g.ma}/pay-info")
        assertEquals(HttpStatusCode.OK, info.status, info.bodyAsText())
        val body = info.data().jsonObject
        assertEquals(100_000L, body.long("amountMinor"))
        assertEquals("IDR", body.str("currency"))
        assertEquals(listOf("GoPay", "BCA"), body.getValue("methods").jsonArray.map { it.jsonObject.str("provider") })
        // nobody may ask on someone else's behalf, and nobody who does not owe gets it
        assertEquals(HttpStatusCode.Forbidden, get("/trips/${g.tripId}/settlements/${g.mb}/${g.ma}/pay-info").status)
        use(g.a)
        assertEquals(HttpStatusCode.Forbidden, get("/trips/${g.tripId}/settlements/${g.ma}/${g.mc}/pay-info").status)

        assertEquals(HttpStatusCode.NoContent, delete("/me/payment-methods/${gopay.data().jsonObject.str("id")}").status)
        assertEquals(1, get("/me/payment-methods").data().jsonArray.size)
    }

    @Test
    fun receiptItemsDecideTheSplits() = apiTest {
        val g = group()
        val items = """[{"name":"Nasi campur","amountMinor":130000,"memberIds":["${g.ma}","${g.mb}"]},""" +
            """{"name":"Sate lilit","amountMinor":130000,"memberIds":["${g.mc}"]},{"name":"Pajak","amountMinor":26000}]"""
        val created = post("/trips/${g.tripId}/expenses", expenseJson(286_000, extra = ""","items":$items"""))
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val body = created.data().jsonObject
        val splits = body.getValue("splits").jsonArray.map { it.jsonObject }.associate { it.str("memberId") to it.long("shareMinor") }
        assertEquals(mapOf(g.ma to 71_500L, g.mb to 71_500L, g.mc to 143_000L), splits)
        assertEquals(3, body.getValue("items").jsonArray.size)

        val wrong = post("/trips/${g.tripId}/expenses", expenseJson(300_000, extra = ""","items":$items"""))
        assertEquals("SPLIT_TOTAL_MISMATCH", wrong.errorCode())
    }

    @Test
    fun aStayWinnerBecomesAnEstimateUntilItIsPaid() = apiTest {
        val g = group()
        val pollId = UUID.randomUUID().toString()
        val optionId = UUID.randomUUID().toString()
        post(
            "/trips/${g.tripId}/polls",
            """{"id":"$pollId","kind":"STAY","question":"Menginap di mana?","closesAt":"2099-01-01T00:00:00Z",""" +
                """"options":[{"id":"$optionId","label":"Villa Kayu","priceMinor":2250000,"priceUnit":"NIGHT"}]}""",
        )
        TestDb.exec("UPDATE polls SET closes_at = now() - interval '1 minute' WHERE id = '$pollId'")
        val decided = post("/polls/$pollId/decide", """{"optionId":"$optionId"}""")
        assertEquals(HttpStatusCode.OK, decided.status, decided.bodyAsText())

        val estimate = get("/trips/${g.tripId}/expenses").data().jsonArray.single().jsonObject
        assertEquals("true", estimate.str("isEstimate"))
        assertEquals(6_750_000L, estimate.long("amountMinor"), "3 nights of 2.250.000")
        assertEquals("LODGING", estimate.str("category"))
        assertTrue(balances(g.tripId).values.all { it == 0L }, "an estimate is not a debt")
        val summary = get("/trips/${g.tripId}/expenses/summary").data().jsonObject
        assertEquals(0L, summary.long("spentMinor"))
        assertEquals(6_750_000L, summary.long("estimatedMinor"))

        // A booked it for 6.600.000: saving it as a real payment
        val paid = put(
            "/trips/${g.tripId}/expenses/${estimate.str("id")}",
            expenseJson(6_600_000, mapOf(g.ma to 2_200_000L, g.mb to 2_200_000L, g.mc to 2_200_000L), id = estimate.str("id"), extra = ""","category":"LODGING""""),
        )
        assertEquals(HttpStatusCode.OK, paid.status, paid.bodyAsText())
        assertEquals("false", paid.data().jsonObject.str("isEstimate"))
        assertEquals(-2_200_000L, balances(g.tripId).getValue(g.mc))
    }

    @Test
    fun receiptsAreReadForOneCreditAndUnreadableOnesCostNothing() {
        val llm = ReceiptLlm(
            LlmReceipt(
                placeName = "Warung Made", date = "2026-11-21", currency = "IDR",
                items = listOf(LlmReceiptLine("Nasi campur", 2, 130_000.0), LlmReceiptLine("Es kelapa", 4, 120_000.0)),
                tax = 25_000.0, service = 0.0, total = 275_000.0,
            ),
        )
        apiTest(ai = AiServices(llm, NoPlaces)) {
            val tripId = UUID.randomUUID().toString()
            post("/trips", tripJson(tripId))
            val before = get("/credits").data().jsonObject
            val available = before.long("balance") + before.long("dailyFreeLeft")
            assertEquals(1L, before.getValue("costs").jsonObject.long("receipt"))

            val read = uploadReceipt(tripId, ByteArray(1024) { 7 })
            assertEquals(HttpStatusCode.OK, read.status, read.bodyAsText())
            val draft = read.data().jsonObject
            assertEquals("Warung Made", draft.str("placeName"))
            assertEquals("2026-11-21", draft.str("spentOn"))
            assertEquals(275_000L, draft.long("totalMinor"))
            assertEquals(25_000L, draft.long("taxMinor"))
            assertEquals(listOf(130_000L, 120_000L), draft.getValue("items").jsonArray.map { it.jsonObject.long("amountMinor") })
            val after = draft.getValue("credits").jsonObject
            assertEquals(available - 1, after.long("balance") + after.long("dailyFreeLeft"))

            llm.receipt = null
            val unreadable = uploadReceipt(tripId, ByteArray(10) { 1 })
            assertEquals("RECEIPT_UNREADABLE", unreadable.errorCode())
            val still = get("/credits").data().jsonObject
            assertEquals(available - 1, still.long("balance") + still.long("dailyFreeLeft"), "an unreadable photo is free")

            val calls = llm.calls
            assertEquals("FILE_TOO_LARGE", uploadReceipt(tripId, ByteArray(5 * 1024 * 1024 + 1)).errorCode())
            assertEquals(HttpStatusCode.UnprocessableEntity, uploadReceipt(tripId, ByteArray(10), ContentType.Application.Pdf).status)
            assertEquals(calls, llm.calls, "nothing too large or of the wrong type reaches the model")
        }
    }

    @Test
    fun claimsSyncLikeOtherRows() = apiTest {
        val g = group()
        val now = "2026-10-26T08:00:00Z"
        val later = "2099-01-01T00:00:00Z"
        // a claim pushed by someone who is not its payer is refused; a confirmation by someone who is not the receiver too
        val claimId = UUID.randomUUID().toString()
        val claim = """{"id":"$claimId","tripId":"${g.tripId}","fromMemberId":"${g.mb}","toMemberId":"${g.ma}","amountMinor":1000,"settledAt":"$now","status":"CLAIMED_PAID","createdAt":"$now","updatedAt":"$now"}"""
        val foreign = post("/sync", """{"changes":{"settlements":[$claim]}}""").data().jsonObject
        assertEquals("FORBIDDEN", foreign.getValue("rejected").jsonArray.single().jsonObject.str("code"))
        use(g.b)
        val own = post("/sync", """{"changes":{"settlements":[$claim]}}""").data().jsonObject
        assertEquals(1, own.getValue("accepted").jsonArray.size, own.toString())
        val confirm = claim.replace("\"CLAIMED_PAID\"", "\"CONFIRMED\"").replace("\"updatedAt\":\"$now\"", "\"updatedAt\":\"$later\"")
        use(g.c)
        // C is a VIEWER: may record payments, but not confirm one for A
        val byC = post("/sync", """{"changes":{"settlements":[$confirm]}}""").data().jsonObject
        assertEquals("FORBIDDEN", byC.getValue("rejected").jsonArray.single().jsonObject.str("code"))
        use(g.a)
        val byA = post("/sync", """{"changes":{"settlements":[$confirm]}}""").data().jsonObject
        assertEquals(1, byA.getValue("accepted").jsonArray.size, byA.toString())
        assertEquals(1_000L, balances(g.tripId).getValue(g.mb), "the 1.000 B paid to A counts once A confirmed it")
    }
}
