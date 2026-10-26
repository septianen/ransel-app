package app.ransel.shared.dto

import app.ransel.shared.model.DevicePlatform
import app.ransel.shared.model.NotificationType
import app.ransel.shared.serialization.Timestamp
import kotlinx.serialization.Serializable

/*
 * Notifications: an in-app inbox (server data, signed-in users only) plus push to registered devices.
 * Push is a nudge; the inbox is the record. Guests have neither.
 *
 * `params` per type (all values are strings):
 *   MEMBER_JOINED       tripTitle, actorName (who joined)
 *   MEMBER_REMOVED      tripTitle
 *   ROLE_CHANGED        tripTitle, role (Role name)
 *   EXPENSE_ADDED       tripTitle, actorName, amountMinor, currency, shareMinor (your share, in `currency`; absent if none)
 *   PACKING_LIST        tripTitle, actorName, stage (SENT = a list arrived, UPDATED = it changed, REMIND = "Ingatkan"), requiredLeft (REMIND)
 *   SETTLEMENT_MARKED   tripTitle, actorName, amountMinor, currency (in the trip's base currency), settlementId,
 *                       stage (CLAIMED = actorName says they paid you, waiting for your confirmation; CONFIRMED; missing = CONFIRMED)
 *   PACKING_REMINDER    tripTitle, date (trip start), remaining (items not packed yet)
 *   ACTIVITY_REMINDER   tripTitle, title (activity), time (HH:mm), date
 *   VOTE_REQUESTED      tripTitle, roundId, stage (VoteStage.OPEN | CLOSING; missing = OPEN), actorName (who searched),
 *                       placeCount, closesAt (ISO timestamp), remaining (CLOSING only: cards you have not voted on),
 *                       recipientRole ("OWNER" when the recipient is the one who decides; absent for everyone else)
 *   VOTE_RESULT         tripTitle, roundId, stage (VoteStage.READY | DECIDED; missing = DECIDED);
 *                       READY (to the OWNER): voted, members (members who finished / current members);
 *                       DECIDED (to the others): actorName (the owner), chosenCount
 *   MEMBER_CLAIMED      tripTitle, actorName (who claimed), guestName (the member added by name), guestMemberId (to undo the claim)  → the OWNER
 *   COMMENT_MENTION     tripTitle, actorName, targetKind (CommentTarget), targetId, commentId, snippet (first 80 characters)
 *   POLL_REQUESTED      tripTitle, pollId, pollKind, question, stage (OPEN | CLOSING | RESET | REOPENED; missing = OPEN), actorName (OPEN: the creator),
 *                       closesAt (ISO timestamp), optionLabel (RESET: the option whose votes were cleared)
 *   POLL_RESULT         tripTitle, pollId, pollKind, question, stage (TIED | DECIDED; missing = DECIDED);
 *                       TIED (to the creator): tiedCount; DECIDED (to the others): actorName, winner (option label; absent when dismissed)
 * All of them follow the "Aktivitas trip" switch. Members without an account have no inbox and no push.
 * Unknown keys must be ignored; a missing key means "do not show that part".
 */

@Serializable
data class NotificationDto(
    val id: String,
    val type: NotificationType,
    val tripId: String? = null,
    val params: Map<String, String> = emptyMap(),
    val createdAt: Timestamp,
    /** Null = unread. */
    val readAt: Timestamp? = null,
)

/** GET /notifications?limit=&before= (newest first; `before` is a notification id to continue after). */
@Serializable
data class NotificationsDto(
    val items: List<NotificationDto>,
    /** Over all notifications, not only this page. */
    val unreadCount: Int,
)

/** POST /devices: register this phone for push, or refresh the token / language of an already known one. */
@Serializable
data class RegisterDeviceRequest(
    val platform: DevicePlatform,
    val pushToken: String,
    /** "id" or "en": the language the push text is written in. */
    val locale: String = "id",
    val appVersion: String? = null,
)

/** POST /notifications/read → 204. Null [ids] = everything of the caller. */
@Serializable
data class MarkReadRequest(val ids: List<String>? = null)

/** Which kinds of notification the user wants. All on by default. */
@Serializable
data class NotificationSettingsDto(
    /** Members joining, being removed, role changes, and voting requests and results. */
    val tripActivity: Boolean = true,
    /** Shared expenses and settlements. */
    val expenses: Boolean = true,
    /** Packing and activity reminders. */
    val reminders: Boolean = true,
)

/** PATCH /notification-settings: only the fields that are sent change. */
@Serializable
data class UpdateNotificationSettingsRequest(
    val tripActivity: Boolean? = null,
    val expenses: Boolean? = null,
    val reminders: Boolean? = null,
)
