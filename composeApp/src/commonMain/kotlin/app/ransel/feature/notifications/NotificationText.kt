package app.ransel.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.ransel.shared.dto.NotificationDto
import app.ransel.shared.model.NotificationType
import app.ransel.ui.format.formatMoney
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.*

/** What a notification row says: built from the type and params, so it follows the app language. */
data class NotificationLine(val title: String, val subtitle: String)

@Composable
fun notificationLine(n: NotificationDto): NotificationLine {
    val p = n.params
    val trip = p["tripTitle"].orEmpty()
    val actor = p["actorName"]
    fun money(key: String) = p[key]?.toLongOrNull()?.let { amount -> p["currency"]?.let { formatMoney(amount, it) } }
    return when (n.type) {
        NotificationType.MEMBER_JOINED -> NotificationLine(
            actor?.let { stringResource(Res.string.notification_member_joined, it) } ?: stringResource(Res.string.notification_member_joined_someone),
            trip,
        )
        NotificationType.MEMBER_REMOVED -> NotificationLine(stringResource(Res.string.notification_member_removed), trip)
        NotificationType.ROLE_CHANGED -> {
            val role = when (p["role"]) {
                "EDITOR" -> stringResource(Res.string.notification_role_editor)
                "VIEWER" -> stringResource(Res.string.notification_role_viewer)
                "OWNER" -> stringResource(Res.string.notification_role_owner)
                else -> p["role"].orEmpty()
            }
            NotificationLine(stringResource(Res.string.notification_role_changed, role), trip)
        }
        NotificationType.EXPENSE_ADDED -> {
            val amount = money("amountMinor")
            val share = money("shareMinor")
            NotificationLine(
                if (amount != null) stringResource(Res.string.notification_expense_added, actor ?: "", amount).trim() else stringResource(Res.string.notification_unknown),
                if (share != null) stringResource(Res.string.notification_expense_added_share, share, trip) else trip,
            )
        }
        NotificationType.SETTLEMENT_MARKED -> {
            val amount = money("amountMinor")
            NotificationLine(
                if (amount != null) stringResource(Res.string.notification_settlement_marked, actor ?: "", amount).trim() else stringResource(Res.string.notification_unknown),
                trip,
            )
        }
        NotificationType.PACKING_REMINDER -> NotificationLine(
            stringResource(Res.string.notification_packing_reminder, trip),
            stringResource(Res.string.notification_packing_reminder_body, p["remaining"].orEmpty()),
        )
        NotificationType.ACTIVITY_REMINDER -> NotificationLine(
            stringResource(Res.string.notification_activity_reminder, p["title"].orEmpty()),
            stringResource(Res.string.notification_activity_reminder_body, p["time"].orEmpty().replace(':', '.'), trip),
        )
        NotificationType.VOTE_REQUESTED -> NotificationLine(stringResource(Res.string.notification_vote_requested), trip)
        NotificationType.VOTE_RESULT -> NotificationLine(stringResource(Res.string.notification_vote_result), trip)
    }
}

/** Tile colour and icon per type (accent tiles with a white icon, like the category tiles). */
@Composable
fun notificationTile(type: NotificationType): Pair<Color, String> {
    val accents = IrisTheme.accents
    return when (type) {
        NotificationType.MEMBER_JOINED -> accents.blue to Symbols.personAdd
        NotificationType.MEMBER_REMOVED -> accents.red to Symbols.personRemove
        NotificationType.ROLE_CHANGED -> accents.purple to Symbols.badge
        NotificationType.EXPENSE_ADDED -> accents.teal to Symbols.payments
        NotificationType.SETTLEMENT_MARKED -> accents.green to Symbols.checkCircle
        NotificationType.PACKING_REMINDER -> accents.orange to Symbols.luggage
        NotificationType.ACTIVITY_REMINDER -> accents.orange to Symbols.schedule
        NotificationType.VOTE_REQUESTED, NotificationType.VOTE_RESULT -> accents.magenta to Symbols.favorite
    }
}
