package app.ransel.server.service

import app.ransel.server.AppConfig
import app.ransel.server.ai.PhotoSigner
import app.ransel.server.db.SuggestionRoundPlaces
import app.ransel.server.db.SuggestionRounds
import app.ransel.server.db.SuggestionVotes
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.VoteStage
import app.ransel.shared.dto.RecommendationItem
import app.ransel.shared.dto.RoundDto
import app.ransel.shared.dto.RoundPlaceDto
import app.ransel.shared.dto.RoundSummaryDto
import app.ransel.shared.dto.TallyDto
import app.ransel.shared.dto.VoterDto
import app.ransel.shared.model.Category
import app.ransel.shared.model.NotificationType
import app.ransel.shared.model.Role
import app.ransel.shared.model.Vote
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Itinerary voting (docs/specs/voting-spec.md): a search on a trip with 2+ members becomes a round, every member votes
 * per card, the OWNER decides. Only current members count: a member who left keeps their vote rows, but the tally and
 * "who finished" read through `trip_members`, so those votes simply stop counting.
 */
class RoundService(private val config: AppConfig, private val photoSigner: PhotoSigner) {

    // ── used by the search ──────────────────────────────────────────────────

    fun memberCount(tripId: UUID): Int = transaction { currentMembers(tripId).size }

