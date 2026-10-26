package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PackingItems
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.shared.model.NotificationType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Packing and activity reminders. Run [tick] about once a minute. A tick is safe to repeat or to miss: the unique
 * (user, dedupe key) means a reminder is written once, and the windows are "from now until X", not "exactly at X".
 * Packing reminders follow WIB (the members are at home the evening before); activity times are local to the trip's
 * zone (V20), WIB when the trip has none.
 */
class ReminderScheduler(private val config: AppConfig, private val notifications: NotificationService) {
    private val zone = ZoneId.of("Asia/Jakarta")

    private companion object {
        const val RECAP_HOUR = 9
    }

    fun tick(now: Instant = Instant.now()) {
        val local = LocalDateTime.ofInstant(now, zone)
        packing(local)
        activities(now)
        recaps(local)
        PollService.tick(now)
    }

    /** The evening before departure, from [AppConfig.notifyPackingHour] on: if things are still not packed. */
    private fun packing(local: LocalDateTime) {
        if (local.hour < config.notifyPackingHour) return
        val departure = local.toLocalDate().plusDays(1)
        transaction {
            Trips.selectAll().where { (Trips.startDate eq departure) and Trips.deletedAt.isNull() }.forEach { trip ->
                val tripId = trip[Trips.id]
                val remaining = PackingItems.selectAll()
                    .where { (PackingItems.tripId eq tripId) and PackingItems.deletedAt.isNull() and (PackingItems.isPacked eq false) }
                    .count().toInt()
                if (remaining == 0) return@forEach
                notifications.notify(members(tripId), NotificationType.PACKING_REMINDER, tripId, "packing:$tripId:$departure") {
                    mapOf("tripTitle" to trip[Trips.title], "date" to departure.toString(), "remaining" to remaining.toString())
                }
            }
        }
    }

    /** Scheduled activities starting within the next [AppConfig.notifyActivityLeadMinutes] minutes. */
    private fun activities(now: Instant) {
        val until = now.plusSeconds(config.notifyActivityLeadMinutes * 60L)
        // zones are at most a day apart from WIB: look at yesterday, today and tomorrow in WIB
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        val days = listOf(today.minusDays(1), today, today.plusDays(1))
        transaction {
            ItineraryItems.join(Trips, org.jetbrains.exposed.v1.core.JoinType.INNER, ItineraryItems.tripId, Trips.id)
                .selectAll()
                .where { (ItineraryItems.dayDate inList days) and ItineraryItems.deletedAt.isNull() and Trips.deletedAt.isNull() }
                .forEach { row ->
                    val day = row[ItineraryItems.dayDate] ?: return@forEach
                    val time = row[ItineraryItems.startTime] ?: return@forEach
                    val tripZone = row[Trips.timeZone]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                    val start = LocalDateTime.of(day, time).atZone(tripZone).toInstant()
                    if (!start.isAfter(now) || start.isAfter(until)) return@forEach
                    val tripId = row[ItineraryItems.tripId]
                    notifications.notify(members(tripId), NotificationType.ACTIVITY_REMINDER, tripId, "activity:${row[ItineraryItems.id]}") {
                        mapOf(
                            "tripTitle" to row[Trips.title], "title" to row[ItineraryItems.title],
                            "time" to "%02d:%02d".format(time.hour, time.minute), "date" to day.toString(),
                        )
                    }
                }
        }
    }

    /** "Rekap siap": from 09.00 WIB the day after a trip ended (within a week, so a late deploy still sends it once). */
    private fun recaps(local: LocalDateTime) {
        if (local.hour < RECAP_HOUR) return
        val today = local.toLocalDate()
        transaction {
            Trips.selectAll()
                .where { (Trips.endDate less today) and (Trips.endDate greaterEq today.minusDays(7)) and Trips.deletedAt.isNull() }
                .forEach { trip ->
                    val tripId = trip[Trips.id]
                    notifications.notify(members(tripId), NotificationType.RECAP_READY, tripId, "recap:$tripId") { mapOf("tripTitle" to trip[Trips.title]) }
                }
        }
    }

    private fun members(tripId: UUID): List<UUID> =
        TripMembers.selectAll().where { (TripMembers.tripId eq tripId) and TripMembers.active() }.mapNotNull { it[TripMembers.userId] }
}
