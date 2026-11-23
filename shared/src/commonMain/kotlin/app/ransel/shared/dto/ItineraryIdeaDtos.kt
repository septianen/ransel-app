package app.ransel.shared.dto

import kotlinx.serialization.Serializable

/*
 * The idea behind an itinerary item made by "Bikin trip dari sini" (plan idea-activity-detail, owner 23 Nov 2026): the
 * Explore Inspirasi card the trip came from, kept on the server per item so Activity details shows it on every device and
 * for every member without an AI call. Server-only like travel modes: not part of ItineraryItemDto, /sync or Room.
 */

/**
 * GET/PUT `/trips/{tripId}/itinerary/{itemId}/idea`. [card] is what the detail shows (why = About; requirements, access,
 * level, visaNote, safetyLine = Good to know; costRange, bestSeason, photo). [lat]/[lng]/[placeId] are the region's
 * point from Google Places (null when it was not found): the app puts the point on the item so the day map shows it.
 */
@Serializable
data class ItineraryIdeaDto(
    /** `photoUrl` is freshly signed on every read, like the other place photos. */
    val card: InspirationCardDto,
    val lat: Double? = null,
    val lng: Double? = null,
    /** The region's Google place id (Open in Maps); never used to ask for AI place text. */
    val placeId: String? = null,
)