    /** Before a new search on a multi-member trip: 409 ROUND_LIMIT when the trip has too many open rounds. */
    fun requireRoomForNewRound(tripId: UUID, now: Instant = Instant.now()) {
        val open = transaction {
            SuggestionRounds.selectAll()
                .where { (SuggestionRounds.tripId eq tripId) and SuggestionRounds.decidedAt.isNull() and (SuggestionRounds.expiresAt greater now) }
                .count()
        }
        if (open >= config.maxOpenRounds) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCode.ROUND_LIMIT, "This trip already has ${config.maxOpenRounds} open voting rounds")
        }
    }

    /** Before a next batch for [roundId]: the round must be the caller's own, on this trip, and still open. */
    fun requireAppendable(tripId: UUID, roundId: UUID, userId: UUID, now: Instant = Instant.now()) {
        val round = transaction { findRound(tripId, roundId, now) } ?: throw roundNotFound()
        if (round[SuggestionRounds.createdBy] != userId) throw forbidden()
        if (round[SuggestionRounds.decidedAt] != null || !now.isBefore(round[SuggestionRounds.closesAt])) throw votingClosed()
    }

    /** Places that were cards in any round of the trip: a later search must not offer them again. */
    fun placeIdsInRounds(tripId: UUID): Set<String> = transaction {
        SuggestionRoundPlaces.join(SuggestionRounds, org.jetbrains.exposed.v1.core.JoinType.INNER, SuggestionRoundPlaces.roundId, SuggestionRounds.id)
            .select(SuggestionRoundPlaces.placeId)
            .where { SuggestionRounds.tripId eq tripId }
            .map { it[SuggestionRoundPlaces.placeId] }
            .toSet()
    }

    /** A new round with the first batch of cards; the other members are asked to vote in the same transaction. */
    fun create(tripId: UUID, creator: UUID, cards: List<Pair<Category, RecommendationItem>>, now: Instant = Instant.now()): UUID {
        val roundId = UUID.randomUUID()
        transaction {
            val closesAt = now.plus(config.votingHours.toLong(), ChronoUnit.HOURS)
            SuggestionRounds.insert {
                it[id] = roundId
                it[SuggestionRounds.tripId] = tripId
                it[createdBy] = creator
                it[createdAt] = now
                it[SuggestionRounds.closesAt] = closesAt
                it[expiresAt] = now.plus(config.roundExpiryDays.toLong(), ChronoUnit.DAYS)
            }
            insertCards(roundId, cards, startAt = 0)
            val title = tripTitle(tripId)
            val name = displayName(creator)
            Notify.emit {
                notify(currentMembers(tripId).filter { it != creator }, NotificationType.VOTE_REQUESTED, tripId, "vote-open:$roundId") {
                    mapOf(
                        "tripTitle" to title, "roundId" to roundId.toString(), "stage" to VoteStage.OPEN, "actorName" to name,
                        "placeCount" to cards.size.toString(), "closesAt" to closesAt.toString(),
                    )
                }
            }
        }
        return roundId
    }

    /** A next batch: its cards go after the existing ones. A place already in the round is skipped. */
    fun append(roundId: UUID, cards: List<Pair<Category, RecommendationItem>>) {
        transaction {
            val count = SuggestionRoundPlaces.selectAll().where { SuggestionRoundPlaces.roundId eq roundId }.count().toInt()
            insertCards(roundId, cards, startAt = count)
        }
    }

    // ── API ─────────────────────────────────────────────────────────────────

    fun list(tripId: UUID, userId: UUID, now: Instant = Instant.now()): List<RoundSummaryDto> {
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val rounds = SuggestionRounds.selectAll()
                .where { (SuggestionRounds.tripId eq tripId) and (SuggestionRounds.expiresAt greater now) }
                .orderBy(SuggestionRounds.createdAt to SortOrder.DESC)
                .toList()
            if (rounds.isEmpty()) return@transaction emptyList()
            val ids = rounds.map { it[SuggestionRounds.id] }
            val places = SuggestionRoundPlaces.selectAll().where { SuggestionRoundPlaces.roundId inList ids }.toList()
            val myVotes = SuggestionVotes.selectAll()
                .where { (SuggestionVotes.roundId inList ids) and (SuggestionVotes.userId eq userId) }
                .groupingBy { it[SuggestionVotes.roundId] }.eachCount()
            val names = names(rounds.map { it[SuggestionRounds.createdBy] })
            rounds.map { round ->
                val id = round[SuggestionRounds.id]
                val mine = places.filter { it[SuggestionRoundPlaces.roundId] == id }
                RoundSummaryDto(
                    id = id.toString(),
                    tripId = tripId.toString(),
                    createdByUserId = round[SuggestionRounds.createdBy].toString(),
                    createdByName = names[round[SuggestionRounds.createdBy]].orEmpty(),
                    createdAt = round[SuggestionRounds.createdAt].toTimestamp(),
                    closesAt = round[SuggestionRounds.closesAt].toTimestamp(),
                    decidedAt = round[SuggestionRounds.decidedAt]?.toTimestamp(),
                    placeCount = mine.size,
                    myVoteCount = myVotes[id] ?: 0,
                    chosenCount = if (round[SuggestionRounds.decidedAt] != null) mine.count { it[SuggestionRoundPlaces.chosen] } else null,
                )
            }
        }
    }

    fun get(tripId: UUID, roundId: UUID, userId: UUID, now: Instant = Instant.now()): RoundDto {
        val role = requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            val round = findRound(tripId, roundId, now) ?: throw roundNotFound()
            val places = places(roundId)
            val members = currentMembers(tripId)
            val votes = SuggestionVotes.selectAll().where { SuggestionVotes.roundId eq roundId }.toList()
            val counted = votes.filter { it[SuggestionVotes.userId] in members }
            val isOwner = role == Role.OWNER
            val closed = !now.isBefore(round[SuggestionRounds.closesAt]) || round[SuggestionRounds.decidedAt] != null
            val showTally = isOwner || closed
            val votedCount = counted.groupingBy { it[SuggestionVotes.userId] }.eachCount()
            val names = names(members + round[SuggestionRounds.createdBy])
            RoundDto(
                id = roundId.toString(),
                tripId = tripId.toString(),
                createdByUserId = round[SuggestionRounds.createdBy].toString(),
                createdByName = names[round[SuggestionRounds.createdBy]].orEmpty(),
                createdAt = round[SuggestionRounds.createdAt].toTimestamp(),
                closesAt = round[SuggestionRounds.closesAt].toTimestamp(),
                decidedAt = round[SuggestionRounds.decidedAt]?.toTimestamp(),
                expiresAt = round[SuggestionRounds.expiresAt].toTimestamp(),
                places = places.map { RoundPlaceDto(it[SuggestionRoundPlaces.category], withFreshPhoto(it[SuggestionRoundPlaces.snapshot])) },
                myVotes = votes.filter { it[SuggestionVotes.userId] == userId }
                    .associate { it[SuggestionVotes.placeId] to it[SuggestionVotes.vote] },
                memberCount = members.size,
                finishedCount = if (showTally) members.count { (votedCount[it] ?: 0) >= places.size } else null,
                tally = if (!showTally) null else places.map { place ->
                    val placeId = place[SuggestionRoundPlaces.placeId]
                    val onPlace = counted.filter { it[SuggestionVotes.placeId] == placeId }
                    TallyDto(
                        placeId = placeId,
                        likes = onPlace.count { it[SuggestionVotes.vote] == Vote.LIKE },
                        skips = onPlace.count { it[SuggestionVotes.vote] == Vote.SKIP },
                        likedBy = if (isOwner) onPlace.filter { it[SuggestionVotes.vote] == Vote.LIKE }.map { it[SuggestionVotes.userId].toString() } else emptyList(),
                    )
                },
                voters = if (!isOwner) null else members.map { member ->
                    VoterDto(member.toString(), names[member].orEmpty(), votedCount[member] ?: 0)
                },
                chosenPlaceIds = places.filter { it[SuggestionRoundPlaces.chosen] }.map { it[SuggestionRoundPlaces.placeId] },
            )
        }
    }

    /** Any member, any role. Replaces an earlier vote. The vote that makes everyone finished tells the OWNER. */
    fun vote(tripId: UUID, roundId: UUID, placeId: String, userId: UUID, vote: Vote, now: Instant = Instant.now()) {
        requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val round = requireVotable(tripId, roundId, placeId, now)
            SuggestionVotes.upsert(
                onUpdateExclude = listOf(SuggestionVotes.createdAt),
            ) {
                it[SuggestionVotes.roundId] = roundId
                it[SuggestionVotes.placeId] = placeId
                it[SuggestionVotes.userId] = userId
                it[SuggestionVotes.vote] = vote
                it[createdAt] = now
                it[updatedAt] = now
            }
            // The OWNER casting the last vote needs no message about it; the scheduler still sends one at closing time.
            if (everyoneFinished(tripId, roundId)) readyToDecide(round, skip = userId)
        }
    }

    /** Undo while voting is open. Removing a vote that is not there is fine. */
    fun undo(tripId: UUID, roundId: UUID, placeId: String, userId: UUID, now: Instant = Instant.now()) {
        requireRole(tripId, userId, Role.VIEWER)
        transaction {
            requireVotable(tripId, roundId, placeId, now)
            SuggestionVotes.deleteWhere {
                (SuggestionVotes.roundId eq roundId) and (SuggestionVotes.placeId eq placeId) and (SuggestionVotes.userId eq userId)
            }
        }
    }

    /**
     * OWNER only, any time before the round expires. The itinerary items were already created by the OWNER's client
     * (normal sync path); this marks which places were chosen, closes the round and tells the others.
     */
    fun decide(tripId: UUID, roundId: UUID, userId: UUID, placeIds: List<String>, now: Instant = Instant.now()) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val round = findRound(tripId, roundId, now) ?: throw roundNotFound()
            if (round[SuggestionRounds.decidedAt] != null) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.ROUND_ALREADY_DECIDED, "This round was already decided")
            }
            val inRound = places(roundId).map { it[SuggestionRoundPlaces.placeId] }.toSet()
            val chosen = placeIds.distinct()
            if (chosen.any { it !in inRound }) throw validationFailed(mapOf("placeIds" to "NOT_IN_ROUND"))
            // The decided_at guard makes a second, parallel decide update nothing.
            val updated = SuggestionRounds.update({ (SuggestionRounds.id eq roundId) and SuggestionRounds.decidedAt.isNull() }) {
                it[decidedAt] = now
                it[decidedBy] = userId
            }
            if (updated == 0) throw ApiException(HttpStatusCode.Conflict, ErrorCode.ROUND_ALREADY_DECIDED, "This round was already decided")
            if (chosen.isNotEmpty()) {
                SuggestionRoundPlaces.update({ (SuggestionRoundPlaces.roundId eq roundId) and (SuggestionRoundPlaces.placeId inList chosen) }) {
                    it[SuggestionRoundPlaces.chosen] = true
                }
            }
            val title = tripTitle(tripId)
            val name = displayName(userId)
            Notify.emit {
                notify(currentMembers(tripId).filter { it != userId }, NotificationType.VOTE_RESULT, tripId, "vote-decided:$roundId") {
                    mapOf(
                        "tripTitle" to title, "roundId" to roundId.toString(), "stage" to VoteStage.DECIDED,
                        "actorName" to name, "chosenCount" to chosen.size.toString(),
                    )
                }
            }
        }
    }

    // ── scheduled ───────────────────────────────────────────────────────────

    /**
     * Once a minute (from ReminderScheduler): "closes soon" to members who still have cards without a vote, and
     * "ready to decide" to the OWNER of every undecided round past its closing time. Dedupe keys make repeats harmless.
     */
    fun tick(now: Instant = Instant.now()) {
        val soon = now.plus(config.notifyVoteClosingHours.toLong(), ChronoUnit.HOURS)
        transaction {
            SuggestionRounds.selectAll()
                .where { SuggestionRounds.decidedAt.isNull() and (SuggestionRounds.closesAt greater now) and (SuggestionRounds.closesAt lessEq soon) }
                .forEach { closingSoon(it) }
            SuggestionRounds.selectAll()
                .where { SuggestionRounds.decidedAt.isNull() and (SuggestionRounds.closesAt lessEq now) and (SuggestionRounds.expiresAt greater now) }
                .forEach { readyToDecide(it) }
        }
    }

    // ── helpers (call inside a transaction) ────────────────────────────────

    private fun closingSoon(round: ResultRow) {
        val roundId = round[SuggestionRounds.id]
        val tripId = round[SuggestionRounds.tripId]
        val placeCount = places(roundId).size
        val voted = SuggestionVotes.selectAll().where { SuggestionVotes.roundId eq roundId }
            .groupingBy { it[SuggestionVotes.userId] }.eachCount()
        val behind = currentMembers(tripId).filter { (voted[it] ?: 0) < placeCount }
        if (behind.isEmpty()) return
        val title = tripTitle(tripId)
        val name = displayName(round[SuggestionRounds.createdBy])
        Notify.emit {
            notify(behind, NotificationType.VOTE_REQUESTED, tripId, "vote-closing:$roundId") { user ->
                mapOf(
                    "tripTitle" to title, "roundId" to roundId.toString(), "stage" to VoteStage.CLOSING, "actorName" to name,
                    "placeCount" to placeCount.toString(), "closesAt" to round[SuggestionRounds.closesAt].toString(),
                    "remaining" to (placeCount - (voted[user] ?: 0)).toString(),
                )
            }
        }
    }

    private fun readyToDecide(round: ResultRow, skip: UUID? = null) {
        val roundId = round[SuggestionRounds.id]
        val tripId = round[SuggestionRounds.tripId]
        val owner = TripMembers.selectAll().where { (TripMembers.tripId eq tripId) and (TripMembers.role eq Role.OWNER) }
            .singleOrNull()?.get(TripMembers.userId) ?: return
        if (owner == skip) return
        val members = currentMembers(tripId)
        val placeCount = places(roundId).size
        val voted = SuggestionVotes.selectAll().where { SuggestionVotes.roundId eq roundId }
            .groupingBy { it[SuggestionVotes.userId] }.eachCount()
        val finished = members.count { (voted[it] ?: 0) >= placeCount }
        val title = tripTitle(tripId)
        Notify.emit {
            notify(listOf(owner), NotificationType.VOTE_RESULT, tripId, "vote-ready:$roundId") {
                mapOf(
                    "tripTitle" to title, "roundId" to roundId.toString(), "stage" to VoteStage.READY,
                    "voted" to finished.toString(), "members" to members.size.toString(),
                )
            }
        }
    }

    private fun everyoneFinished(tripId: UUID, roundId: UUID): Boolean {
        val placeCount = places(roundId).size
        if (placeCount == 0) return false
        val voted = SuggestionVotes.selectAll().where { SuggestionVotes.roundId eq roundId }
            .groupingBy { it[SuggestionVotes.userId] }.eachCount()
        return currentMembers(tripId).all { (voted[it] ?: 0) >= placeCount }
    }

    private fun requireVotable(tripId: UUID, roundId: UUID, placeId: String, now: Instant): ResultRow {
        val round = findRound(tripId, roundId, now) ?: throw roundNotFound()
        if (round[SuggestionRounds.decidedAt] != null || !now.isBefore(round[SuggestionRounds.closesAt])) throw votingClosed()
        val exists = SuggestionRoundPlaces.selectAll()
            .where { (SuggestionRoundPlaces.roundId eq roundId) and (SuggestionRoundPlaces.placeId eq placeId) }
            .count() > 0
        if (!exists) throw roundNotFound()
        return round
    }

    /** The round on this trip, unless it expired. */
    private fun findRound(tripId: UUID, roundId: UUID, now: Instant): ResultRow? =
        SuggestionRounds.selectAll()
            .where { (SuggestionRounds.id eq roundId) and (SuggestionRounds.tripId eq tripId) and (SuggestionRounds.expiresAt greater now) }
            .singleOrNull()

    private fun places(roundId: UUID): List<ResultRow> =
        SuggestionRoundPlaces.selectAll().where { SuggestionRoundPlaces.roundId eq roundId }
            .orderBy(SuggestionRoundPlaces.sortOrder to SortOrder.ASC)
            .toList()

    private fun insertCards(roundId: UUID, cards: List<Pair<Category, RecommendationItem>>, startAt: Int) {
        cards.forEachIndexed { index, (category, item) ->
            SuggestionRoundPlaces.insertIgnore {
                it[SuggestionRoundPlaces.roundId] = roundId
                it[placeId] = item.placeId
                it[SuggestionRoundPlaces.category] = category
                it[sortOrder] = startAt + index
                it[snapshot] = item
                it[chosen] = false
            }
        }
    }

    /** The stored photo path was signed when the round was made; sign it again so it never runs out while the round lives. */
    private fun withFreshPhoto(item: RecommendationItem): RecommendationItem =
        if (item.photoUrl == null) item else item.copy(photoUrl = photoSigner.signedPath(item.placeId))

    private fun currentMembers(tripId: UUID): List<UUID> =
        TripMembers.selectAll().where { TripMembers.tripId eq tripId }.map { it[TripMembers.userId] }

    private fun names(ids: Collection<UUID>): Map<UUID, String> =
        if (ids.isEmpty()) emptyMap()
        else Users.selectAll().where { Users.id inList ids.distinct() }.associate { it[Users.id] to it[Users.displayName] }

    private fun tripTitle(tripId: UUID) = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull()?.get(Trips.title).orEmpty()
    private fun displayName(userId: UUID) = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.displayName).orEmpty()

    private fun roundNotFound() = ApiException(HttpStatusCode.NotFound, ErrorCode.ROUND_NOT_FOUND, "Voting round not found")
    private fun votingClosed() = ApiException(HttpStatusCode.Conflict, ErrorCode.VOTING_CLOSED, "Voting for this round is closed")
}
