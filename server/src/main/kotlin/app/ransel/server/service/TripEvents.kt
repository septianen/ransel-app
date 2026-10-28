package app.ransel.server.service

import kotlinx.datetime.LocalDate

/**
 * Lets TripService and SyncService tell the AI side that a trip was created or changed without depending on it.
 * Application sets [onTripSaved]; nothing happens until then (tests, tools). [startDate] is null for a trip without
 * dates.
 */
object TripEvents {
    @Volatile
    var onTripSaved: (destination: String, startDate: LocalDate?) -> Unit = { _, _ -> }
}
