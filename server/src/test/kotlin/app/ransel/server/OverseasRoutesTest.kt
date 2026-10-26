package app.ransel.server

import app.ransel.server.service.FxRateService
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun rates(day: String, vararg pairs: Pair<String, String>) =
    TestDb.exec(pairs.joinToString(";") { (cur, rate) -> "INSERT INTO fx_rates (rate_date, currency, per_euro) VALUES ('$day', '$cur', $rate)" })

class OverseasRoutesTest {
    @Test
    fun ratesAreCrossedThroughTheEuroAndFallBackToTheLatestEarlierDay() = apiTest {
        TestDb.exec("TRUNCATE fx_rates")
        rates("2026-12-04", "JPY" to "162.5", "IDR" to "17250", "USD" to "1.08")
        rates("2026-12-07", "JPY" to "160", "IDR" to "17280")

        val friday = get("/fx/rate?from=JPY&to=IDR&date=2026-12-04").data().jsonObject
        assertEquals("106.153846154", friday.str("rate"))
        assertEquals("false", friday.str("stale"))

        // Saturday has no rates: Friday's, flagged
        val saturday = get("/fx/rate?from=jpy&to=IDR&date=2026-12-05").data().jsonObject
        assertEquals("2026-12-04", saturday.str("date"))
        assertEquals("true", saturday.str("stale"))

        // USD is missing on the 7th: the last day that has both
        assertEquals("2026-12-04", get("/fx/rate?from=USD&to=IDR&date=2026-12-07").data().jsonObject.str("date"))
        assertEquals("17280", get("/fx/rate?from=EUR&to=IDR&date=2026-12-07").data().jsonObject.str("rate"))
        assertEquals("1", get("/fx/rate?from=IDR&to=IDR&date=2026-12-07").data().jsonObject.str("rate"))

        assertEquals("NOT_FOUND", get("/fx/rate?from=JPY&to=IDR&date=2026-01-01").errorCode())
        assertEquals("NOT_FOUND", get("/fx/rate?from=XAF&to=IDR&date=2026-12-07").errorCode())
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/fx/rate?from=JPY&to=IDR&date=kemarin").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/fx/rate?from=JP1&to=IDR&date=2026-12-07").status)
    }

    @Test
    fun theEcbHistoryFileIsReadPerDay() {
        val days = FxRateService.parseHistory(
            """<gesmes:Envelope><Cube><Cube time="2026-12-07"><Cube currency="USD" rate="1.0812"/><Cube currency="JPY" rate="160.10"/></Cube>
            <Cube time='2026-12-04'><Cube currency='IDR' rate='17250.5'/></Cube></Cube></gesmes:Envelope>""",
        )
        assertEquals(setOf(LocalDate(2026, 12, 7), LocalDate(2026, 12, 4)), days.keys)
        assertEquals("160.10", days.getValue(LocalDate(2026, 12, 7)).getValue("JPY").toPlainString())
        assertEquals("17250.5", days.getValue(LocalDate(2026, 12, 4)).getValue("IDR").toPlainString())
    }

    @Test
    fun aTripAbroadKeepsItsCountryCurrencyAndZoneUntilTheDestinationChanges() = apiTest {
        val id = UUID.randomUUID().toString()
        val created = post("/trips", tripJson(id, destination = "Tokyo").dropLast(1) + ""","countryCode":"jp","localCurrency":"JPY","timeZone":"Asia/Tokyo"}""")
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val trip = created.data().jsonObject
        assertEquals("JP", trip.str("countryCode"))
        assertEquals("Asia/Tokyo", trip.str("timeZone"))

        put("/trips/$id", tripJson(id, title = "Jepang", destination = "Tokyo").replace(""""id":"$id",""", ""))
        assertEquals("JPY", get("/trips/$id").data().jsonObject.getValue("trip").jsonObject.str("localCurrency"))

        put("/trips/$id", tripJson(id, destination = "Bali").replace(""""id":"$id",""", ""))
        val home = get("/trips/$id").data().jsonObject.getValue("trip").jsonObject
        assertTrue(home["timeZone"] == null || home["timeZone"].toString() == "null")

        val bad = post("/trips", tripJson(UUID.randomUUID().toString()).dropLast(1) + ""","timeZone":"Tokyo"}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
    }

    @Test
    fun anExpenseKeepsWhereItsRateCameFrom() = apiTest {
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip))
        val body = """{"id":"${UUID.randomUUID()}","amountMinor":4800,"currency":"JPY","fxRateToBase":"105","spentAt":"2026-12-06T03:00:00Z","fxSource":"DAILY","fxDate":"2026-12-04"}"""
        val saved = post("/trips/$trip/expenses", body).data().jsonObject
        assertEquals("DAILY", saved.str("fxSource"))
        assertEquals("2026-12-04", saved.str("fxDate"))
        // a home-currency expense has no rate, so no rate source either
        val home = post("/trips/$trip/expenses", """{"id":"${UUID.randomUUID()}","amountMinor":50000,"currency":"IDR","spentAt":"2026-12-06T03:00:00Z","fxSource":"MANUAL"}""").data().jsonObject
        assertTrue(home["fxSource"] == null || home["fxSource"].toString() == "null")
        val listed = get("/trips/$trip/expenses").data().jsonArray.map { it.jsonObject }
        assertEquals(2, listed.size)
    }
}
