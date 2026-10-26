package app.ransel.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.ransel.shared.domain.VoteStage
import app.ransel.shared.domain.hoursLeft
import app.ransel.shared.dto.NotificationDto
import kotlin.time.Clock
import kotlin.time.Instant
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
            when {
                // a guest wrote their name on the guest page
                actor != null && p["via"] == "LINK" -> stringResource(Res.string.notification_member_joined_link, actor)
                actor != null -> stringResource(Res.string.notification_member_joined, actor)
                else -> stringResource(Res.string.notification_member_joined_someone)
            },
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
                when {
                    amount == null -> stringResource(Res.string.notification_unknown)
                    // "Saya sudah bayar": the receiver is asked to confirm
                    p["stage"] == "CLAIMED" -> stringResource(Res.string.notification_settlement_claimed, actor ?: "", amount).trim()
                    else -> stringResource(Res.string.notification_settlement_marked, actor ?: "", amount).trim()
                },
                trip,
            )
        }
        NotificationType.MEMBER_CLAIMED -> NotificationLine(
            stringResource(Res.string.notification_member_claimed, actor.orEmpty()).trim(),
            p["guestName"]?.let { stringResource(Res.string.notification_member_claimed_body, it, trip) } ?: trip,
        )
        NotificationType.COMMENT_MENTION -> NotificationLine(
            stringResource(Res.string.notification_comment_mention, actor.orEmpty()).trim(),
            p["snippet"]?.takeIf { it.isNotBlank() } ?: trip,
        )
        NotificationType.POLL_REQUESTED -> {
            val question = p["question"].orEmpty()
            when (p["stage"]) {
                "CLOSING" -> NotificationLine(stringResource(Res.string.notification_poll_closing), question)
                "RESET" -> NotificationLine(stringResource(Res.string.notification_poll_reset), listOfNotNull(p["optionLabel"], question).joinToString(" · "))
                "REOPENED" -> NotificationLine(stringResource(Res.string.notification_poll_reopened), question)
                else -> NotificationLine(
                    actor?.let { stringResource(Res.string.notification_poll_open, it) } ?: stringResource(Res.string.notification_poll_open_someone),
                    question,
                )
            }
        }
        NotificationType.POLL_RESULT -> {
            val question = p["question"].orEmpty()
            if (p["stage"] == "TIED") NotificationLine(stringResource(Res.string.notification_poll_tied), question)
            else NotificationLine(
                stringResource(Res.string.notification_poll_decided),
                when {
                    actor != null && p["winner"] != null -> stringResource(Res.string.notification_poll_decided_body, actor, p["winner"]!!)
                    actor != null -> stringResource(Res.string.notification_poll_dismissed_body, actor)
                    else -> question
                },
            )
        }
        NotificationType.PACKING_LIST -> when (p["stage"]) {
            "UPDATED" -> NotificationLine(stringResource(Res.string.notification_packing_list_updated), trip)
            "REMIND" -> NotificationLine(
                stringResource(Res.string.notification_packing_list_remind),
                p["requiredLeft"]?.takeIf { it != "0" }?.let { stringResource(Res.string.notification_packing_list_remind_body, it, trip) } ?: trip,
            )
            else -> NotificationLine(
                actor?.let { stringResource(Res.string.notification_packing_list_sent, it) } ?: stringResource(Res.string.notification_packing_list_sent_someone),
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
        NotificationType.VOTE_REQUESTED -> if (p["stage"] == VoteStage.CLOSING) {
            val left = p["closesAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { hoursLeft(Clock.System.now(), it) }
            NotificationLine(
                if (left != null && left > 0) stringResource(Res.string.notification_vote_closing, left) else stringResource(Res.string.notification_vote_closing_soon),
                p["remaining"]?.toIntOrNull()?.let { stringResource(Res.string.notification_vote_closing_body, it, trip) } ?: trip,
            )
        } else if (p["recipientRole"] == "OWNER" && actor != null && p["placeCount"]?.toIntOrNull() != null) {
            // The owner decides: a proposal for them, not a request for their picks.
            NotificationLine(
                stringResource(Res.string.notification_vote_owner_open, actor, p["placeCount"]!!.toInt()),
                stringResource(Res.string.notification_vote_owner_open_body, trip),
            )
        } else {
            val count = p["placeCount"]?.toIntOrNull()
            val left = p["closesAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { hoursLeft(Clock.System.now(), it) }
            NotificationLine(
                actor?.let { stringResource(Res.string.notification_vote_open, it) } ?: stringResource(Res.string.notification_vote_open_someone),
                if (count != null && left != null && left > 0) stringResource(Res.string.notification_vote_open_body, count, left, trip) else trip,
            )
        }
        NotificationType.VOTE_RESULT -> if (p["stage"] == VoteStage.READY) {
            val voted = p["voted"]?.toIntOrNull()
            val members = p["members"]?.toIntOrNull()
            NotificationLine(
                stringResource(Res.string.notification_vote_ready),
                if (voted != null && members != null) stringResource(Res.string.notification_vote_ready_body, voted, members, trip) else trip,
            )
        } else {
            val chosen = p["chosenCount"]?.toIntOrNull()
            when {
                chosen == 0 && actor != null -> NotificationLine(stringResource(Res.string.notification_vote_decided_none, actor), trip)
                chosen != null && actor != null -> NotificationLine(
                    stringResource(Res.string.notification_vote_decided, actor, chosen),
                    stringResource(Res.string.notification_vote_decided_body, trip),
                )
                else -> NotificationLine(stringResource(Res.string.notification_vote_result), trip)
            }
        }
    }
}

/** Tile colour and icon per type (accent tiles with a white icon, like the category tiles). */
@Composable
fun notificationTile(type: NotificationType, params: Map<String, String> = emptyMap()): Pair<Color, String> {
    val accents = IrisTheme.accents
    // The design uses how_to_vote and timer for voting; they are not in the app's icon subset.
    val stage = params["stage"]
    return when (type) {
        NotificationType.MEMBER_JOINED -> accents.blue to Symbols.personAdd
        NotificationType.MEMBER_REMOVED -> accents.red to Symbols.personRemove
        NotificationType.ROLE_CHANGED -> accents.purple to Symbols.badge
        NotificationType.EXPENSE_ADDED -> accents.teal to Symbols.payments
        NotificationType.SETTLEMENT_MARKED -> accents.green to Symbols.checkCircle
        NotificationType.MEMBER_CLAIMED -> accents.blue to Symbols.person
        NotificationType.COMMENT_MENTION -> accents.teal to Symbols.alternateEmail
        NotificationType.POLL_REQUESTED -> accents.magenta to Symbols.checklist
        NotificationType.POLL_RESULT -> accents.magenta to Symbols.checkCircle
        NotificationType.PACKING_REMINDER -> accents.orange to Symbols.luggage
        NotificationType.PACKING_LIST -> accents.orange to Symbols.checklist
        NotificationType.ACTIVITY_REMINDER -> accents.orange to Symbols.schedule
        NotificationType.VOTE_REQUESTED -> accents.magenta to (if (stage == VoteStage.CLOSING) Symbols.schedule else Symbols.favorite)
        NotificationType.VOTE_RESULT -> accents.magenta to (if (stage == VoteStage.READY) Symbols.doneAll else Symbols.checkCircle)
    }
}
