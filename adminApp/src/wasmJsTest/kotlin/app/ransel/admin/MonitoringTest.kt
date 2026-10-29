package app.ransel.admin

import app.ransel.admin.data.AdminApi
import app.ransel.admin.data.ApiResult
import app.ransel.admin.data.createHttpClient
import app.ransel.admin.feature.monitoring.MonitorPresenter
import app.ransel.admin.feature.monitoring.budgetRunsOutOn
import app.ransel.admin.feature.monitoring.shiftMonth
import app.ransel.admin.ui.AdminFormat
import app.ransel.shared.model.AdminRange
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Karung stage B (F9.4): number formats, month and budget helpers, routes, and loading a page. */
class MonitoringTest {
    private val id = AdminFormat(
        "Jan Feb Mar Apr Mei Jun Jul Agu Sep Okt Nov Des".split(' '), ".", "Baru saja", "%1\$d mnt lalu", "%1\$d jam lalu", "Kemarin", "%1\$d hari lalu",
    )
    private val en = AdminFormat(
        "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split(' '), ",", "Just now", "%1\$d min ago", "%1\$d h ago", "Yesterday", "%1\$d days ago",
    )

    @Test
    fun providerMoneyAndNumbersFollowThePageLanguage() {
        assertEquals("US$139,40", id.usd(139_400_000))
        assertEquals("US$139.40", en.usd(139_400_000))
        assertEquals("US$170", id.usd(170_000_000, 0))
        assertEquals("US$0,04", id.usd(40_000))
        assertEquals("1.236", id.number(1_236L))
        assertEquals("8,4 dtk", id.duration(8_400, "dtk"))
        assertEquals("212 ms", id.duration(212, "dtk"))
        assertEquals("1,8 GB", id.bytes(1_932_735_283))
        assertEquals("41%", id.percent(130, 318))
        assertEquals("0%", id.percent(3, 0))
        assertEquals("+8%", id.change(412, 381))
        assertEquals("−4%", id.change(96, 100))
        assertNull(id.change(5, 0))
        assertEquals("Okt 2026", id.monthYear("2026-10"))
        assertEquals("16 Okt", id.dayMonth(LocalDate(2026, 10, 16)))
    }

    @Test
    fun monthsStepAcrossYears() {
        assertEquals("2026-09", shiftMonth("2026-10", -1))
        assertEquals("2027-01", shiftMonth("2026-12", 1))
        assertEquals("2025-12", shiftMonth("2026-01", -1))
    }

    @Test
    fun theBudgetRunsOutAtThisMonthsPace() {
        // US$139 in 16 days is about US$8.69 a day: US$170 lasts 20 days
        assertEquals(LocalDate(2026, 10, 20), budgetRunsOutOn(139_000_000, 170_000_000, LocalDate(2026, 10, 16)))
        // US$40 in 16 days: the budget lasts beyond the month
        assertNull(budgetRunsOutOn(40_000_000, 170_000_000, LocalDate(2026, 10, 16)))
        assertNull(budgetRunsOutOn(139_000_000, null, LocalDate(2026, 10, 16)))
    }

    @Test
    fun hashesOpenTheMonitoringPagesAndOverviewIsTheStart() {
        assertEquals(AdminRoute.Overview, AdminRoute.parse(""))
        assertEquals(AdminRoute.Overview, AdminRoute.parse("#/overview"))
        assertEquals(AdminRoute.ExploreAi, AdminRoute.parse("#/explore"))
        assertEquals(AdminRoute.System, AdminRoute.parse("#/system"))
        assertEquals(AdminRoute.Users, AdminRoute.parse("#/users"))
        assertEquals(AdminRoute.User("u1"), AdminRoute.parse("#/users/u1"))
    }

    @Test
    fun theOverviewLoadsForTheChosenRange() = runTest {
        val asked = mutableListOf<String?>()
        val body = """{"data":{"range":"LAST_30_DAYS","generatedAt":"2026-10-16T13:00:00Z","activeToday":412,"activeWeekAgo":381,
            |"activeLast7Days":874,"registeredUsers":1236,"newTrips":96,"newTripsPrevious":100,"searchesToday":318,"searchesTodayFromCache":130,
            |"usersOutOfCreditsToday":7,"creditsUsedToday":706,"creditsUsedTodayFree":430,"vouchersRedeemedToday":4,
            |"aiCost":{"month":"2026-10","spentMicroUsd":139000000,"projectedMicroUsd":270000000,"budgetMicroUsd":170000000,"alertPercent":80},
            |"activeByDay":[],"topDestinations":[{"name":"Bali","count":84}],"recentErrors":[],
            |"costByService":{"llmMicroUsd":88600000,"placesMicroUsd":50800000,"serverMicroUsd":24600000}}}""".trimMargin()
        val api = AdminApi(
            createHttpClient(
                MockEngine { request ->
                    asked += request.url.parameters["range"]
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            origin = "http://api.test",
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val presenter = MonitorPresenter(scope, AdminRange.LAST_7_DAYS, onAccessLost = {}) { range -> api.overview(range) }
            withContext(Dispatchers.Default) { withTimeout(5_000) { while (presenter.state.value.loading) delay(10) } }
            presenter.select(AdminRange.LAST_30_DAYS)
            withContext(Dispatchers.Default) { withTimeout(5_000) { while (presenter.state.value.loading) delay(10) } }
            assertEquals(listOf<String?>("LAST_7_DAYS", "LAST_30_DAYS"), asked)
            assertEquals(412, presenter.state.value.data?.activeToday)
            assertEquals("Bali", presenter.state.value.data?.topDestinations?.single()?.name)
        } finally {
            scope.cancel()
        }
        assertIs<ApiResult.Success<*>>(api.overview(AdminRange.TODAY))
    }
}
