package app.ransel.data.explore

import app.ransel.data.remote.ApiResult
import app.ransel.data.remote.RanselApi
import app.ransel.shared.domain.InterviewTaste
import app.ransel.shared.domain.toInterviewTaste
import app.ransel.shared.domain.toTripTaste
import app.ransel.shared.dto.TripDto
import app.ransel.shared.dto.TripTasteDto
import app.ransel.shared.model.PriceLevel
import app.ransel.shared.model.TravelStyle

/**
 * A trip's Seleramu (plan explore-trip-one-tap, owner 22 Nov 2026): kept on the server with the trip (`/trips/{id}/taste`)
 * so every member and device starts "Cari tempat" from it. [ExploreSessionStore] keeps a copy on this device: the answer
 * while offline, and the Seleramu of trips made before the server kept it, which is uploaded once by an OWNER or EDITOR.
 */
class TripTasteRepository(
    private val api: RanselApi,
    private val store: ExploreSessionStore,
) {
    /**
     * The server's Seleramu (also kept on this device), else this device's copy. A device-only Seleramu is uploaded when
     * [canEdit] (a viewer cannot save). Null = nothing anywhere: the sheet opens empty.
     */
    suspend fun load(trip: TripDto, canEdit: Boolean): InterviewTaste? {
        val local = store.taste(trip.id)?.toInterviewTaste()
        val answer = api.tripTaste(trip.id)
        if (answer !is ApiResult.Success) return local
        val remote = answer.value.taste?.toInterviewTaste()?.takeUnless { it.isEmpty }
        return when {
            remote != null -> remote.also { store.saveTaste(it.toForm(trip)) }
            answer.value.taste != null -> null // cleared on the server (an undo of the first Seleramu)
            local != null -> local.also { if (canEdit && !it.isEmpty) api.saveTripTaste(trip.id, it.toTripTaste()) }
            else -> null
        }
    }

    /**
     * Saves [taste] for the trip, on this device and on the server; null clears it. The server save is best effort: false
     * when it failed (offline, a viewer); the device copy is kept either way.
     */
    suspend fun save(trip: TripDto, taste: InterviewTaste?, budgetPriceLevel: PriceLevel? = null): Boolean {
        if (taste == null || taste.isEmpty) store.clearTaste(trip.id) else store.saveTaste(taste.toForm(trip, budgetPriceLevel))
        return api.saveTripTaste(trip.id, taste?.toTripTaste() ?: TripTasteDto()) is ApiResult.Success
    }
}

/** This device's form as the sheet's answers. */
fun ExploreForm.toInterviewTaste() = InterviewTaste(
    interests = interests.toSet(), extraInterests = extraInterests.toSet(), style = style, priceLevel = priceLevel, notes = notes,
)

/** The answers as the search form of [trip]: the range the user picked, else the budget's, else Sedang. */
fun InterviewTaste.toForm(trip: TripDto, budgetPriceLevel: PriceLevel? = null) = ExploreForm(
    tripId = trip.id, tripTitle = trip.title, destination = trip.destination, startDate = trip.startDate, endDate = trip.endDate,
    priceLevel = priceLevel ?: budgetPriceLevel ?: PriceLevel.MEDIUM, interests = interests.toList(),
    extraInterests = extraInterests.toList().take(5), style = style ?: TravelStyle.SANTAI, notes = notes,
)
