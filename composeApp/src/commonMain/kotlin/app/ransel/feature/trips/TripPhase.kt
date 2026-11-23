package app.ransel.feature.trips

import app.ransel.shared.dto.TripDto
import app.ransel.shared.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

enum class TripPhase { ONGOING, UPCOMING, FINISHED }

/** A trip without dates ("Tanggal belum pasti") is still being planned, so it counts as upcoming. */
fun TripDto.phase(today: LocalDate): TripPhase {
    val start = startDate ?: return TripPhase.UPCOMING
    val end = endDate ?: return TripPhase.UPCOMING
    return when {
        today < start -> TripPhase.UPCOMING
        today > end -> TripPhase.FINISHED
        else -> TripPhase.ONGOING
    }
}

/** Days until the trip starts; 0 on the first day, negative once it has started. Null without dates. */
fun TripDto.daysUntilStart(today: LocalDate): Int? = startDate?.let { today.daysUntil(it) }

/** 1-based day of the trip for an ongoing trip (0 without dates). */
fun TripDto.dayNumber(today: LocalDate): Int = startDate?.let { it.daysUntil(today) + 1 } ?: 0

/** Number of days of the trip; 0 without dates. */
fun TripDto.totalDays(): Int {
    val start = startDate ?: return 0
    val end = endDate ?: return 0
    return start.daysUntil(end) + 1
}

/**
 * Upcoming trips in the order they start; trips without dates come after the dated ones (newest first), since
 * nobody knows when they start yet.
 */
val upcomingOrder: Comparator<TripDto> =
    compareBy<TripDto> { it.startDate == null }.thenBy { it.startDate }.thenByDescending { it.createdAt }

/**
 * The trip Home highlights: the one in progress, else the next one to start (a dated one before one without dates).
 * Null when nothing is ahead: a finished trip is never featured; Home then uses the empty state (recap still may show).
 */
fun pickFeaturedTrip(trips: List<TripDto>, today: LocalDate): TripDto? =
    trips.filter { it.phase(today) == TripPhase.ONGOING }.minByOrNull { it.endDate ?: today }
        ?: trips.filter { it.phase(today) == TripPhase.UPCOMING }.minWithOrNull(upcomingOrder)

/**
 * An existing trip the user can still change: OWNER or EDITOR, and not finished. Undated trips count as upcoming.
 * Finished trips and VIEWER-only memberships are left out (itinerary writes would 403; Explore search would 422).
 */
fun TripDto.canReceiveIdea(today: LocalDate): Boolean =
    myRole.atLeast(Role.EDITOR) && phase(today) != TripPhase.FINISHED

/** True when [trips] has at least one [canReceiveIdea] trip: the idea sheet's "Add to existing trip" button. */
fun canAddIdeaToExistingTrip(trips: List<TripDto>, today: LocalDate): Boolean = trips.any { it.canReceiveIdea(today) }

/** Nearest ongoing first, then dated upcoming, then undated. */
fun tripsThatCanReceiveIdea(trips: List<TripDto>, today: LocalDate): List<TripDto> =
    trips.filter { it.canReceiveIdea(today) }
        .sortedWith(
            compareBy(
                { it.phase(today) != TripPhase.ONGOING },
                { it.startDate == null },
                { if (it.phase(today) == TripPhase.ONGOING) it.endDate else it.startDate },
            ),
        )
