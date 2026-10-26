package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.KittyDepositDto
import app.ransel.shared.dto.KittyDto
import app.ransel.shared.dto.PaymentMethodInput
import app.ransel.shared.dto.SettlementDto
import app.ransel.shared.model.PaymentMethodKind
import app.ransel.shared.model.PollPriceUnit
import app.ransel.shared.model.SettlementStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GroupMoneyTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val members = listOf("septian", "dinda", "raka", "maya")

    private fun expense(
        id: String,
        amount: Long,
        paidBy: String,
        shares: Map<String, Long>,
        fromKitty: Boolean = false,
        estimate: Boolean = false,
    ) = ExpenseDto(
        id = id, tripId = "t", amountMinor = amount, currency = "IDR", spentAt = now, paidByMemberId = paidBy,
        splits = shares.map { ExpenseSplitDto(it.key, it.value) }, paidFromKitty = fromKitty, isEstimate = estimate,
        createdAt = now, updatedAt = now,
    )

    private fun deposit(id: String, member: String, amount: Long, treasurer: String = "dinda") =
        KittyDepositDto(id, "t", member, amount, treasurer, now, now, now)

    private fun settlement(from: String, to: String, amount: Long, status: SettlementStatus) =
        SettlementDto("s-$from", "t", from, to, amount, now, status, createdAt = now, updatedAt = now)

    private val kitty = KittyDto("t", 500_000, "dinda", now, now)

    @Test
    fun kittyLeftoverIsOwedByTheTreasurerToTheDepositors() {
        // everyone puts in 500.000 with Dinda; dinner 600.000 from the kitty for Septian, Dinda and Raka (Maya skipped it)
        val deposits = members.mapIndexed { i, m -> deposit("d$i", m, 500_000) }
        val dinner = expense("e1", 600_000, "dinda", mapOf("septian" to 200_000L, "dinda" to 200_000L, "raka" to 200_000L), fromKitty = true)

        val balances = tripBalances(members, listOf(dinner), emptyList(), deposits, "IDR").associateBy { it.memberId }

        assertEquals(300_000L, balances.getValue("septian").balanceMinor)
        assertEquals(300_000L, balances.getValue("raka").balanceMinor)
        assertEquals(500_000L, balances.getValue("maya").balanceMinor)
        // Dinda holds 2.000.000 − 600.000 = 1.400.000 that belongs to the others (minus her own 300.000 left)
        assertEquals(-1_100_000L, balances.getValue("dinda").balanceMinor)
        assertEquals(0L, balances.values.sumOf { it.balanceMinor })
    }

    @Test
    fun theTreasurersOwnDepositMovesNothing() {
        val balances = tripBalances(members, emptyList(), emptyList(), listOf(deposit("d1", "dinda", 500_000)), "IDR")
        assertTrue(balances.all { it.balanceMinor == 0L })
    }

    @Test
    fun estimatesAndUnconfirmedClaimsStayOutOfBalances() {
        val stay = expense("e1", 6_750_000, "septian", members.associateWith { 1_687_500L }, estimate = true)
        val lunch = expense("e2", 100_000, "septian", mapOf("septian" to 50_000L, "raka" to 50_000L))
        val claimed = settlement("raka", "septian", 50_000, SettlementStatus.CLAIMED_PAID)

        val balances = tripBalances(members, listOf(stay, lunch), listOf(claimed), emptyList(), "IDR").associateBy { it.memberId }
        assertEquals(50_000L, balances.getValue("septian").balanceMinor)
        assertEquals(-50_000L, balances.getValue("raka").balanceMinor)

        val confirmed = tripBalances(members, listOf(stay, lunch), listOf(claimed.copy(status = SettlementStatus.CONFIRMED)), emptyList(), "IDR")
        assertTrue(confirmed.all { it.balanceMinor == 0L })
    }

    @Test
    fun theSummaryShowsEstimatesApartFromWhatWasSpent() {
        val stay = expense("e1", 6_750_000, "septian", emptyMap(), estimate = true)
        val lunch = expense("e2", 100_000, "septian", emptyMap())
        val summary = summarizeExpenses("IDR", 10_000_000, listOf(stay, lunch), kotlinx.datetime.TimeZone.UTC)
        assertEquals(100_000L, summary.spentMinor)
        assertEquals(6_750_000L, summary.estimatedMinor)
        assertEquals(9_900_000L, summary.remainingMinor)
    }

    @Test
    fun kittySummaryCountsDepositsAndKittyExpensesOnly() {
        val deposits = listOf(deposit("d1", "septian", 500_000), deposit("d2", "dinda", 500_000), deposit("d3", "raka", 200_000))
        val expenses = listOf(
            expense("e1", 600_000, "dinda", emptyMap(), fromKitty = true),
            expense("e2", 90_000, "septian", emptyMap()),
            expense("e3", 1_000_000, "dinda", emptyMap(), fromKitty = true, estimate = true),
        )
        val summary = kittySummary(kitty, deposits, expenses, members, "IDR")

        assertEquals(1_200_000L, summary.collectedMinor)
        assertEquals(600_000L, summary.spentMinor)
        assertEquals(600_000L, summary.leftMinor)
        assertEquals(listOf(true, true, false, false), summary.members.map { it.done })
        assertEquals(200_000L, summary.members.first { it.memberId == "raka" }.depositedMinor)
    }

    @Test
    fun kittyExpensesAreSharedByTheDepositorsByDefault() {
        assertEquals(listOf("septian", "raka"), kittyShareMemberIds(listOf(deposit("d1", "raka", 1), deposit("d2", "septian", 1)), members))
        assertEquals(members, kittyShareMemberIds(emptyList(), members))
    }

    @Test
    fun receiptItemsAreSplitPerItemAndTaxInProportion() {
        // the HasilStruk example: 845.000 with 80.000 tax spread by what each person had
        val items = listOf(
            ExpenseItemDto("Nasi campur ×2", 130_000, listOf("septian", "dinda")),
            ExpenseItemDto("Ikan bakar 1 kg", 385_000, members),
            ExpenseItemDto("Es kelapa ×4", 120_000, members),
            ExpenseItemDto("Sate lilit ×2", 130_000, listOf("raka", "maya")),
            ExpenseItemDto("Pajak 10%", 80_000),
        )
        val shares = splitByItems(items, payerId = "septian")

        // everyone had items worth 191.250, so everyone carries a quarter of the tax
        assertEquals(members.associateWith { 211_250L }, shares)
        assertEquals(itemsTotal(items), shares.values.sum())
    }

    @Test
    fun taxRoundingGoesToThePayer() {
        val items = listOf(
            ExpenseItemDto("Teh", 100, listOf("a")),
            ExpenseItemDto("Kopi", 200, listOf("b")),
            ExpenseItemDto("Pajak", 10),
        )
        val shares = splitByItems(items, payerId = "a")
        assertEquals(mapOf("a" to 104L, "b" to 206L), shares)
    }

    @Test
    fun withoutExtrasItemsAreSplitEquallyPerItem() {
        val shares = splitByItems(listOf(ExpenseItemDto("Kopi", 100, listOf("a", "b", "c"))), payerId = "b")
        assertEquals(mapOf("a" to 33L, "b" to 34L, "c" to 33L), shares)
    }

    @Test
    fun stayEstimateFollowsThePriceUnit() {
        assertEquals(6_750_000L, stayEstimateMinor(2_250_000, PollPriceUnit.NIGHT, nights = 3, people = 5))
        assertEquals(2_250_000L, stayEstimateMinor(450_000, PollPriceUnit.PERSON, nights = 3, people = 5))
        assertEquals(900_000L, stayEstimateMinor(900_000, PollPriceUnit.TOTAL, nights = 3, people = 5))
        assertEquals(500_000L, stayEstimateMinor(500_000, null, nights = 0, people = 0))
    }

    @Test
    fun paymentMethodsNeedAProviderANumberAndAHolder() {
        assertEquals(emptyMap(), validatePaymentMethod(PaymentMethodInput(PaymentMethodKind.BANK, "BCA", "1234 5678 90", "Septian Nugraha")))
        assertEquals(emptyMap(), validatePaymentMethod(PaymentMethodInput(PaymentMethodKind.EWALLET, "GoPay", "0812-3456-7890", "Septian")))
        val bad = validatePaymentMethod(PaymentMethodInput(PaymentMethodKind.BANK, " ", "12ab", ""))
        assertEquals(mapOf("provider" to "REQUIRED", "accountNumber" to "INVALID", "holderName" to "REQUIRED"), bad)
    }
}
