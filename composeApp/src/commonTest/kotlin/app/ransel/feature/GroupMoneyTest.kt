package app.ransel.feature

import app.ransel.data.auth.SessionManager
import app.ransel.data.auth.StoredSession
import app.ransel.data.auth.TokenStore
import app.ransel.data.credit.CreditsStore
import app.ransel.data.fake.InMemoryExpenseRepository
import app.ransel.data.fake.InMemoryPreferencesRepository
import app.ransel.data.fake.InMemoryStore
import app.ransel.data.remote.RanselApi
import app.ransel.data.remote.createApiHttpClient
import app.ransel.feature.expenses.ExpensesViewModel
import app.ransel.feature.expenses.MoneyEvent
import app.ransel.feature.expenses.receipt.PersonShare
import app.ransel.feature.expenses.receipt.ReceiptFormState
import app.ransel.feature.expenses.receipt.ReceiptImage
import app.ransel.feature.expenses.receipt.ReceiptStep
import app.ransel.feature.expenses.receipt.ReceiptViewModel
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.model.Role
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.AuthSessionDto
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.ReceiptLineDto
import app.ransel.shared.dto.TransferDto
import app.ransel.shared.dto.TripDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import app.ransel.stopTestMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TRIP = "t1"
private const val ME = "u1"
private val ME_MEMBER = memberIdFor(TRIP, ME)
private val json = headersOf(HttpHeaders.ContentType, "application/json")

private val trip = TripDto(
    id = TRIP, ownerId = ME, title = "Bali 4 hari", destination = "Bali",
    startDate = LocalDate(2026, 11, 20), endDate = LocalDate(2026, 11, 23), baseCurrency = "IDR",
    createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)

