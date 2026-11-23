package app.ransel.data.inspiration

import app.ransel.shared.dto.InspirationCardDto

/**
 * The idea card waiting for "Add to existing trip": the detail sheet sets it, the trip picker reads it. Not in the
 * address (the card is too large, and a reload starts over on Explore).
 */
class AddIdeaToTripRequests {
    var card: InspirationCardDto? = null
        private set

    fun set(card: InspirationCardDto) {
        this.card = card
    }

    fun clear() {
        card = null
    }
}
