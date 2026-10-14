package app.ransel.ui

import app.ransel.ui.format.formatMoney
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    @Test fun idrHasNoDecimals() = assertEquals("Rp50.000", formatMoney(50_000, "IDR"))
    @Test fun idrGroupsMillions() = assertEquals("Rp5.000.000", formatMoney(5_000_000, "IDR"))
    @Test fun usdHasTwoDecimals() = assertEquals("US$12,50", formatMoney(1_250, "USD"))
    @Test fun smallUsdKeepsLeadingZero() = assertEquals("US$0,05", formatMoney(5, "USD"))
    @Test fun negativeKeepsSignBeforeSymbol() = assertEquals("-Rp1.500", formatMoney(-1_500, "IDR"))
    @Test fun unknownCurrencyUsesCode() = assertEquals("THB\u00A010,00", formatMoney(1_000, "THB"))
}

class ParseAmountTest {
    @kotlin.test.Test fun idrIgnoresSeparators() = assertEquals(85_000L, app.ransel.ui.format.parseAmountMinor("85.000", "IDR"))
    @kotlin.test.Test fun usdWithComma() = assertEquals(1_250L, app.ransel.ui.format.parseAmountMinor("12,50", "USD"))
    @kotlin.test.Test fun usdWithDotAndOneDecimal() = assertEquals(1_250L, app.ransel.ui.format.parseAmountMinor("12.5", "USD"))
    @kotlin.test.Test fun usdWholeNumber() = assertEquals(1_200L, app.ransel.ui.format.parseAmountMinor("12", "USD"))
    @kotlin.test.Test fun emptyIsNull() = assertEquals(null, app.ransel.ui.format.parseAmountMinor("  ", "IDR"))
    @kotlin.test.Test fun extraDecimalsAreTruncated() = assertEquals(1_234L, app.ransel.ui.format.parseAmountMinor("12,349", "USD"))
}
