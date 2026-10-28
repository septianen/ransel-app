package app.ransel.shared.domain

import app.ransel.shared.dto.ExpenseDto
import app.ransel.shared.dto.ExpenseItemDto
import app.ransel.shared.dto.ExpenseSplitDto
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
        estimate: Boolean = false,
    ) = ExpenseDto(
        id = id, tripId = "t", amountMinor = amount, currency = "IDR", spentAt = now, paidByMemberId = paidBy,
        splits = shares.map { ExpenseSplitDto(it.key, it.value) }, isEstimate = estimate,
        createdAt = now, updatedAt = now,
    )

    private fun settlement(from: String, to: String, amount: Long, status: SettlementStatus) =
        SettlementDto("s-$from", "t", from, to, amount, now, status, createdAt = now, updatedAt = now)

    @Test
    fun estimatesAndUnconfirmedClaimsStayOutOfBalances() {
        val stay = expense("e1", 6_750_000, "septian", members.associateWith { 1_687_500L }, estimate = true)
        val lunch = expense("e2", 100_000, "septian", mapOf("septian" to 50_000L, "raka" to 50_000L))
        val claimed = settlement("raka", "septian", 50_000, SettlementStatus.CLAIMED_PAID)

        val balances = tripBalances(members, listOf(stay, lunch), listOf(claimed), "IDR").associateBy { it.memberId }
        assertEquals(50_000L, balances.getValue("septian").balanceMinor)
        assertEquals(-50_000L, balances.getValue("raka").balanceMinor)

        val confirmed = tripBalances(members, listOf(stay, lunch), listOf(claimed.copy(status = SettlementStatus.CONFIRMED)), "IDR")
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
    fun extrasOnFreeItemsAreSplitEqually() {
        val items = listOf(
            ExpenseItemDto("Air putih", 0, listOf("a", "b")),
            ExpenseItemDto("Kerupuk gratis", 0, listOf("c")),
            ExpenseItemDto("Biaya layanan", 10),
        )
        val shares = splitByItems(items, payerId = "b")
        assertEquals(mapOf("a" to 3L, "b" to 4L, "c" to 3L), shares)
    }

    @Test
    fun itemSharesAlwaysAddUpToTheReceipt() {
        val people = listOf("a", "b", "c", "d", "e")
        val random = kotlin.random.Random(7)
        repeat(500) {
            val items = List(random.nextInt(1, 7)) { i ->
                val members = people.shuffled(random).take(random.nextInt(0, people.size + 1))
                ExpenseItemDto("item $i", random.nextLong(0, 500_000), members)
            }.let { if (it.none { item -> item.memberIds.isNotEmpty() }) it + ExpenseItemDto("x", 0, listOf("a")) else it }
            val payer = people[random.nextInt(people.size)]
            val shares = splitByItems(items, payer)
            assertEquals(itemsTotal(items), shares.values.sum(), "$items paid by $payer")
            assertTrue(shares.values.all { it >= 0 }, "$items paid by $payer")
        }
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
