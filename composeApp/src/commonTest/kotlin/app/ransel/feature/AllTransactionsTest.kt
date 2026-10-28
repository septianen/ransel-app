package app.ransel.feature

import androidx.lifecycle.viewModelScope
import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.fake.InMemoryExpenseRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.fake.InMemoryTripRepository
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.expenses.AllTransactionsViewModel
import app.ransel.feature.expenses.ExpenseDayGroup
import app.ransel.feature.expenses.latest
import app.ransel.feature.expenses.TransactionFilter
import app.ransel.feature.expenses.TransactionItem
import app.ransel.feature.expenses.transactionDays
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpensePayerDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.SettlementStatus
import app.ransel.stopTestMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TRIP = "t1"
private const val ME = "u1"
private val ME_MEMBER = memberIdFor(TRIP, ME)
private val WITA = TimeZone.of("Asia/Makassar")
private val stamp = Instant.parse("2026-10-01T00:00:00Z")

private fun expense(
    id: String,
    at: String,
    amount: Long,
    payer: String = ME_MEMBER,
    splits: List<String> = emptyList(),
    currency: String = "IDR",
    fx: String? = null,
    payers: List<String> = emptyList(),
) = ExpenseDto(
    id = id, tripId = TRIP, amountMinor = amount, currency = currency, fxRateToBase = fx, spentAt = Instant.parse(at),
    paidByMemberId = payer, payers = payers.map { ExpensePayerDto(it, amount / payers.size) },
    splits = splits.map { ExpenseSplitDto(it, amount / splits.size) }, createdAt = stamp, updatedAt = stamp,
)

private fun settlement(id: String, at: String, from: String, to: String, amount: Long, status: SettlementStatus = SettlementStatus.CONFIRMED, deleted: Boolean = false) =
    SettlementDto(
        id = id, tripId = TRIP, fromMemberId = from, toMemberId = to, amountMinor = amount, settledAt = Instant.parse(at),
        status = status, createdAt = stamp, updatedAt = stamp, deletedAt = if (deleted) stamp else null,
    )

class TransactionDaysTest {
    // 20 Nov 23:30 WITA is still the 20th there, although it is the 20th 15:30 UTC
    private val expenses = listOf(
        expense("lunch", "2026-11-20T04:30:00Z", 85_000, splits = listOf(ME_MEMBER, "m-dinda")),
        expense("late", "2026-11-20T15:30:00Z", 40_000, payer = "m-dinda", splits = listOf("m-dinda", "m-rina")),
        expense("car", "2026-11-21T01:00:00Z", 450_000, payer = "m-dinda", splits = listOf(ME_MEMBER, "m-dinda")),
        // 10 USD at 16 000 = Rp160.000
        expense("usd", "2026-11-21T02:00:00Z", 1_000, payer = "m-rina", currency = "USD", fx = "16000"),
    )
    private val settlements = listOf(
        settlement("s1", "2026-11-21T10:00:00Z", ME_MEMBER, "m-dinda", 182_500),
        settlement("s2", "2026-11-22T10:00:00Z", "m-rina", "m-dinda", 20_000, SettlementStatus.CLAIMED_PAID),
        settlement("gone", "2026-11-22T11:00:00Z", "m-rina", ME_MEMBER, 1, deleted = true),
    )

    private fun days(filter: TransactionFilter, me: String? = ME_MEMBER) = transactionDays(expenses, settlements, filter, me, "IDR", WITA)

