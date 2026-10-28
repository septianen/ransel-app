package app.ransel.server.ai

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Stand-in trip length and start for the AI side when a trip has no dates yet ("Tanggal belum pasti", PRD "Date poll").
 * The prompt and the caches need a length and a month; a neutral guess is a short trip ([DAYS] days) starting on the
 * first day of next month in WIB, close enough for the season and what is open. Nothing is stored on the trip.
 */
internal object UndatedTrip {
    const val DAYS = 3

    private val wib = TimeZone.of("Asia/Jakarta")

    fun startDate(today: LocalDate = Clock.System.now().toLocalDateTime(wib).date): LocalDate =
        LocalDate(today.year, today.month, 1).plus(1, DateTimeUnit.MONTH)

    fun month(today: LocalDate = Clock.System.now().toLocalDateTime(wib).date): String =
        startDate(today).let { "%04d-%02d".format(it.year, it.monthNumber) }
}
