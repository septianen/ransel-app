package app.ransel.feature

import app.ransel.data.notification.NotificationTarget
import app.ransel.feature.notifications.notificationTab
import app.ransel.shared.model.NotificationType
import app.ransel.ui.components.TripTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationRoutingTest {
    @Test
    fun eachNotificationOpensTheTabItIsAbout() {
        assertEquals(TripTab.ITINERARY, notificationTab(NotificationType.ACTIVITY_REMINDER))
        assertEquals(TripTab.PACKING, notificationTab(NotificationType.PACKING_REMINDER))
        assertEquals(TripTab.PACKING, notificationTab(NotificationType.PACKING_LIST))
        assertEquals(TripTab.EXPENSES, notificationTab(NotificationType.EXPENSE_ADDED))
        assertEquals(TripTab.EXPENSES, notificationTab(NotificationType.SETTLEMENT_MARKED))
        assertNull(notificationTab(NotificationType.MEMBER_JOINED), "the rest open Ringkasan")
    }

    @Test
    fun aTappedPushKeepsItsTabUntilTheTripOpens() {
        val target = NotificationTarget()
        target.offer("t1", type = "ACTIVITY_REMINDER")
        assertEquals("t1", target.tripId.value)
        assertEquals(TripTab.ITINERARY, target.tab.value)
        target.clear()
        assertNull(target.tab.value)
        target.offer("t1", type = "SOMETHING_NEWER")
        assertNull(target.tab.value, "a type this app does not know opens the trip as before")
    }
}
