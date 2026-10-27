package app.ransel.data.analytics

import app.ransel.shared.model.Category
import app.ransel.shared.model.Role

/**
 * The events behind the PRD "Goals and metrics". Names and parameter keys are snake_case (Firebase limits: name ≤ 40
 * characters, ≤ 25 parameters, string values ≤ 100 characters). Parameters hold enums, flags and counts only.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    // Firebase's recommended names, so the console's built-in reports pick them up.
    data object SignUp : AnalyticsEvent("sign_up", mapOf("method" to "email"))
    data object Login : AnalyticsEvent("login", mapOf("method" to "email"))

    // Trips are actually planned.
    data object TripCreated : AnalyticsEvent("trip_created")
    data class ActivityAdded(val category: Category, val scheduled: Boolean) :
        AnalyticsEvent("activity_added", mapOf("category" to category.name, "scheduled" to scheduled))
    /** An unscheduled place got a day and time (the schedule sheet). */
    data object ActivityScheduled : AnalyticsEvent("activity_scheduled")

    // Packing is used until departure.
    data class PackingItemChecked(val progressPercent: Int) :
        AnalyticsEvent("packing_item_checked", mapOf("progress_percent" to progressPercent))

    // Expenses are recorded.
    data class ExpenseAdded(val category: Category, val shared: Boolean) :
        AnalyticsEvent("expense_added", mapOf("category" to category.name, "shared" to shared))
    data object BalanceSettled : AnalyticsEvent("balance_settled")

    // Group money (stage 4).
    data object SettlementClaimed : AnalyticsEvent("settlement_claimed")
    data object SettlementConfirmed : AnalyticsEvent("settlement_confirmed")
    data class ReceiptScanned(val readable: Boolean) : AnalyticsEvent("receipt_scanned", mapOf("readable" to readable))

    // Explore AI is useful.
    data class ExploreSearch(val newSearch: Boolean, val results: Int) :
        AnalyticsEvent("explore_search", mapOf("new_search" to newSearch, "results" to results))
    data object PlaceSaved : AnalyticsEvent("place_saved")
    data object PlaceSkipped : AnalyticsEvent("place_skipped")

    // Collaboration.
    data class InviteCreated(val role: Role) : AnalyticsEvent("invite_created", mapOf("role" to role.name))
    data object MemberJoined : AnalyticsEvent("member_joined")
}
