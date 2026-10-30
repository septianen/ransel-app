package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Karung stage B pages (B9.7): overview, Explore AI, recent searches, system. */
class AdminMonitoringRoutesTest {
    private val config = TestDb.config().copy(
        adminEmails = setOf("admin@example.com"),
        aiMonthlyBudgetMicroUsd = 170_000_000,
        serverMonthlyCostMicroUsd = 24_600_000,
        appVersion = "abc123",
    )

    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.content.toInt()
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    /** The first user plans a Bali trip, searches once and likes one card; then the admin signs in. Returns the user id. */
    private suspend fun Api.userSearchedThenAdmin(): String {
        val user = userId
        val trip = UUID.randomUUID().toString()
        post("/trips", tripJson(trip, start = "2099-11-20", end = "2099-11-23", destination = " bali "))
        assertEquals(HttpStatusCode.OK, post("/recommendations", monitoringSearch(trip)).status)
        val like = """{"tripId":"$trip","placeId":"place-Pantai Kuta","placeName":"Pantai Kuta","category":"ATTRACTION","action":"LIKE"}"""
        assertEquals(HttpStatusCode.NoContent, post("/recommendations/feedback", like).status)
        assertEquals(HttpStatusCode.NotFound, get("/trips/${UUID.randomUUID()}").status)
        signUp(email = "admin@example.com")
        return user
    }

    /** Active days are written off the request path. */
    private fun waitForActiveDays(count: Int) {
        repeat(50) {
            if (TestDb.column("SELECT count(*) FROM user_active_days").single().toInt() >= count) return
            Thread.sleep(100)
        }
    }

    @Test
    fun overviewCountsTodayTripsSearchesCreditsAndCost() = apiTest(config, ai = MonitoringAi) {
        userSearchedThenAdmin()
        get("/admin/me")
        waitForActiveDays(2)
        val o = get("/admin/overview").data().jsonObject
        assertEquals("LAST_7_DAYS", o.str("range"))
        assertEquals(2, o.int("activeToday"))
        assertEquals(2, o.int("registeredUsers"))
        assertEquals(1, o.int("newTrips"))
        assertEquals(0, o.int("newTripsPrevious"))
        assertEquals(1, o.int("searchesToday"))
        assertEquals(0, o.int("searchesTodayFromCache"))
        // a new search costs 3, all from the free daily allowance
        assertEquals(3, o.int("creditsUsedToday"))
        assertEquals(3, o.int("creditsUsedTodayFree"))
        val destinations = o.getValue("topDestinations").jsonArray.map { it.jsonObject }
        assertEquals(listOf("bali" to 1), destinations.map { it.str("name") to it.int("count") })
        val days = o.getValue("activeByDay").jsonArray
        assertEquals(30, days.size)
        val today = days.last().jsonObject
        assertEquals(LocalDate.now(ZoneId.of("Asia/Jakarta")).toString(), today.str("date"))
        assertEquals(2, today.int("signups"))
        val cost = o.getValue("aiCost").jsonObject
        assertEquals(170_000_000L, cost.str("budgetMicroUsd").toLong())
        assertTrue(cost.str("spentMicroUsd").toLong() >= 3_000) // at least the search's LLM call
        assertEquals(24_600_000L, o.getValue("costByService").jsonObject.str("serverMicroUsd").toLong())
        val errors = o.getValue("recentErrors").jsonArray.map { it.jsonObject }
        val notFound = errors.single { it.str("code") == "TRIP_NOT_FOUND" }
        assertEquals("GET /trips/{id}", notFound.str("topRoute"))
        assertEquals(1, notFound.int("users"))
        assertEquals(0, notFound.getValue("trend").jsonArray.size)

        assertEquals(1, get("/admin/overview?range=TODAY").data().jsonObject.int("newTrips"))
        val bad = get("/admin/overview?range=LAST_24_HOURS")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
    }