private suspend fun signedIn(): SessionManager {
    val store = object : TokenStore {
        var saved: StoredSession? = null
        override suspend fun read() = saved
        override suspend fun write(session: StoredSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    return SessionManager(store) { error("not used") }.also {
        it.start(AuthSessionDto("access", Instant.parse("2099-01-01T00:00:00Z"), "refresh", MeDto(ME, "ayu@contoh.id", "Ayu")))
    }
}

private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(5_000) { while (!check()) delay(10) }
}

private val membersJson = """{"data":[
    {"tripId":"$TRIP","memberId":"$ME_MEMBER","userId":"$ME","displayName":"Ayu","role":"OWNER","joinedAt":"2026-10-01T00:00:00Z"},
    {"tripId":"$TRIP","memberId":"m-dinda","userId":"u2","displayName":"Dinda","role":"EDITOR","joinedAt":"2026-10-02T00:00:00Z"}]}"""

private const val CREDITS = """{"balance":10,"dailyFreeLeft":3,"dailyFreeLimit":3,"resetsAt":"2026-10-27T17:00:00Z","costs":{"search":3,"nextBatch":1,"placeDetail":0,"receipt":1}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMoneyViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /** Every ViewModel made here; their loads are cancelled before Main goes away, so nothing leaks into later tests. */
    private val made = mutableListOf<ExpensesViewModel>()

    @AfterTest fun tearDown() {
        made.forEach { it.viewModelScope.cancel() }
        stopTestMain()
    }

    private val log = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    private fun api(claimStatus: HttpStatusCode = HttpStatusCode.Created) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                log.update { it + (request.method.value + " " + path to request.body.toByteArray().decodeToString()) }
                when {
                    path.endsWith("/members") -> respond(membersJson, HttpStatusCode.OK, json)
                    path.endsWith("/balances") -> respond(
                        """{"data":{"baseCurrency":"IDR","members":[],"transfers":[{"fromMemberId":"$ME_MEMBER","toMemberId":"m-dinda","amountMinor":300000}]}}""",
                        HttpStatusCode.OK, json,
                    )
                    path.endsWith("/settlements") -> respond("""{"data":[]}""", HttpStatusCode.OK, json)
                    path.endsWith("/settlements/claim") ->
                        if (claimStatus == HttpStatusCode.Created) respond(
                            """{"data":{"id":"s1","tripId":"$TRIP","fromMemberId":"$ME_MEMBER","toMemberId":"m-dinda","amountMinor":300000,"settledAt":"2026-10-26T00:00:00Z","status":"CLAIMED_PAID","createdAt":"2026-10-26T00:00:00Z","updatedAt":"2026-10-26T00:00:00Z"}}""",
                            HttpStatusCode.Created, json,
                        ) else respond("""{"error":{"code":"VALIDATION_FAILED","message":"x"}}""", claimStatus, json)
                    path.endsWith("/pay-info") -> respond(
                        """{"data":{"toMemberId":"m-dinda","displayName":"Dinda","amountMinor":300000,"currency":"IDR","methods":[{"id":"p1","kind":"BANK","provider":"BCA","accountNumber":"1234567890","holderName":"Dinda","isPrimary":true,"createdAt":"2026-10-20T00:00:00Z","updatedAt":"2026-10-20T00:00:00Z"}]}}""",
                        HttpStatusCode.OK, json,
                    )
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    private suspend fun vm(api: RanselApi) =
        ExpensesViewModel(trip, InMemoryExpenseRepository(InMemoryStore()), api, signedIn(), InMemoryPreferencesRepository()).also { made += it }

    private val transfer = TransferDto(ME_MEMBER, "m-dinda", 300_000)

    private fun moneyLoaded() = log.value.any { it.first.endsWith("/settlements") }

    @Test
    fun aSharedTripLoadsItsSettlementsAndNoKitty() = runTest {
        vm(api())
        eventually { moneyLoaded() }
        assertTrue(log.value.none { "kitty" in it.first }, "the kitty is gone")
    }

    @Test
    fun payingOpensTheReceiversInfoAndClaimingClosesIt() = runTest {
        val vm = vm(api())
        eventually { moneyLoaded() }
        vm.openPayTo(transfer)
        eventually { vm.money.value.payTo?.loading == false }
        assertEquals(listOf("BCA"), vm.money.value.payTo?.info?.methods?.map { it.provider })

        vm.claim(transfer)
        assertEquals(MoneyEvent.Claimed, vm.events.first())
        eventually { vm.money.value.payTo == null && !vm.money.value.busy }
        val post = log.value.single { it.first == "POST /api/v1/trips/$TRIP/settlements/claim" }.second
        assertTrue("\"toMemberId\":\"m-dinda\"" in post && "\"amountMinor\":300000" in post, post)
    }

    @Test
    fun aRefusedClaimSaysWhyAndKeepsTheSheet() = runTest {
        val vm = vm(api(claimStatus = HttpStatusCode.UnprocessableEntity))
        eventually { moneyLoaded() }
        vm.openPayTo(transfer)
        vm.claim(transfer)
        assertEquals(MoneyEvent.Failed(ErrorCode.VALIDATION_FAILED), vm.events.first())
        assertNotNull(vm.money.value.payTo)
    }

    @Test
    fun theReminderSwitchIsRememberedOnTheDevice() = runTest {
        val prefs = InMemoryPreferencesRepository()
        val vm = ExpensesViewModel(trip, InMemoryExpenseRepository(InMemoryStore()), api(), signedIn(), prefs).also { made += it }
        vm.setIncludePayInfo(false)
        eventually { true }
        assertEquals("false", prefs.getString(ExpensesViewModel.PAY_INFO_IN_REMINDERS))
        val again = ExpensesViewModel(trip, InMemoryExpenseRepository(InMemoryStore()), api(), signedIn(), prefs).also { made += it }
        eventually { !again.money.value.includePayInfo }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = stopTestMain()

    private val wallet = CreditsDto(10, 3, 3, Instant.parse("2026-10-27T17:00:00Z"), CreditCostsDto(3, 1, 0, 1))

    private val draft = ReceiptDraftDto(
        placeName = "Warung Made", spentOn = LocalDate(2026, 11, 21), currency = "IDR",
        items = listOf(ReceiptLineDto("Nasi campur", 2, 130_000), ReceiptLineDto("Sate lilit", 2, 130_000)),
        taxMinor = 26_000, totalMinor = 286_000, credits = wallet,
    )

    private val receiptMembers = listOf("a", "b").map {
        TripMemberDto(tripId = "t1", memberId = it, displayName = it.uppercase(), role = Role.EDITOR, joinedAt = Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Test
    fun aReceiptForMyselfIsAPersonalExpense() {
        // owner, 27 Oct 2026: the receipt form offers the same "Dibagi ke" as the manual form
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        form.split!!.toggle("b")
        assertTrue(form.canSave)
        val input = form.toInput("e1", Instant.parse("2026-11-21T12:00:00Z"), null)
        assertEquals("a", input.paidByMemberId)
        assertTrue(input.splits.isEmpty(), "only the payer takes part: nobody owes anything")
    }

    @Test
    fun aReceiptCanUseSetAmountsAndSeveralPayers() {
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        val split = form.split!!
        split.useCustom(form.total, "IDR")
        split.shareTexts["a"] = "86000"
        assertFalse(form.canSave, "shares short of the total")
        split.shareTexts["b"] = "200000"
        assertTrue(form.canSave)
        split.togglePayer("b")
        val input = form.toInput("e1", Instant.parse("2026-11-21T12:00:00Z"), null)
        assertEquals(mapOf("a" to 86_000L, "b" to 200_000L), input.splits.associate { it.memberId to it.shareMinor })
        assertEquals(mapOf("a" to 143_000L, "b" to 143_000L), input.payers.associate { it.memberId to it.amountMinor })
    }

    @Test
    fun equalSplitNeedsNothingButATotal() {
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        assertTrue(form.canSave)
        val input = form.toInput("e1", Instant.parse("2026-11-21T12:00:00Z"), null)
        assertEquals(mapOf("a" to 143_000L, "b" to 143_000L), input.splits.associate { it.memberId to it.shareMinor })
        // the lines are kept to be shown on the expense, without members: they do not decide the split
        assertEquals(listOf("Nasi campur", "Sate lilit", "Pajak"), input.items.map { it.name })
        assertTrue(input.items.all { it.memberIds.isEmpty() })
        assertFalse(input.itemsSplit)
        assertEquals("Warung Made", input.note)
    }

    @Test
    fun perItemSendsTheLinesAndSpreadsTheTax() {
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        form.perItem = true
        form.toggle(1, "a") // Sate only for b
        val input = form.toInput("e1", Instant.parse("2026-11-21T12:00:00Z"), null)
        assertEquals(3, input.items.size)
        assertEquals("Pajak", input.items.last().name)
        assertTrue(input.itemsSplit)
        assertTrue(input.items.last().memberIds.isEmpty())
        // a: 65.000 + 6.500 tax, b: 65.000 + 130.000 + 19.500 tax
        assertEquals(mapOf("a" to 71_500L, "b" to 214_500L), input.splits.associate { it.memberId to it.shareMinor })
    }

    @Test
    fun perItemWaitsUntilTheLinesMatchTheTotal() {
        val form = ReceiptFormState(draft.copy(totalMinor = 300_000), receiptMembers, "a", "Pajak")
        form.perItem = true
        assertEquals(-14_000L, form.difference)
        assertFalse(form.canSave)
        form.perItem = false
        assertTrue(form.canSave, "an equal split does not need the lines")
    }

    @Test
    fun taxAlreadyInThePricesIsANoteNotALine() {
        // Tous Les Jours: latte 35.000 for a, bread 40.000 for b, TOTAL 75.000 "Incl. Pb1 10% 6.818"
        val included = ReceiptDraftDto(
            spentOn = LocalDate(2017, 12, 3), currency = "IDR",
            items = listOf(ReceiptLineDto("Cafe latte", 1, 35_000), ReceiptLineDto("Creamcheese walnut bread", 1, 40_000)),
            taxMinor = 6_818, totalMinor = 75_000, credits = wallet,
        )
        val form = ReceiptFormState(included, receiptMembers, "a", "Pajak")
        assertEquals(6_818L, form.includedExtrasMinor)
        assertEquals(-1, form.extrasIndex)
        form.perItem = true
        form.toggle(0, "b") // latte only for a
        form.toggle(1, "a") // bread only for b
        assertEquals(0L, form.difference)
        assertTrue(form.canSave)
        val input = form.toInput("e1", Instant.parse("2026-11-21T12:00:00Z"), null)
        assertEquals(mapOf("a" to 35_000L, "b" to 40_000L), input.splits.associate { it.memberId to it.shareMinor })
        assertEquals(listOf(0L, 0L), form.perPerson().map { it.extrasMinor })
    }

    @Test
    fun eachPersonsShareShowsTheirPartOfTheTax() {
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        assertEquals(0L, form.includedExtrasMinor, "286.000 = items + tax: the tax comes on top")
        form.perItem = true
        form.toggle(1, "a") // Sate only for b
        assertEquals(
            listOf(PersonShare("a", 71_500, 6_500), PersonShare("b", 214_500, 19_500)),
            form.perPerson(),
        )
    }

    @Test
    fun theReceiptDateCanBeFixed() {
        val form = ReceiptFormState(draft.copy(spentOn = LocalDate(2017, 12, 3)), receiptMembers, "a", "Pajak")
        assertEquals(LocalDate(2017, 12, 3), form.date, "the date read is kept, also outside the trip")
        form.date = LocalDate(2026, 11, 21)
        assertEquals(LocalDate(2026, 11, 21), form.date)
    }

    @Test
    fun aLineNobodySharesBlocksSaving() {
        val form = ReceiptFormState(draft, receiptMembers, "a", "Pajak")
        form.perItem = true
        form.toggle(0, "a")
        form.toggle(0, "b")
        assertFalse(form.canSave)
    }

    private fun readerApi(status: HttpStatusCode, body: String) = RanselApi(
        createApiHttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/credits") -> respond("""{"data":$CREDITS}""", HttpStatusCode.OK, json)
                    request.method == HttpMethod.Post -> respond(body, status, json)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            },
        ),
        origin = "http://api.test",
    )

    @Test
    fun aReadReceiptBecomesADraftAndUpdatesTheWallet() = runTest {
        val body = """{"data":{"placeName":"Warung Made","currency":"IDR","items":[],"totalMinor":50000,"credits":$CREDITS}}"""
        val store = CreditsStore()
        val vm = ReceiptViewModel(trip, readerApi(HttpStatusCode.OK, body), store)
        vm.read(ReceiptImage(ByteArray(10), "image/jpeg"))
        eventually { vm.step.value is ReceiptStep.Draft }
        assertEquals(50_000L, (vm.step.value as ReceiptStep.Draft).draft.totalMinor)
        assertEquals(1, store.state.value?.costs?.receipt)
    }

    @Test
    fun anUnreadableReceiptSaysSoAndATooLargeOneIsNotSent() = runTest {
        val vm = ReceiptViewModel(
            trip,
            readerApi(HttpStatusCode.UnprocessableEntity, """{"error":{"code":"RECEIPT_UNREADABLE","message":"x"}}"""),
            CreditsStore(),
        )
        vm.read(ReceiptImage(ByteArray(10), "image/jpeg"))
        eventually { vm.step.value is ReceiptStep.Failed }
        assertEquals(ReceiptStep.Failed(ErrorCode.RECEIPT_UNREADABLE), vm.step.value)

        vm.retry()
        vm.read(ReceiptImage(ByteArray(5 * 1024 * 1024 + 1), "image/jpeg"))
        assertEquals(ReceiptStep.Failed(ErrorCode.FILE_TOO_LARGE), vm.step.value)
    }
}
