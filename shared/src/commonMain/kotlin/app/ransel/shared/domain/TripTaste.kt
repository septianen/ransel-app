package app.ransel.shared.domain

import app.ransel.shared.dto.TripTasteDto

/*
 * The trip's Seleramu on the server (`/trips/{id}/taste`) and the sheet's [InterviewTaste]: one shape, the same limits on
 * both sides (the request limits of `RecommendationRequest`).
 */

/** At most this many "Khas" chips, each at most [TRIP_TASTE_EXTRA_MAX_LENGTH] characters. */
const val TRIP_TASTE_EXTRA_MAX = 5
const val TRIP_TASTE_EXTRA_MAX_LENGTH = 40

/**
 * Field problems for 422 VALIDATION_FAILED: `extraInterests` = TOO_MANY / TOO_LONG, `notes` = TOO_LONG. An empty taste is
 * valid. Blank "Khas" chips are dropped by [normalized], not rejected.
 */
fun validateTripTaste(taste: TripTasteDto): Map<String, String> = buildMap {
    val extras = taste.normalized().extraInterests
    if (extras.size > TRIP_TASTE_EXTRA_MAX) put("extraInterests", "TOO_MANY")
    else if (extras.any { it.length > TRIP_TASTE_EXTRA_MAX_LENGTH }) put("extraInterests", "TOO_LONG")
    if ((taste.notes?.trim()?.length ?: 0) > TASTE_NOTES_MAX) put("notes", "TOO_LONG")
}

/** Trimmed, no blanks or duplicates, blank notes = null: what is stored and what two equal tastes compare as. */
fun TripTasteDto.normalized(): TripTasteDto = copy(
    interests = interests.distinct(),
    extraInterests = extraInterests.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
)

/** The sheet's answers as the server's shape. */
fun InterviewTaste.toTripTaste(): TripTasteDto = TripTasteDto(
    interests = interests.toList(),
    extraInterests = extraInterests.toList(),
    style = style,
    priceLevel = priceLevel,
    notes = notes.takeIf { it.isNotBlank() },
).normalized()

/** The server's taste as the sheet's answers. */
fun TripTasteDto.toInterviewTaste(): InterviewTaste = normalized().let {
    InterviewTaste(
        interests = it.interests.toSet(),
        extraInterests = it.extraInterests.toSet(),
        style = it.style,
        priceLevel = it.priceLevel,
        notes = it.notes.orEmpty(),
    )
}
