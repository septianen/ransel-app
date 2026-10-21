package app.ransel.server

import app.ransel.server.push.LoggingPushSender
import app.ransel.server.service.NotificationService
import app.ransel.server.service.ReminderScheduler
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderSchedulerTest {
    private val wib = ZoneId.of("Asia/Jakarta")
    private fun at(date: String, time: String) = LocalDateTime.parse("${date}T$time").atZone(wib).toInstant()

    private fun scheduler() = TestDb.config().let { ReminderScheduler(it, NotificationService(it, LoggingPushSender())) }

    private suspend fun Api.inboxTypes() = get("/notifications").data().jsonObject.getValue("items").jsonArray
        .map { it.jsonObject.getValue("type").jsonPrimitive.content }

    private suspend fun Api.tripWithItem(start: String, itemDate: String, itemTime: String?): String {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId, start = start, end = "2026-11-25"))
        val res = post(
            "/trips/$tripId/itinerary",
            itemJson(day = itemDate, start = itemTime, end = itemTime?.let { "23:00" }),
        )
        assertEquals(HttpStatusCode.Created, res.status, res.status.toString())
        return tripId
    }

    @Test
    fun anActivityReminderComesOnceInTheThirtyMinutesBeforeItStarts() = apiTest {
        tripWithItem("2026-11-20", "2026-11-20", "10:00")
        val s = scheduler()
        s.tick(at("2026-11-20", "09:20")) // 40 minutes before: too early
        assertEquals(emptyList(), inboxTypes())

        s.tick(at("2026-11-20", "09:35")) // 25 minutes before
        s.tick(at("2026-11-20", "09:36")) // the next tick does not repeat it
        assertEquals(listOf("ACTIVITY_REMINDER"), inboxTypes())

        val params = get("/notifications").data().jsonObject.getValue("items").jsonArray.single().jsonObject.getValue("params").jsonObject
        assertEquals("10:00", params.getValue("time").jsonPrimitive.content)
        assertEquals("Pantai Kuta", params.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun nothingForAnActivityThatAlreadyStartedOrHasNoTime() = apiTest {
        tripWithItem("2026-11-20", "2026-11-20", "10:00")
        scheduler().tick(at("2026-11-20", "10:05"))
        assertEquals(emptyList(), inboxTypes())

        tripWithItem("2026-11-21", "2026-11-21", null)
        scheduler().tick(at("2026-11-21", "10:00"))
        assertEquals(emptyList(), inboxTypes())
    }

    @Test
    fun anActivityJustAfterMidnightIsRemindedBeforeMidnight() = apiTest {
        tripWithItem("2026-11-20", "2026-11-21", "00:10")
        scheduler().tick(at("2026-11-20", "23:50"))
        assertEquals(listOf("ACTIVITY_REMINDER"), inboxTypes())
    }

    @Test
    fun theEveningBeforeDepartureUnpackedItemsGetOnePackingReminder() = apiTest {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId, start = "2026-11-20", end = "2026-11-23"))
        repeat(2) {
            post("/trips/$tripId/packing", """{"id":"${UUID.randomUUID()}","category":"CLOTHING","name":"Kaos $it","quantity":1}""")
        }
        val s = scheduler()
        s.tick(at("2026-11-19", "17:00")) // before 18:00
        assertEquals(emptyList(), inboxTypes())
        s.tick(at("2026-11-19", "18:00"))
        s.tick(at("2026-11-19", "21:00"))
        assertEquals(listOf("PACKING_REMINDER"), inboxTypes())
        assertEquals("2", get("/notifications").data().jsonObject.getValue("items").jsonArray.single().jsonObject.getValue("params").jsonObject.getValue("remaining").jsonPrimitive.content)
    }

    @Test
    fun noPackingReminderWhenEverythingIsPackedOrThereIsNothingToPack() = apiTest {
        val tripId = UUID.randomUUID().toString()
        post("/trips", tripJson(tripId, start = "2026-11-20", end = "2026-11-23"))
        scheduler().tick(at("2026-11-19", "19:00"))
        assertEquals(emptyList(), inboxTypes())
    }

    @Test
    fun theRemindersSettingTurnsThemOff() = apiTest {
        tripWithItem("2026-11-20", "2026-11-20", "10:00")
        patch("/notification-settings", """{"reminders":false}""")
        scheduler().tick(at("2026-11-20", "09:40"))
        assertEquals(emptyList(), inboxTypes())
    }
}