    @Test
    fun theExpensesTabKeepsOnlyTheNewestTransactions() {
        val day1 = ExpenseDayGroup(LocalDate(2026, 11, 22), 300, listOf(expense("a", "2026-11-22T03:00:00Z", 100), expense("b", "2026-11-22T02:00:00Z", 200)))
        val day2 = ExpenseDayGroup(LocalDate(2026, 11, 21), 600, listOf(expense("c", "2026-11-21T03:00:00Z", 100), expense("d", "2026-11-21T02:00:00Z", 500)))
        val day3 = ExpenseDayGroup(LocalDate(2026, 11, 20), 50, listOf(expense("e", "2026-11-20T03:00:00Z", 50)))
        val shown = listOf(day1, day2, day3).latest(3)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), shown.map { g -> g.items.map { it.id } })
        assertEquals(600, shown[1].totalMinor) // the day's total stays whole
        assertEquals(3, listOf(day1, day2, day3).latest(10).size)
    }

    @Test
    fun allIsGroupedByTripDayNewestFirst() {
        val days = days(TransactionFilter.ALL)
        assertEquals(listOf(LocalDate(2026, 11, 22), LocalDate(2026, 11, 21), LocalDate(2026, 11, 20)), days.map { it.date })
        assertEquals(listOf("s1", "usd", "car"), days[1].items.map { it.id })
        assertEquals(listOf("late", "lunch"), days[2].items.map { it.id })
        assertTrue(days.flatMap { it.items }.none { it.id == "gone" }, "deleted settlements are left out")
    }

    @Test
    fun dayTotalsAreInTheBaseCurrencyAndKeepSpendingApartFromPayments() {
        val day = days(TransactionFilter.ALL).single { it.date == LocalDate(2026, 11, 21) }
        assertEquals(450_000 + 160_000, day.spentMinor)
        assertEquals(182_500, day.paidMinor)
    }

    @Test
    fun expensesAndPaymentsChipsKeepOneKind() {
        assertTrue(days(TransactionFilter.EXPENSES).flatMap { it.items }.all { it is TransactionItem.Expense })
        assertEquals(4, days(TransactionFilter.EXPENSES).sumOf { it.items.size })
        val payments = days(TransactionFilter.PAYMENTS)
        assertEquals(listOf("s2", "s1"), payments.flatMap { it.items }.map { it.id })
        assertTrue(payments.all { it.spentMinor == 0L })
    }

    @Test
    fun mineKeepsWhatIPaidSharedOrSettled() {
        val ids = days(TransactionFilter.MINE).flatMap { it.items }.map { it.id }
        assertEquals(listOf("s1", "car", "lunch"), ids)
        // one of several payers counts too
        val joint = expense("joint", "2026-11-23T01:00:00Z", 100_000, payer = "m-dinda", payers = listOf("m-dinda", ME_MEMBER))
        val withJoint = transactionDays(listOf(joint), emptyList(), TransactionFilter.MINE, ME_MEMBER, "IDR", WITA)
        assertEquals(listOf("joint"), withJoint.flatMap { it.items }.map { it.id })
    }

    @Test
    fun mineWithoutAMemberKeepsEverything() {
        assertEquals(days(TransactionFilter.ALL), days(TransactionFilter.MINE, me = null))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AllTransactionsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val made = mutableListOf<AllTransactionsViewModel>()

    @AfterTest fun tearDown() {
        made.forEach { it.viewModelScope.cancel() }
        stopTestMain()
    }

    private val trip = TripDto(
        id = TRIP, ownerId = ME, title = "Bali", destination = "Bali", startDate = null, endDate = null,
        baseCurrency = "IDR", timeZone = "Asia/Makassar", createdAt = stamp, updatedAt = stamp,
    )
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun session(signedIn: Boolean): SessionManager {
        val store = object : TokenStore {
            var saved: StoredSession? = null
            override suspend fun read() = saved
            override suspend fun write(session: StoredSession) { saved = session }
            override suspend fun clear() { saved = null }
        }
        return SessionManager(store) { error("not used") }
    }

    private suspend fun SessionManager.signIn() =
        start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))

    private fun api(settlementsStatus: HttpStatusCode = HttpStatusCode.OK) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/members") -> respond(
                        """{"data":[
                        {"tripId":"$TRIP","memberId":"$ME_MEMBER","userId":"$ME","displayName":"Ayu","role":"OWNER","joinedAt":"2026-10-01T00:00:00Z"},
                        {"tripId":"$TRIP","memberId":"m-dinda","userId":"u2","displayName":"Dinda","role":"EDITOR","joinedAt":"2026-10-02T00:00:00Z"}]}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/settlements") ->
                        if (settlementsStatus == HttpStatusCode.OK) respond(
                            """{"data":[{"id":"s1","tripId":"$TRIP","fromMemberId":"$ME_MEMBER","toMemberId":"m-dinda","amountMinor":50000,"settledAt":"2026-11-21T10:00:00Z","status":"CONFIRMED","createdAt":"2026-11-21T10:00:00Z","updatedAt":"2026-11-21T10:00:00Z"}]}""",
                            HttpStatusCode.OK, json,
                        ) else respond("""{"error":{"code":"INTERNAL","message":"x"}}""", settlementsStatus, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun store() = InMemoryStore().also {
        it.trips.value = listOf(trip)
        it.expenses.value = listOf(
            expense("e1", "2026-11-20T04:30:00Z", 85_000, splits = listOf(ME_MEMBER, "m-dinda")),
            expense("e2", "2026-11-21T04:30:00Z", 30_000, payer = "m-dinda", splits = listOf("m-dinda")),
        )
    }

    private fun vm(store: InMemoryStore, session: SessionManager, api: RanselApi) =
        AllTransactionsViewModel(TRIP, InMemoryTripRepository(store), InMemoryExpenseRepository(store), api, session)
            .also { made += it }
            .also { it.state.launchIn(it.viewModelScope) }

    @Test
    fun aGuestSeesOnlyExpensesAndNoPaymentChips() = runTest {
        val vm = vm(store(), session(signedIn = false), api())
        eventually { !vm.state.value.loading }
        val state = vm.state.value
        assertFalse(state.shared)
        assertEquals(listOf("e2", "e1"), state.days.flatMap { it.items }.map { it.id })
        // a chip that needs members falls back to all
        vm.setFilter(TransactionFilter.PAYMENTS)
        assertEquals(TransactionFilter.ALL, vm.state.value.filter)
        assertEquals(2, vm.state.value.count)
    }

    @Test
    fun aSharedTripListsPaymentsAndFilters() = runTest {
        val session = session(signedIn = true).also { it.signIn() }
        val vm = vm(store(), session, api())
        eventually { vm.state.value.count == 3 }
        assertTrue(vm.state.value.shared)
        assertEquals(115_000, vm.state.value.spentMinor)
        assertEquals(50_000, vm.state.value.paidMinor)

        vm.setFilter(TransactionFilter.PAYMENTS)
        assertEquals(listOf("s1"), vm.state.value.days.flatMap { it.items }.map { it.id })

        vm.setFilter(TransactionFilter.MINE)
        assertEquals(listOf("s1", "e1"), vm.state.value.days.flatMap { it.items }.map { it.id })
    }

    @Test
    fun offlineSettlementsLeaveTheExpensesAndSaySo() = runTest {
        val session = session(signedIn = true).also { it.signIn() }
        val vm = vm(store(), session, api(settlementsStatus = HttpStatusCode.InternalServerError))
        eventually { vm.state.value.paymentsUnavailable }
        assertEquals(2, vm.state.value.count)
    }

    @Test
    fun editingAndDeletingGoThroughTheRepository() = runTest {
        val store = store()
        val vm = vm(store, session(signedIn = false), api())
        eventually { vm.state.value.count == 2 }
        vm.delete("e1")
        eventually { vm.state.value.count == 1 }
        assertEquals(listOf("e2"), vm.state.value.days.flatMap { it.items }.map { it.id })
    }
}
