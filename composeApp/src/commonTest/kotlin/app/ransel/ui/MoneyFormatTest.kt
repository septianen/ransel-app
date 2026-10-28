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

class SanitizeAmountTest {
    @kotlin.test.Test fun zeroDecimalKeepsDigitsOnly() =
        assertEquals("100000", app.ransel.ui.format.sanitizeAmountInput("100.000", 0))
    @kotlin.test.Test fun decimalCurrencyNormalisesSeparator() =
        assertEquals("12,5", app.ransel.ui.format.sanitizeAmountInput("12.5", 2))
    @kotlin.test.Test fun fractionIsLimited() =
        assertEquals("12,34", app.ransel.ui.format.sanitizeAmountInput("12,349", 2))
    @kotlin.test.Test fun secondSeparatorIsDropped() {
        // Typing a second "," or "." must not change the number: only the first separator counts.
        assertEquals("12,5", app.ransel.ui.format.sanitizeAmountInput("12,5,", 2))
        assertEquals("1,23", app.ransel.ui.format.sanitizeAmountInput("1,234,5", 2))
    }
}

class FxRateInputTest {
    @Test fun storedRateShowsWithAComma() = assertEquals("16250,5", app.ransel.ui.format.fxRateToInput("16250.500000"))
    @Test fun wholeStoredRateDropsTheZeros() = assertEquals("16250", app.ransel.ui.format.fxRateToInput("16250.000000"))
    @Test fun commaIsTheDecimal() = assertEquals("0.62", app.ransel.ui.format.fxRateFromInput("0,62"))
    @Test fun wholeRateStaysWhole() = assertEquals("16250", app.ransel.ui.format.fxRateFromInput("16250"))
    @Test fun zeroOrEmptyIsNoRate() {
        assertEquals(null, app.ransel.ui.format.fxRateFromInput("0"))
        assertEquals(null, app.ransel.ui.format.fxRateFromInput(""))
    }

    /** "16.250" typed the Indonesian way: the field turns the first separator into the decimal, so it reads 16,25 on screen. */
    @Test fun aTypedDotBecomesTheVisibleDecimal() {
        val field = app.ransel.ui.format.sanitizeAmountInput("16.250", app.ransel.ui.format.FX_RATE_INPUT_DECIMALS)
        assertEquals("16,250", field)
        assertEquals("16.250", app.ransel.ui.format.fxRateFromInput(field))
    }

    @Test fun roundTripKeepsTheRate() {
        val stored = "16333.3333"
        assertEquals(stored, app.ransel.ui.format.fxRateFromInput(app.ransel.ui.format.fxRateToInput(stored)))
    }
}
