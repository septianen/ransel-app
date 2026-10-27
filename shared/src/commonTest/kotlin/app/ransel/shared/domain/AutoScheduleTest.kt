package app.ransel.shared.domain

import app.ransel.shared.model.Category
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoScheduleTest {
    private val d1 = LocalDate(2026, 11, 20)
    private val d2 = LocalDate(2026, 11, 21)
    private val d3 = LocalDate(2026, 11, 22)

    // Bali: two near Ubud, two near Uluwatu
    private val tegalalang = AutoPlace("tegalalang", Category.ATTRACTION, -8.430, 115.280, 120)
    private val ubudPalace = AutoPlace("ubud-palace", Category.ATTRACTION, -8.507, 115.263, 90)
    private val uluwatu = AutoPlace("uluwatu", Category.ATTRACTION, -8.829, 115.088, 120)
    private val padang = AutoPlace("padang", Category.ATTRACTION, -8.811, 115.103, 120)

    private fun minutes(s: AutoScheduled) = s.start.hour * 60 + s.start.minute

    @Test
    fun nothingToDoWithoutPlacesOrDays() {
        assertEquals(AutoScheduleResult(emptyList(), emptyList()), autoSchedule(emptyList(), listOf(AutoDay(d1))))
        assertEquals(listOf("uluwatu"), autoSchedule(listOf(uluwatu), emptyList()).leftOver)
    }

    @Test
    fun placesThatAreCloseShareADay() {
        val result = autoSchedule(listOf(tegalalang, uluwatu, ubudPalace, padang), listOf(AutoDay(d1), AutoDay(d2)))
        assertTrue(result.leftOver.isEmpty())
        val byDay = result.scheduled.groupBy { it.date }.mapValues { (_, v) -> v.map { it.id }.toSet() }
        assertEquals(setOf("tegalalang", "ubud-palace"), byDay.values.first { "tegalalang" in it })
        assertEquals(setOf("uluwatu", "padang"), byDay.values.first { "uluwatu" in it })
    }

    @Test
    fun anEmptyDayStartsAtNineAndNothingOverlaps() {
        val result = autoSchedule(listOf(tegalalang, ubudPalace), listOf(AutoDay(d1)))
        val day = result.scheduled.sortedBy { it.sortOrder }
        assertEquals(9 * 60, minutes(day.first()), "the first place of an empty day starts at 09:00")
        val ends = day.map { it.end.hour * 60 + it.end.minute }
        day.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(minutes(b) >= ends[i], "${b.id} starts after ${a.id} ends")
        }
    }

    @Test
    fun existingActivitiesStayAndNewOnesFollow() {
        val existing = AutoExisting(startMinute = 9 * 60, endMinute = 12 * 60, sortOrder = 5.0, lat = -8.5, lng = 115.26)
        val result = autoSchedule(listOf(ubudPalace), listOf(AutoDay(d1, listOf(existing))))
        val s = result.scheduled.single()
        assertTrue(minutes(s) >= 12 * 60, "after the last activity")
        assertEquals(6.0, s.sortOrder)
    }

    @Test
    fun aPlaceNextToADayGoesThere() {
        val anchor = AutoExisting(startMinute = 9 * 60, endMinute = 11 * 60, sortOrder = 1.0, lat = -8.82, lng = 115.09)
        val result = autoSchedule(listOf(uluwatu), listOf(AutoDay(d1), AutoDay(d2, listOf(anchor))))
        assertEquals(d2, result.scheduled.single().date)
    }

    @Test
    fun aPlaceToEatComesAtLunch() {
        val food = AutoPlace("warung", Category.FOOD, -8.51, 115.26, 60)
        val a = AutoPlace("a", Category.ATTRACTION, -8.50, 115.26, 120)
        val b = AutoPlace("b", Category.ATTRACTION, -8.52, 115.26, 120)
        val c = AutoPlace("c", Category.ATTRACTION, -8.53, 115.26, 120)
        val order = autoSchedule(listOf(food, a, b, c), listOf(AutoDay(d1))).scheduled.sortedBy { it.sortOrder }
        val lunch = order.first { it.id == "warung" }
        assertTrue(minutes(lunch) in (11 * 60 + 30)..(13 * 60 + 30), "lunch at ${lunch.start}")
    }

    @Test
    fun whatDoesNotFitStaysUnscheduled() {
        val long = (1..8).map { AutoPlace("p$it", Category.ATTRACTION, -8.5 - it * 0.001, 115.26, 180) }
        val result = autoSchedule(long, listOf(AutoDay(d1)))
        assertTrue(result.leftOver.isNotEmpty())
        assertEquals(long.size, result.scheduled.size + result.leftOver.size)
        assertTrue(result.scheduled.all { it.end.hour * 60 + it.end.minute <= 20 * 60 + 5 })
    }

    @Test
    fun placesWithoutCoordinatesGoToTheEmptiestDay() {
        val busy = AutoDay(d1, listOf(AutoExisting(9 * 60, 18 * 60, 1.0)))
        val manual = AutoPlace("manual", Category.OTHER, durationMin = 60)
        val result = autoSchedule(listOf(manual), listOf(busy, AutoDay(d2)))
        assertEquals(d2, result.scheduled.single().date)
    }

    @Test
    fun theSameInputGivesTheSameResult() {
        val places = listOf(tegalalang, uluwatu, ubudPalace, padang)
        val days = listOf(AutoDay(d1), AutoDay(d2), AutoDay(d3))
        assertEquals(autoSchedule(places, days), autoSchedule(places, days))
    }

    @Test
    fun everyPlaceIsScheduledOrReportedOnce() {
        val places = listOf(tegalalang, uluwatu, ubudPalace, padang, AutoPlace("m", Category.SHOPPING))
        val result = autoSchedule(places, listOf(AutoDay(d1), AutoDay(d2), AutoDay(d3)))
        assertEquals(places.map { it.id }.sorted(), (result.scheduled.map { it.id } + result.leftOver).sorted())
    }
}