    @Test
    fun exploreShowsTheMonthsSearchesCardsInterestsAndCostPerDay() = apiTest(config, ai = MonitoringAi) {
        userSearchedThenAdmin()
        val e = get("/admin/explore").data().jsonObject
        assertEquals(1, e.int("searches"))
        assertEquals(2, e.int("cardsShown"))
        assertEquals(1, e.int("cardsSaved"))
        assertEquals(1, e.int("exploreSearches"))
        assertEquals(3, e.int("creditsFromFree"))
        assertEquals(0, e.int("creditsFromWallet"))
        assertEquals(listOf("LOW" to 0, "MEDIUM" to 1, "HIGH" to 0), e.getValue("priceLevels").jsonArray.map { it.jsonObject.str("name") to it.jsonObject.int("count") })
        val interests = e.getValue("topInterests").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Diving", "FOOD"), interests.map { it.str("name") })
        assertEquals("bali", interests.first().str("destination"))
        assertNull(interests.last()["destination"]?.jsonPrimitive?.takeIf { it.isString }) // FOOD is built in
        val days = e.getValue("costByDay").jsonArray
        assertEquals(e.int("days"), days.size)
        assertTrue(days.last().jsonObject.str("llmMicroUsd").toLong() >= 3_000)

        assertEquals(0, get("/admin/explore?month=2020-01").data().jsonObject.int("searches"))
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/admin/explore?month=januari").status)
    }

    @Test
    fun recentSearchesListParametersAndFilterByUser() = apiTest(config, ai = MonitoringAi) {
        val user = userSearchedThenAdmin()
        val rows = get("/admin/explore/searches").data().jsonArray.map { it.jsonObject }
        val row = rows.single()
        assertEquals(user, row.str("userId"))
        assertEquals("Tester", row.str("userName"))
        assertEquals("EXPLORE", row.str("kind"))
        assertEquals("NEW", row.str("source"))
        assertEquals("MEDIUM", row.str("priceLevel"))
        assertEquals(2, row.int("cards"))
        assertEquals(1, row.int("saved"))
        assertEquals(3, row.int("creditsCharged"))
        assertEquals(1, get("/admin/explore/searches?userId=$user").data().jsonArray.size)
        assertEquals(0, get("/admin/explore/searches?userId=$userId").data().jsonArray.size) // the admin searched nothing
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/admin/explore/searches?userId=nope").status)
    }

    @Test
    fun systemShowsHealthAndErrorsWithASevenDayTrend() = apiTest(config, ai = MonitoringAi) {
        userSearchedThenAdmin()
        val s = get("/admin/system").data().jsonObject
        assertEquals("LAST_24_HOURS", s.str("range"))
        assertEquals("abc123", s.str("version"))
        // the newest migration in the folder, so the next one does not break this test
        val newest = java.io.File("src/main/resources/db/migration").list().orEmpty()
            .mapNotNull { Regex("^V(\\d+)__").find(it)?.groupValues?.get(1)?.toInt() }.max()
        assertEquals(newest.toString(), s.str("flywayVersion"))
        assertTrue(s.str("databaseBytes").toLong() > 0)
        assertTrue(s.str("requests24h").toLong() >= 5)
        assertNull(s["lastBackupAt"]?.takeIf { it.toString() != "null" })
        assertEquals("false", s.str("llmConfigured")) // the test config has no keys
        val notFound = s.getValue("errors").jsonArray.map { it.jsonObject }.single { it.str("code") == "TRIP_NOT_FOUND" }
        assertEquals(404, notFound.int("httpStatus"))
        val trend = notFound.getValue("trend").jsonArray.map { it.jsonPrimitive.content.toLong() }
        assertEquals(7, trend.size)
        assertEquals(1L, trend.last())
        assertEquals(HttpStatusCode.UnprocessableEntity, get("/admin/system?range=TODAY").status)
    }

    @Test
    fun theLastBackupComesFromTheMarkerFile() {
        val marker = File.createTempFile("ransel-backup", ".marker").apply { writeText("2026-10-29T03:15:00Z\n"); deleteOnExit() }
        apiTest(config.copy(backupMarkerFile = marker.path)) {
            signUp(email = "admin@example.com")
            assertEquals("2026-10-29T03:15:00Z", get("/admin/system").data().jsonObject.str("lastBackupAt"))
        }
    }
}
