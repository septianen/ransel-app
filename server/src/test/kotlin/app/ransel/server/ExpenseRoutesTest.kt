package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ME = "00000000-0000-0000-0000-000000000001"

private fun expenseJson(
    id: String = UUID.randomUUID().toString(),
    amount: Long = 50_000,
    currency: String = "IDR",
    fx: String? = null,
    category: String = "FOOD",
    spentAt: String = "2026-11-20T05:00:00Z",
    splits: String = "[]",
) = buildString {
    append("""{"id":"$id","amountMinor":$amount,"currency":"$currency","category":"$category","spentAt":"$spentAt","splits":$splits""")
    if (fx != null) append(""","fxRateToBase":"$fx"""")
    append("}")
}

class ExpenseRoutesTest {
    private suspend fun Api.newTrip(): String = UUID.randomUUID().toString().also { post("/trips", tripJson(it)) }

    @Test
    fun createDefaultsPayerAndPaymentMethod() = apiTest {
        val trip = newTrip()
        val response = post("/trips/$trip/expenses", expenseJson())
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.data().jsonObject
        assertEquals(ME, body.getValue("paidByUserId").jsonPrimitive.content)
        assertEquals("CASH", body.getValue("paymentMethod").jsonPrimitive.content)
    }

    @Test
    fun splitsMustAddUpToAmount() = apiTest {
        val trip = newTrip()
        val bad = post("/trips/$trip/expenses", expenseJson(amount = 100, splits = """[{"userId":"$ME","shareMinor":60}]"""))
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertEquals("SPLIT_TOTAL_MISMATCH", bad.errorCode())

        val ok = post("/trips/$trip/expenses", expenseJson(amount = 100, splits = """[{"userId":"$ME","shareMinor":100}]"""))
        assertEquals(HttpStatusCode.Created, ok.status)
        assertEquals(1, ok.data().jsonObject.getValue("splits").jsonArray.size)
    }

    @Test
    fun replaceReplacesSplitsWholeAndValidates() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/expenses", expenseJson(id, amount = 100, splits = """[{"userId":"$ME","shareMinor":100}]"""))
        val updated = put("/trips/$trip/expenses/$id", expenseJson(id, amount = 200))
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(0, updated.data().jsonObject.getValue("splits").jsonArray.size)
        assertEquals("VALIDATION_FAILED", put("/trips/$trip/expenses/$id", expenseJson(id, amount = 0)).errorCode())
        assertEquals("VALIDATION_FAILED", put("/trips/$trip/expenses/$id", expenseJson(UUID.randomUUID().toString())).errorCode())
    }

    @Test
    fun unknownPayerIs422() = apiTest {
        val trip = newTrip()
        val body = expenseJson().dropLast(1) + ""","paidByUserId":"${UUID.randomUUID()}"}"""
        val response = post("/trips/$trip/expenses", body)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun deletedExpenseIsHiddenFromListAndSummary() = apiTest {
        val trip = newTrip()
        val id = UUID.randomUUID().toString()
        post("/trips/$trip/expenses", expenseJson(id, amount = 70_000))
        assertEquals(HttpStatusCode.NoContent, delete("/trips/$trip/expenses/$id").status)
        assertEquals(0, get("/trips/$trip/expenses").data().jsonArray.size)
        assertEquals("0", get("/trips/$trip/expenses/summary").data().jsonObject.getValue("spentMinor").jsonPrimitive.content)
        assertEquals(HttpStatusCode.NotFound, delete("/trips/$trip/expenses/$id").status)
    }

    @Test
    fun summaryConvertsCurrenciesAndGroupsByJakartaDay() = apiTest {
        val trip = newTrip() // IDR, budget 5.000.000
        post("/trips/$trip/expenses", expenseJson(amount = 50_000, category = "FOOD", spentAt = "2026-11-20T05:00:00Z"))
        // 18:00Z on the 20th = 01:00 WIB on the 21st; USD 10.00 at 16.000 = Rp160.000
        post("/trips/$trip/expenses", expenseJson(amount = 1_000, currency = "USD", fx = "16000", category = "SHOPPING", spentAt = "2026-11-20T18:00:00Z"))

        val summary = get("/trips/$trip/expenses/summary").data().jsonObject
        assertEquals(210_000L, summary.getValue("spentMinor").jsonPrimitive.content.toLong())
        assertEquals(4_790_000L, summary.getValue("remainingMinor").jsonPrimitive.content.toLong())

        val categories = summary.getValue("byCategory").jsonArray.map { it.jsonObject }
        assertEquals(6, categories.size)
        assertEquals(160_000L, categories.first { it.str("category") == "SHOPPING" }.str("amountMinor").toLong())

        val days = summary.getValue("byDay").jsonArray.map { it.jsonObject }
        assertEquals(listOf("2026-11-20", "2026-11-21"), days.map { it.str("date") })
        assertEquals(160_000L, days[1].str("amountMinor").toLong())
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
