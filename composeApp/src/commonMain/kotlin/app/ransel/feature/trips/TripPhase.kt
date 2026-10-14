package app.ransel.feature.trips

import app.ransel.shared.dto.TripDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

enum class TripPhase { ONGOING, UPCOMING, FINISHED }

fun TripDto.phase(today: LocalDate): TripPhase = when {
    today < startDate -> TripPhase.UPCOMING
    today > endDate -> TripPhase.FINISHED
    else -> TripPhase.ONGOING
}

/** Days until the trip starts; 0 on the first day, negative once it has started. */
fun TripDto.daysUntilStart(today: LocalDate): Int = today.daysUntil(startDate)

/** 1-based day of the trip for an ongoing trip. */
fun TripDto.dayNumber(today: LocalDate): Int = startDate.daysUntil(today) + 1

fun TripDto.totalDays(): Int = startDate.daysUntil(endDate) + 1

/**
 * The trip Home highlights: the one in progress, else the next one to start, else the most recently
 * finished one. Null when there are no trips at all.
 */
fun pickFeaturedTrip(trips: List<TripDto>, today: LocalDate): TripDto? =
    trips.filter { it.phase(today) == TripPhase.ONGOING }.minByOrNull { it.endDate }
        ?: trips.filter { it.phase(today) == TripPhase.UPCOMING }.minByOrNull { it.startDate }
        ?: trips.filter { it.phase(today) == TripPhase.FINISHED }.maxByOrNull { it.endDate }
