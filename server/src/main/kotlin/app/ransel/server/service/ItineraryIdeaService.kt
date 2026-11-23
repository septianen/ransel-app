package app.ransel.server.service

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.db.ItineraryIdeaDetails
import app.ransel.server.db.ItineraryItems
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.domain.ideaActivityTitle
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationCostRangeDto
import app.ransel.shared.dto.ItineraryIdeaDto
import app.ransel.shared.model.Role
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The idea card behind an item made by "Bikin trip dari sini" (plan idea-activity-detail, owner 23 Nov 2026). Saving it
 * finds the card photo and the region's point with Google Places (Text Search IDs + one Place Details, no AI); reading
 * it never calls Google or the AI. The text is the user's own trip content, kept to the card limits.
 */
class ItineraryIdeaService(
    private val ai: AiServices,
    private val photoSigner: PhotoSigner,
) {
    private val log = LoggerFactory.getLogger(ItineraryIdeaService::class.java)

    fun get(userId: UUID, tripId: UUID, itemId: UUID): ItineraryIdeaDto {
        requireRole(tripId, userId, Role.VIEWER)
        return load(tripId, itemId) ?: throw notFound("Idea")
    }

    suspend fun put(userId: UUID, tripId: UUID, itemId: UUID, body: InspirationCardDto): ItineraryIdeaDto {
        requireRole(tripId, userId, Role.EDITOR)
        val itemInTrip = transaction {
            ItineraryItems.selectAll()
                .where { (ItineraryItems.id eq itemId) and (ItineraryItems.tripId eq tripId) and ItineraryItems.deletedAt.isNull() }
                .any()
        }
        if (!itemInTrip) throw notFound("Itinerary item")
        val card = clean(body)
        requireValid(buildMap { if (card.activity.isEmpty()) put("activity", "REQUIRED"); if (card.region.isEmpty()) put("region", "REQUIRED") })
        // Google, not AI; a failure only leaves the photo or the point out
        val photo = runCatching { ai.places.findPlaceId(card.activity, card.region) }.onFailure { log.warn("Idea photo lookup failed: {}", it.javaClass.simpleName) }.getOrNull()
        val region = runCatching {
            ai.places.findPlaceId(card.region, card.region)?.let { ai.places.details(it, withPhotoCredit = false) }
        }.onFailure { log.warn("Idea region lookup failed: {}", it.javaClass.simpleName) }.getOrNull()
        transaction {
            ItineraryIdeaDetails.upsert {
                it[ItineraryIdeaDetails.itemId] = itemId
                it[ItineraryIdeaDetails.tripId] = tripId
                it[ItineraryIdeaDetails.card] = card
                it[photoPlaceId] = photo
                it[regionPlaceId] = region?.placeId
                it[lat] = region?.lat
                it[lng] = region?.lng
                it[updatedBy] = userId
                it[updatedAt] = Instant.now()
            }
        }
        return load(tripId, itemId)!!
    }

    private fun load(tripId: UUID, itemId: UUID): ItineraryIdeaDto? = transaction {
        ItineraryIdeaDetails.selectAll()
            .where { (ItineraryIdeaDetails.itemId eq itemId) and (ItineraryIdeaDetails.tripId eq tripId) }
            .singleOrNull()
            ?.let { row ->
                val photo = row[ItineraryIdeaDetails.photoPlaceId]
                ItineraryIdeaDto(
                    card = row[ItineraryIdeaDetails.card].copy(
                        photoUrl = photo?.let(photoSigner::signedPath),
                        photoAttribution = if (photo != null) row[ItineraryIdeaDetails.card].photoAttribution ?: "Foto: Google" else null,
                    ),
                    lat = row[ItineraryIdeaDetails.lat],
                    lng = row[ItineraryIdeaDetails.lng],
                    placeId = row[ItineraryIdeaDetails.regionPlaceId],
                )
            }
    }

    /** The card limits of Explore Inspirasi; no photo link is kept (it is signed on read). */
    private fun clean(c: InspirationCardDto): InspirationCardDto {
        fun short(text: String?, max: Int) = text?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
        val region = c.region.trim().take(80)
        return InspirationCardDto(
            id = c.id.take(40),
            activity = ideaActivityTitle(c.activity, region).take(80),
            region = region,
            countryCode = c.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) },
            why = c.why.trim().take(200),
            access = short(c.access, 80),
            bestSeason = short(c.bestSeason, 80),
            level = short(c.level, 60),
            requirements = c.requirements.mapNotNull { short(it, 120) }.take(6),
            costRange = c.costRange?.takeIf { it.minMinor >= 0 && it.maxMinor >= it.minMinor && it.currency.length == 3 }
                ?.let { InspirationCostRangeDto(it.minMinor, it.maxMinor, it.currency.uppercase()) },
            visaNote = short(c.visaNote, 200),
            safetyLine = short(c.safetyLine, 200),
            photoAttribution = short(c.photoAttribution, 80),
            durationDays = c.durationDays?.takeIf { it in 1..60 },
            surprise = c.surprise,
        )
    }
}
