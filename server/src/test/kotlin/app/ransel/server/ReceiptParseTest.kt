package app.ransel.server

import app.ransel.server.ai.LlmOutput
import app.ransel.server.ai.LlmReceipt
import app.ransel.server.ai.LlmReceiptLine
import app.ransel.server.ai.Prompt
import app.ransel.server.service.ReceiptService
import app.ransel.shared.dto.CreditCostsDto
import app.ransel.shared.dto.CreditsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Receipt reading: prompt, lenient parse, and the draft the app fills in. No database. */
class ReceiptParseTest {
    private val wallet = CreditsDto(10, 3, 3, Instant.parse("2026-11-10T17:00:00Z"), CreditCostsDto(3, 1, 0, 1))

    @Test
    fun thePromptAsksForLinesTaxServiceAndDiscountAndForbidsInventedLines() {
        val prompt = Prompt.receiptSystem()
        assertTrue("discount" in prompt)
        assertTrue("service" in prompt)
        assertTrue("PB1" in prompt)
        assertTrue("Do not translate, merge, split, or invent a line." in prompt)
        assertTrue("null when the photo is not a readable receipt" in prompt)
    }

    @Test
    fun printedAmountsKeepIndonesianThousandsAndRealDecimals() {
        assertEquals(130_000.0, LlmOutput.parsePrintedAmount("130.000"))
        assertEquals(130_000.0, LlmOutput.parsePrintedAmount("Rp130.000"))
        assertEquals(1_250_000.0, LlmOutput.parsePrintedAmount("1.250.000"))
        assertEquals(1_250.5, LlmOutput.parsePrintedAmount("1.250,50"))
        assertEquals(1_250.5, LlmOutput.parsePrintedAmount("1,250.50"))
        assertEquals(12.5, LlmOutput.parsePrintedAmount("12.50"))
        assertEquals(10.5, LlmOutput.parsePrintedAmount("10,5"))
        assertNull(LlmOutput.parsePrintedAmount("gratis"))
    }

    @Test
    fun stringAmountsAndSummaryLinesBecomeItemsTaxServiceAndDiscount() {
        val raw = """
            ```json
            Here is the receipt:
            {"placeName":" Warung Made ","date":"2026-11-21","currency":"idr","items":[
              {"name":"Nasi campur","quantity":"2","amount":"130.000"},
              {"name":"Es teh","quantity":1,"amount":"15.000"},
              {"name":"Es teh","quantity":1,"amount":15000},
              {"name":"Subtotal","amount":160000},
              {"name":"PB1 10%","amount":"16.000"},
              {"name":"Service Charge 5%","amount":8000},
              {"name":"Diskon","amount":"10.000"},
              {"name":"Total","amount":189000}
            ],"tax":0,"service":0,"discount":0,"total":"189.000"}
            ```
        """.trimIndent()
        val read = LlmOutput.parseReceipt(raw)
        assertEquals("Warung Made", read.placeName)
        assertEquals("IDR", read.currency)
        assertEquals("2026-11-21", read.date)
        assertEquals(listOf("Nasi campur", "Es teh", "Es teh"), read.items.map { it.name })
        assertEquals(listOf(130_000.0, 15_000.0, 15_000.0), read.items.map { it.amount })
        assertEquals(2, read.items.first().quantity)
        assertEquals(16_000.0, read.tax)
        assertEquals("10%", read.taxRate, "the printed rate, not the rupiah amount")
        assertEquals(8_000.0, read.service)
        assertEquals(10_000.0, read.discount)
        assertEquals(189_000.0, read.total)
        val draft = ReceiptService.toDraft(read, "IDR", wallet)
        // 130.000 + 15.000 + 15.000 + 16.000 tax + 8.000 service - 10.000 discount = 174.000, not 189.000,
        // so the discount is left out of the lines and the app can show the gap.
        assertEquals(16_000L, draft.taxMinor)
        assertEquals("10%", draft.includedTaxRate)
        assertEquals(8_000L, draft.serviceMinor)
        assertEquals(189_000L, draft.totalMinor)
        assertEquals(3, draft.items.size)
    }

    @Test
    fun aDiscountThatExplainsTheGapComesOffServiceThenTaxAndNoItemIsInvented() {
        val read = LlmReceipt(
            currency = "IDR",
            items = listOf(LlmReceiptLine("Nasi campur", 1, 250_000.0)),
            tax = 25_000.0,
            service = 12_500.0,
            discount = 10_000.0,
            total = 277_500.0,
        )
        val draft = ReceiptService.toDraft(read, "IDR", wallet)
        assertEquals(listOf("Nasi campur"), draft.items.map { it.name })
        assertEquals(250_000L, draft.items.single().amountMinor)
        assertEquals(25_000L, draft.taxMinor)
        assertEquals(2_500L, draft.serviceMinor, "10.000 discount comes off service first")
        assertEquals(277_500L, draft.totalMinor)
        assertEquals(draft.totalMinor, draft.items.sumOf { it.amountMinor } + draft.taxMinor + draft.serviceMinor)
    }

    @Test
    fun includedTaxStaysBesideTheItemsAndAnEmptyReceiptInventsNothing() {
        val pb1 = LlmReceipt(
            currency = "IDR",
            items = listOf(LlmReceiptLine("Cafe latte", 1, 35_000.0), LlmReceiptLine("Bread", 1, 40_000.0)),
            tax = 6818.0,
            discount = 6818.0,
            total = 75_000.0,
        )
        val included = ReceiptService.toDraft(pb1, "IDR", wallet)
        assertEquals(75_000L, included.items.sumOf { it.amountMinor })
        assertEquals(6_818L, included.taxMinor, "Incl. Pb1 is not subtracted and not turned into a new line")
        assertEquals(null, included.includedTaxRate)
        assertEquals(75_000L, included.totalMinor)
        assertEquals(2, included.items.size)

        val onlyTotal = LlmOutput.parseReceipt("""{"placeName":"Warung","currency":"IDR","items":[],"total":50000}""")
        assertTrue(onlyTotal.items.isEmpty())
        assertEquals(50_000.0, onlyTotal.total)
        val draft = ReceiptService.toDraft(onlyTotal, "IDR", wallet)
        assertTrue(draft.items.isEmpty())
        assertEquals(0L, draft.taxMinor)
        assertEquals(50_000L, draft.totalMinor)

        val unread = LlmOutput.parseReceipt("""{"items":[{"name":"??","amount":null}],"total":null}""")
        assertTrue(unread.items.isEmpty())
        assertNull(unread.total)
    }

    @Test
    fun usdDecimalsSurviveTheDraftAndAGuessedSummaryLineDoesNot() {
        val read = LlmOutput.parseReceipt(
            """{"currency":"USD","items":[{"name":"Coffee","amount":"12.50"},{"name":"Tax","amount":"1.25"}],"total":13.75}""",
        )
        assertEquals(listOf("Coffee"), read.items.map { it.name })
        assertEquals(1.25, read.tax)
        val draft = ReceiptService.toDraft(read, "IDR", wallet)
        assertEquals("USD", draft.currency)
        assertEquals(1_250L, draft.items.single().amountMinor)
        assertEquals(125L, draft.taxMinor)
        assertEquals(1_375L, draft.totalMinor)
    }
}
