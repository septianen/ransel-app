package app.ransel.data.travel

import app.ransel.data.explore.ExploreForm
import app.ransel.shared.domain.TravelRow
import app.ransel.shared.dto.UpdateTripRequest
import app.ransel.shared.model.DepartureMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * An answer of the trip that was just saved from "Edit trip, Perjalanan" (spec 1638556). The change is already stored; this is
 * what Edit trip shows after it: the snackbar "Cara berangkat diubah." with "Urungkan", and, when the cities made an answer
 * impossible, the dialog "Cara berangkat direset".
 *
 * @property undo the request that puts the trip's answers back (null for the Seleramu, which is not on the trip).
 * @property undoTaste the Seleramu as it was, to put back; [tasteChanged] says whether this change was the Seleramu.
 * @property resetDeparture the "Cara berangkat" that stopped being possible, for the dialog.
 * @property byDestination the reset came from a new destination, not from a new starting city (the dialog says which).
 */
data class AnswerChange(
    val tripId: String,
    val row: TravelRow,
    val undo: UpdateTripRequest? = null,
    val tasteChanged: Boolean = false,
    val undoTaste: ExploreForm? = null,
    val resetDeparture: DepartureMode? = null,
    val byDestination: Boolean = false,
)

/**
 * Hands [AnswerChange]s from the screens that save an answer to Edit trip, which may not be on screen yet when the change is made
 * (the answer screen closes first). Nothing is lost: a change waits until Edit trip collects it.
 */
class TravelAnswerChanges {
    private val channel = Channel<AnswerChange>(Channel.BUFFERED)
    val changes: Flow<AnswerChange> = channel.receiveAsFlow()
    fun post(change: AnswerChange) {
        channel.trySend(change)
    }
}
