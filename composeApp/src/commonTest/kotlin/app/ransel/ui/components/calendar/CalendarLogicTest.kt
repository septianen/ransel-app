package app.ransel.ui.components.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarLogicTest {
    @Test fun novemberTwentyTwentySixStartsOnSundayAndFillsWholeWeeks() {
        val cells = monthCells(LocalDate(2026, 11, 14))
        // 1 Nov 2026 is a Sunday: six empty cells before it in a Monday-first week
        assertEquals(6, cells.indexOfFirst { it != null })
        assertEquals(LocalDate(2026, 11, 1), cells[6])
        assertEquals(LocalDate(2026, 11, 30), cells.last { it != null })
        assertEquals(0, cells.size % 7)
    }

    @Test fun aMondayFirstMonthHasNoLeadingGap() {
        // 1 Feb 2027 is a Monday, 28 days: exactly four full weeks
        val cells = monthCells(LocalDate(2027, 2, 10))
        assertEquals(28, cells.size)
        assertEquals(LocalDate(2027, 2, 1), cells.first())
        assertEquals(LocalDate(2027, 2, 28), cells.last())
    }

    @Test fun leapFebruaryHasTwentyNineDays() {
        assertEquals(29, monthCells(LocalDate(2028, 2, 1)).count { it != null })
    }

    @Test fun monthsBetweenCountsAcrossYears() {
        assertEquals(0, monthsBetween(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)))
        assertEquals(2, monthsBetween(LocalDate(2026, 11, 20), LocalDate(2027, 1, 1)))
        assertEquals(-1, monthsBetween(LocalDate(2026, 11, 1), LocalDate(2026, 10, 31)))
    }

    @Test fun boundsAreInclusive() {
        val e = LocalDate(2026, 11, 10)
        val l = LocalDate(2026, 11, 20)
        assertTrue(isSelectable(e, e, l))
        assertTrue(isSelectable(l, e, l))
        assertFalse(isSelectable(LocalDate(2026, 11, 9), e, l))
        assertFalse(isSelectable(LocalDate(2026, 11, 21), e, l))
        assertTrue(isSelectable(LocalDate(2000, 1, 1), null, null))
    }

    @Test fun rangeTapsSetStartThenEndThenRestart() {
        val a = LocalDate(2026, 11, 20)
        val b = LocalDate(2026, 11, 23)
        val first = tapRange(DateSelection(), a)
        assertEquals(DateSelection(a, null), first)
        val second = tapRange(first, b)
        assertEquals(DateSelection(a, b), second)
        assertEquals(DateSelection(LocalDate(2026, 11, 25), null), tapRange(second, LocalDate(2026, 11, 25)))
    }

    @Test fun anEarlierTapMovesTheStart() {
        val a = LocalDate(2026, 11, 20)
        val early = LocalDate(2026, 11, 18)
        assertEquals(DateSelection(early, null), tapRange(DateSelection(a, null), early))
    }

    @Test fun sameDayTapMakesAOneDayRange() {
        val a = LocalDate(2026, 11, 20)
        assertEquals(DateSelection(a, a), tapRange(DateSelection(a, null), a))
    }

    @Test fun containsCoversTheWholeRangeAndASingleStart() {
        val sel = DateSelection(LocalDate(2026, 11, 20), LocalDate(2026, 11, 22))
        assertTrue(sel.contains(LocalDate(2026, 11, 21)))
        assertFalse(sel.contains(LocalDate(2026, 11, 23)))
        assertTrue(DateSelection(LocalDate(2026, 11, 20)).contains(LocalDate(2026, 11, 20)))
        assertFalse(DateSelection().contains(LocalDate(2026, 11, 20)))
        assertNull(DateSelection().start)
    }
}
