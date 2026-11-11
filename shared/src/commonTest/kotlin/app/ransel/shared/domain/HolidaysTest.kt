package app.ransel.shared.domain

import app.ransel.shared.dto.HolidayDto
import app.ransel.shared.model.HolidayType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HolidaysTest {
    private fun count(year: Int, type: HolidayType) =
        BUNDLED_HOLIDAYS.count { it.date.year == year && it.type == type }

    @Test fun bundledListMatchesTheSkbCounts() {
        assertEquals(17, count(2026, HolidayType.NATIONAL))
        assertEquals(8, count(2026, HolidayType.CUTI_BERSAMA))
        assertEquals(18, count(2027, HolidayType.NATIONAL))
        assertEquals(8, count(2027, HolidayType.CUTI_BERSAMA))
    }

    @Test fun bundledListHasNoDuplicateDates() {
        assertEquals(BUNDLED_HOLIDAYS.size, BUNDLED_HOLIDAYS.map { it.date }.toSet().size)
    }

    @Test fun emptyRemoteKeepsTheBundledList() {
        assertEquals(BUNDLED_HOLIDAYS, mergeHolidays(BUNDLED_HOLIDAYS, emptyList()))
    }

    @Test fun remoteReplacesOnlyItsOwnYears() {
        val remote = listOf(HolidayDto(LocalDate(2027, 1, 1), "Tahun Baru", HolidayType.NATIONAL))
        val merged = mergeHolidays(BUNDLED_HOLIDAYS, remote)
        assertEquals(1, merged.count { it.date.year == 2027 })
        assertEquals(BUNDLED_HOLIDAYS.count { it.date.year == 2026 }, merged.count { it.date.year == 2026 })
    }

    @Test fun nationalWinsOverCutiBersamaOnTheSameDate() {
        val d = LocalDate(2026, 5, 1)
        val map = holidaysByDate(listOf(
            HolidayDto(d, "a", HolidayType.CUTI_BERSAMA),
            HolidayDto(d, "b", HolidayType.NATIONAL),
        ))
        assertEquals("b", map[d]?.name)
        assertNull(map[LocalDate(2026, 5, 2)])
    }
}
