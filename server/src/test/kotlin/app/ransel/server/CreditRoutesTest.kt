package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditRoutesTest {
    private fun voucher(code: String, credits: Int = 15, maxUses: Int = 1, expires: String = "NULL", revoked: String = "NULL") =
        TestDb.exec("INSERT INTO credit_vouchers (code, credits, max_uses, expires_at, revoked_at) VALUES ('$code', $credits, $maxUses, $expires, $revoked)")

    private suspend fun Api.balance() = get("/credits").data().jsonObject.getValue("balance").jsonPrimitive.content
    private suspend fun Api.redeem(code: String) = post("/credits/redeem", """{"code":"$code"}""")

    @Test
    fun aNewUserGetsTheSignupGrantAndTheFreeAllowance() {
        apiTest {
            val credits = get("/credits").data().jsonObject
            assertEquals("30", credits.getValue("balance").jsonPrimitive.content)
            assertEquals("3", credits.getValue("dailyFreeLeft").jsonPrimitive.content)
            assertEquals("3", credits.getValue("dailyFreeLimit").jsonPrimitive.content)
            assertTrue(credits.getValue("resetsAt").jsonPrimitive.content.endsWith("Z"))
            val costs = credits.getValue("costs").jsonObject
            assertEquals(listOf("3", "1", "0"), listOf("search", "nextBatch", "placeDetail").map { costs.getValue(it).jsonPrimitive.content })
        }
    }

    @Test
    fun theSignupGrantIsGivenOnceEvenWhenTheFirstCallsRace() {
        apiTest {
            signUp()
            coroutineScope { (1..6).map { async { get("/credits") } }.awaitAll() }
            assertEquals("30", balance())
            val ledger = get("/credits/ledger").data().jsonArray
            assertEquals(listOf("SIGNUP_GRANT"), ledger.map { it.jsonObject.getValue("kind").jsonPrimitive.content })
        }
    }

    @Test
    fun packagesListThePricesButCannotBeBoughtYet() {
        apiTest {
            val data = get("/credits/packages").data().jsonObject
            val packs = data.getValue("packages").jsonArray.map { it.jsonObject }
            assertEquals(listOf("15", "40", "100"), packs.map { it.getValue("credits").jsonPrimitive.content })
            assertEquals(listOf("10000", "25000", "55000"), packs.map { it.getValue("priceMinor").jsonPrimitive.content })
            assertEquals("false", data.getValue("purchasable").jsonPrimitive.content)
        }
    }

    @Test
    fun creditsNeedASignedInUser() {
        apiTest {
            token = null
            assertEquals(HttpStatusCode.Unauthorized, get("/credits").status)
        }
    }

    @Test
    fun redeemingAVoucherAddsItsCreditsAndWritesTheLedger() {
        apiTest {
            voucher("TESTCODE1", credits = 15)
            val response = redeem(" testcode1 ") // trimmed, case-insensitive
            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.data().jsonObject
            assertEquals("15", data.getValue("granted").jsonPrimitive.content)
            assertEquals("45", data.getValue("credits").jsonObject.getValue("balance").jsonPrimitive.content)

            val kinds = get("/credits/ledger").data().jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content }
            assertEquals(listOf("VOUCHER", "SIGNUP_GRANT"), kinds, "newest first")
        }
    }

    @Test
    fun aVoucherWorksOncePerUserAndStopsWhenFull() {
        apiTest {
            voucher("SHARED2", credits = 10, maxUses = 2)
            assertEquals(HttpStatusCode.OK, redeem("SHARED2").status)

            val again = redeem("SHARED2")
            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals("VOUCHER_USED", again.errorCode())
            assertEquals("40", balance(), "a second redemption by the same user adds nothing")

            signUp()
            assertEquals(HttpStatusCode.OK, redeem("SHARED2").status)
            signUp()
            val full = redeem("SHARED2")
            assertEquals(HttpStatusCode.Conflict, full.status)
            assertEquals("VOUCHER_USED", full.errorCode())
        }
    }

    @Test
    fun unknownRevokedAndExpiredVouchersAreRejected() {
        apiTest {
            voucher("OLDONE", expires = "now() - interval '1 day'")
            voucher("GONE", revoked = "now()")

            val unknown = redeem("NOPE")
            assertEquals(HttpStatusCode.NotFound, unknown.status)
            assertEquals("VOUCHER_INVALID", unknown.errorCode())
            assertEquals("VOUCHER_INVALID", redeem("GONE").errorCode())

            val expired = redeem("OLDONE")
            assertEquals(HttpStatusCode.UnprocessableEntity, expired.status)
            assertEquals("VOUCHER_EXPIRED", expired.errorCode())
            assertEquals("30", balance())
        }
    }

    @Test
    fun redeemingTheSameVoucherInParallelAddsItOnce() {
        apiTest {
            voucher("RACE1", credits = 20, maxUses = 5)
            val statuses = coroutineScope { (1..5).map { async { redeem("RACE1").status } }.awaitAll() }
            assertEquals(1, statuses.count { it == HttpStatusCode.OK })
            assertEquals("50", balance())
        }
    }
}
