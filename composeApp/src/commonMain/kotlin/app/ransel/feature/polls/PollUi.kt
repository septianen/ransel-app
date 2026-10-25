package app.ransel.feature.polls

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.*

/** Icons come from the app's icon subset, which has no `how_to_vote` or `poll`. */
val PollKind.symbol: String
    get() = when (this) {
        PollKind.DATE -> Symbols.calendarMonth
        PollKind.PLACE -> Symbols.locationOn
        PollKind.STAY -> Symbols.hotel
        PollKind.FOOD -> Symbols.restaurant
        PollKind.BUDGET -> Symbols.wallet
        PollKind.DESTINATION -> Symbols.explore
        PollKind.TRANSPORT -> Symbols.directionsCar
        PollKind.CUSTOM -> Symbols.editNote
    }

@Composable
fun PollKind.tileColor(): Color = with(IrisTheme.accents) {
    when (this@tileColor) {
        PollKind.DATE -> blue
        PollKind.PLACE -> teal
        PollKind.STAY -> purple
        PollKind.FOOD -> orange
        PollKind.BUDGET -> green
        PollKind.DESTINATION -> magenta
        PollKind.TRANSPORT -> brown
        PollKind.CUSTOM -> blue
    }
}

private val PollKind.labelRes: StringResource
    get() = when (this) {
        PollKind.DATE -> Res.string.poll_kind_date
        PollKind.PLACE -> Res.string.poll_kind_place
        PollKind.STAY -> Res.string.poll_kind_stay
        PollKind.FOOD -> Res.string.poll_kind_food
        PollKind.BUDGET -> Res.string.poll_kind_budget
        PollKind.DESTINATION -> Res.string.poll_kind_destination
        PollKind.TRANSPORT -> Res.string.poll_kind_transport
        PollKind.CUSTOM -> Res.string.poll_kind_custom
    }

private val PollKind.hintRes: StringResource
    get() = when (this) {
        PollKind.DATE -> Res.string.poll_kind_date_hint
        PollKind.PLACE -> Res.string.poll_kind_place_hint
        PollKind.STAY -> Res.string.poll_kind_stay_hint
        PollKind.FOOD -> Res.string.poll_kind_food_hint
        PollKind.BUDGET -> Res.string.poll_kind_budget_hint
        PollKind.DESTINATION -> Res.string.poll_kind_destination_hint
        PollKind.TRANSPORT -> Res.string.poll_kind_transport_hint
        PollKind.CUSTOM -> Res.string.poll_kind_custom_hint
    }

@Composable fun PollKind.label(): String = stringResource(labelRes)
@Composable fun PollKind.hint(): String = stringResource(hintRes)

/** The question a new poll starts with; a free question starts empty. */
@Composable
fun PollKind.defaultQuestion(): String = when (this) {
    PollKind.DATE -> stringResource(Res.string.poll_question_date)
    PollKind.PLACE -> stringResource(Res.string.poll_question_place)
    PollKind.STAY -> stringResource(Res.string.poll_question_stay)
    PollKind.FOOD -> stringResource(Res.string.poll_question_food)
    PollKind.BUDGET -> stringResource(Res.string.poll_question_budget)
    PollKind.DESTINATION -> stringResource(Res.string.poll_question_destination)
    PollKind.TRANSPORT -> stringResource(Res.string.poll_question_transport)
    PollKind.CUSTOM -> ""
}

/** What the user has to do about a poll, shown under "Perlu tindakanmu". */
enum class PollNeed { VOTE, DECIDE }

/** The creator and the trip owner run a poll: close it, confirm the winner, reopen, delete. */
fun PollDto.isManagedBy(myMemberId: String?, isOwner: Boolean): Boolean = isOwner || (myMemberId != null && createdByMemberId == myMemberId)

fun PollDto.needFor(myMemberId: String?, isOwner: Boolean): PollNeed? = when {
    status == PollStatus.OPEN && myVotes.isEmpty() -> PollNeed.VOTE
    (status == PollStatus.CLOSED || status == PollStatus.TIED) && isManagedBy(myMemberId, isOwner) -> PollNeed.DECIDE
    else -> null
}

/** The option(s) with the most votes; empty while nobody has voted. */
fun PollDto.leaders(): List<PollOptionDto> {
    val top = options.maxOfOrNull { it.voteCount } ?: 0
    return if (top <= 0) emptyList() else options.filter { it.voteCount == top }
}

/** Active polls first (the ones that still take votes), then the rest, newest first inside each group. */
fun List<PollDto>.forDisplay(): List<PollDto> = sortedWith(
    compareByDescending<PollDto> { it.status == PollStatus.OPEN }.thenByDescending { it.createdAt },
)
