package app.ransel.server.service

import app.ransel.server.db.Expenses
import app.ransel.server.db.ItineraryItems
import app.ransel.server.db.PollOptions
import app.ransel.server.db.PollVotes
import app.ransel.server.db.Polls
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Trips
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.forbidden
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.MAX_POLL_OPTIONS
import app.ransel.shared.domain.POLL_REMINDER_BEFORE
import app.ransel.shared.domain.POLL_TIE_REOPEN_AFTER
import app.ransel.shared.domain.POLL_TIE_REOPEN_FOR
import app.ransel.shared.domain.PollAction
import app.ransel.shared.domain.PollOutcome
import app.ransel.shared.domain.actionFor
import app.ransel.shared.domain.canChangeAnonymous
import app.ransel.shared.domain.effectiveAnonymous
import app.ransel.shared.domain.isAnonymousForced
import app.ransel.shared.domain.optionChangesVotes
import app.ransel.shared.domain.pollOptionLabel
import app.ransel.shared.domain.pollOutcome
import app.ransel.shared.domain.splitEqually
import app.ransel.shared.domain.stayEstimateMinor
import app.ransel.shared.domain.validatePollOption
import app.ransel.shared.domain.validatePollQuestion
import app.ransel.shared.dto.CreatePollRequest
import app.ransel.shared.dto.DecidePollRequest
import app.ransel.shared.dto.ExpenseSplitDto
import app.ransel.shared.dto.PollDto
import app.ransel.shared.dto.PollShareDto
import app.ransel.shared.dto.PollOptionDto
import app.ransel.shared.dto.PollOptionInput
import app.ransel.shared.dto.ReopenPollRequest
import app.ransel.shared.dto.UpdatePollRequest
import app.ransel.shared.dto.VotePollRequest
import app.ransel.shared.model.Category
import app.ransel.shared.model.ItemSource
import app.ransel.shared.model.NotificationType
import app.ransel.shared.model.PaymentMethod
import app.ransel.shared.model.PollKind
import app.ransel.shared.model.PollStatus
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant

/**
 * Polls: one question for the group, voted by every member with an account, decided by the creator (docs: PRD "Polling
 * with templates", "Poll edge cases"). The status is settled lazily: whoever reads or votes first after `closes_at`
 * turns OPEN into CLOSED or TIED; the minute job in [tick] does the same for polls nobody looks at, and sends the
 * reminders and the tie reopen.
 */
object PollService {
    // ── reads ───────────────────────────────────────────────────────────────

    fun list(userId: UUID, tripId: UUID): List<PollDto> {
        requireRole(tripId, userId, Role.VIEWER)
        val me = callerMemberId(tripId, userId)
        return transaction {
            settleDue(tripId = tripId)
            Polls.selectAll()
                .where { (Polls.tripId eq tripId) and Polls.deletedAt.isNull() }
                .orderBy(Polls.createdAt to SortOrder.DESC, Polls.id to SortOrder.DESC)
                .map { toDto(it, me) }
        }
    }

    fun get(userId: UUID, pollId: UUID): PollDto {
        val row = loadRow(pollId)
        val tripId = row[Polls.tripId]
        requireRole(tripId, userId, Role.VIEWER)
        return transaction {
            settleDue(pollId = pollId)
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    // ── create, edit, delete ────────────────────────────────────────────────

    fun create(userId: UUID, tripId: UUID, body: CreatePollRequest): PollDto {
        requireRole(tripId, userId, Role.EDITOR)
        val id = uuid(body.id, "id")
        val now = Instant.now()
        val closesAt = body.closesAt.toJavaInstant()
        requireValid(
            buildMap {
                putAll(validatePollQuestion(body.question))
                if (closesAt <= now) put("closesAt", "MUST_BE_IN_THE_FUTURE")
                if (body.options.size > MAX_POLL_OPTIONS) put("options", "TOO_MANY")
                if (body.options.map { it.id }.toSet().size != body.options.size) put("options", "DUPLICATE_ID")
                body.options.forEachIndexed { i, option ->
                    validatePollOption(body.kind, option).forEach { (field, code) -> put("options[$i].$field", code) }
                    if (runCatching { UUID.fromString(option.id) }.isFailure) put("options[$i].id", "INVALID_UUID")
                }
            },
        )
        return transaction {
            if (Polls.select(Polls.id).where { Polls.id eq id }.any()) throw conflict("Poll id already exists")
            val me = callerMemberId(tripId, userId)
            Polls.insert {
                it[Polls.id] = id
                it[Polls.tripId] = tripId
                it[kind] = body.kind
                it[question] = body.question.trim()
                it[createdByMemberId] = me
                it[Polls.closesAt] = closesAt
                it[multiChoice] = body.multiChoice
                it[isAnonymous] = effectiveAnonymous(body.kind, body.anonymous)
                it[applyWinner] = body.applyWinner
                it[dayDate] = body.dayDate?.toJavaLocalDate()
                it[startTime] = body.startTime?.toJavaLocalTime()
                it[status] = PollStatus.OPEN
            }
            body.options.forEachIndexed { i, option -> insertOption(id, body.kind, option, i) }
            val row = loadRow(id)
            tell(NotificationType.POLL_REQUESTED, row, "poll-open:$id", activeMemberUserIds(tripId).filter { it != userId }) {
                mapOf("stage" to "OPEN", "actorName" to nameOf(userId), "closesAt" to body.closesAt.toString())
            }
            toDto(row, me)
        }
    }

    fun update(userId: UUID, pollId: UUID, body: UpdatePollRequest): PollDto {
        val row0 = loadRow(pollId)
        manager(userId, row0)
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            val kind = row[Polls.kind]
            val problems = buildMap {
                body.question?.let { putAll(validatePollQuestion(it)) }
                if (body.closesAt != null && body.closesAt!!.toJavaInstant() <= Instant.now()) put("closesAt", "MUST_BE_IN_THE_FUTURE")
                if (body.anonymous == false && isAnonymousForced(kind)) put("anonymous", "ALWAYS_ON_FOR_BUDGET")
            }
            requireValid(problems)
            val voted = row[Polls.anonymityLocked]
            if (body.anonymous != null && body.anonymous != row[Polls.isAnonymous] && !canChangeAnonymous(if (voted) 1 else 0)) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.POLL_LOCKED, "The anonymous switch is locked after the first vote")
            }
            if (body.multiChoice != null && body.multiChoice != row[Polls.multiChoice] && voted) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.POLL_LOCKED, "Choices cannot change after the first vote")
            }
            if ((body.question != null || body.closesAt != null) && row[Polls.status] != PollStatus.OPEN) throw closed()
            Polls.update({ Polls.id eq pollId }) {
                body.question?.let { q -> it[question] = q.trim() }
                body.closesAt?.let { c -> it[closesAt] = c.toJavaInstant() }
                body.multiChoice?.let { m -> it[multiChoice] = m }
                body.anonymous?.let { a -> it[isAnonymous] = effectiveAnonymous(kind, a) }
                body.applyWinner?.let { w -> it[applyWinner] = w }
            }
            toDto(loadRow(pollId), callerMemberId(row[Polls.tripId], userId))
        }
    }

    fun delete(userId: UUID, pollId: UUID) {
        manager(userId, loadRow(pollId))
        transaction { Polls.update({ Polls.id eq pollId }) { it[deletedAt] = Instant.now() } }
    }

    // ── options ─────────────────────────────────────────────────────────────

    fun addOption(userId: UUID, pollId: UUID, option: PollOptionInput): PollDto {
        val row0 = loadRow(pollId)
        val tripId = row0[Polls.tripId]
        requireRole(tripId, userId, Role.EDITOR)
        val kind = row0[Polls.kind]
        requireValid(optionProblems(kind, option))
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.OPEN) throw closed()
            val existing = PollOptions.selectAll().where { (PollOptions.pollId eq pollId) and PollOptions.deletedAt.isNull() }.count().toInt()
            if (existing >= MAX_POLL_OPTIONS) throw validationFailed(mapOf("options" to "TOO_MANY"))
            if (PollOptions.select(PollOptions.id).where { PollOptions.id eq uuid(option.id, "id") }.any()) throw conflict("Option id already exists")
            insertOption(pollId, kind, option, existing)
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    /** The ticked Jelajah results, all or none: the same checks as [addOption] for each, and the limit for the total. */
    fun addOptions(userId: UUID, pollId: UUID, options: List<PollOptionInput>): PollDto {
        val row0 = loadRow(pollId)
        val tripId = row0[Polls.tripId]
        requireRole(tripId, userId, Role.EDITOR)
        val kind = row0[Polls.kind]
        if (options.isEmpty()) throw validationFailed(mapOf("options" to "REQUIRED"))
        if (options.size > MAX_POLL_OPTIONS) throw validationFailed(mapOf("options" to "TOO_MANY"))
        requireValid(options.withIndex().flatMap { (i, o) -> optionProblems(kind, o).map { (k, v) -> "options[$i].$k" to v } }.toMap())
        val ids = options.map { uuid(it.id, "id") }
        if (ids.toSet().size != ids.size) throw validationFailed(mapOf("options" to "DUPLICATE_ID"))
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.OPEN) throw closed()
            val existing = PollOptions.selectAll().where { (PollOptions.pollId eq pollId) and PollOptions.deletedAt.isNull() }.count().toInt()
            if (existing + options.size > MAX_POLL_OPTIONS) throw validationFailed(mapOf("options" to "TOO_MANY"))
            if (PollOptions.select(PollOptions.id).where { PollOptions.id inList ids }.any()) throw conflict("Option id already exists")
            options.forEachIndexed { i, option -> insertOption(pollId, kind, option, existing + i) }
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    /** What Jelajah needs to know about a poll, after the checks: who may search, which kinds, and that it is still open. */
    class SearchContext(
        val tripId: UUID,
        val kind: PollKind,
        val destination: String,
        val startDate: kotlinx.datetime.LocalDate,
        val endDate: kotlinx.datetime.LocalDate,
        val currency: String,
        val placeIds: Set<String>,
    )

    fun searchContext(userId: UUID, pollId: UUID): SearchContext {
        val poll = loadRow(pollId)
        val tripId = poll[Polls.tripId]
        requireRole(tripId, userId, Role.EDITOR)
        val kind = poll[Polls.kind]
        if (kind != PollKind.STAY && kind != PollKind.FOOD) throw validationFailed(mapOf("kind" to "SEARCH_NOT_SUPPORTED"))
        return transaction {
            settleDue(pollId = pollId)
            if (loadRow(pollId)[Polls.status] != PollStatus.OPEN) throw closed()
            val trip = Trips.selectAll().where { Trips.id eq tripId }.single()
            SearchContext(
                tripId, kind, trip[Trips.destination], trip[Trips.startDate].toKotlinLocalDate(), trip[Trips.endDate].toKotlinLocalDate(),
                trip[Trips.baseCurrency].trim(), activeOptions(pollId).mapNotNull { it[PollOptions.placeId] }.toSet(),
            )
        }
    }

    /** Editing what people voted on clears the votes on that option and asks those voters to choose again. */
    fun updateOption(userId: UUID, pollId: UUID, optionId: UUID, option: PollOptionInput): PollDto {
        val row0 = loadRow(pollId)
        val tripId = row0[Polls.tripId]
        requireRole(tripId, userId, Role.EDITOR)
        val kind = row0[Polls.kind]
        requireValid(optionProblems(kind, option, checkId = false))
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.OPEN) throw closed()
            val current = activeOption(pollId, optionId)
            val before = current.toInput()
            val after = option.copy(id = optionId.toString())
            val changes = optionChangesVotes(before, after.copy(label = pollOptionLabel(kind, after)))
            PollOptions.update({ PollOptions.id eq optionId }) {
                it[label] = pollOptionLabel(kind, after)
                it[startDate] = after.startDate?.toJavaLocalDate()
                it[endDate] = after.endDate?.toJavaLocalDate()
                it[placeId] = after.placeId
                it[url] = after.url
                it[imageUrl] = after.imageUrl
                it[priceMinor] = after.priceMinor
                it[priceUnit] = after.priceUnit
                it[minMinor] = after.minMinor
                it[maxMinor] = after.maxMinor
                if (changes) it[version] = current[PollOptions.version] + 1
            }
            if (changes) clearVotes(row, optionId, pollOptionLabel(kind, after), current[PollOptions.version] + 1)
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    fun deleteOption(userId: UUID, pollId: UUID, optionId: UUID): PollDto {
        val row0 = loadRow(pollId)
        val tripId = row0[Polls.tripId]
        requireRole(tripId, userId, Role.EDITOR)
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.OPEN) throw closed()
            val current = activeOption(pollId, optionId)
            clearVotes(row, optionId, current[PollOptions.label], current[PollOptions.version])
            PollOptions.update({ PollOptions.id eq optionId }) { it[deletedAt] = Instant.now() }
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    // ── voting ──────────────────────────────────────────────────────────────

    /** Replaces the caller's votes. A vote on an option edited since the voter saw it is refused (VOTE_STALE). */
    fun vote(userId: UUID, pollId: UUID, body: VotePollRequest): PollDto {
        val tripId = loadRow(pollId)[Polls.tripId]
        requireRole(tripId, userId, Role.VIEWER)
        return transaction { voteAs(pollId, callerMemberId(tripId, userId), body) }
    }

    /** Replaces [me]'s votes; also used by the guest page. Call inside a transaction. */
    internal fun voteAs(pollId: UUID, me: UUID, body: VotePollRequest): PollDto {
        settleDue(pollId = pollId)
        val row = loadRow(pollId)
        if (row[Polls.status] != PollStatus.OPEN) throw closed()
        val options = activeOptions(pollId).associateBy { it[PollOptions.id] }
        val chosen = body.votes.map { uuid(it.optionId, "votes") to it.version }
        if (chosen.map { it.first }.toSet().size != chosen.size) throw validationFailed(mapOf("votes" to "DUPLICATE_OPTION"))
        if (!row[Polls.multiChoice] && chosen.size > 1) throw validationFailed(mapOf("votes" to "ONLY_ONE_CHOICE"))
        chosen.forEach { (optionId, version) ->
            val option = options[optionId] ?: throw validationFailed(mapOf("votes" to "UNKNOWN_OPTION"))
            if (option[PollOptions.version] != version) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.VOTE_STALE, "This option changed: load the poll again")
            }
        }
        PollVotes.deleteWhere { (PollVotes.pollId eq pollId) and (PollVotes.memberId eq me) }
        chosen.forEach { (optionId, version) ->
            PollVotes.insert {
                it[PollVotes.optionId] = optionId
                it[PollVotes.pollId] = pollId
                it[memberId] = me
                it[optionVersion] = version
            }
        }
        if (chosen.isNotEmpty() && !row[Polls.anonymityLocked]) {
            Polls.update({ Polls.id eq pollId }) { it[anonymityLocked] = true }
        }
        return toDto(loadRow(pollId), me)
    }

    // ── guest link ──────────────────────────────────────────────────────────

    /** "Bagikan": the poll's guest link, made once and kept until it is turned off. Any member may share. */
    fun share(userId: UUID, pollId: UUID, baseUrl: String): PollShareDto {
        val tripId = loadRow(pollId)[Polls.tripId]
        requireRole(tripId, userId, Role.VIEWER)
        val code = transaction {
            loadRow(pollId)[Polls.shareCode] ?: run {
                var made: String
                do { made = newShareCode() } while (Polls.select(Polls.id).where { Polls.shareCode eq made }.any())
                Polls.update({ Polls.id eq pollId }) { it[shareCode] = made }
                made
            }
        }
        return PollShareDto(code, "${baseUrl.trimEnd('/')}/p/$code")
    }

    /** Turns the link off (the creator or the OWNER); sharing again makes a new code. */
    fun unshare(userId: UUID, pollId: UUID) {
        val row = loadRow(pollId)
        val role = requireRole(row[Polls.tripId], userId, Role.VIEWER)
        if (role != Role.OWNER && row[Polls.createdByMemberId] != callerMemberId(row[Polls.tripId], userId)) throw forbidden()
        transaction { Polls.update({ Polls.id eq pollId }) { it[shareCode] = null } }
    }

    /** The guest page's polls: open ones, plus [focus] whatever its status. Call inside a transaction. */
    internal fun forGuest(tripId: UUID, me: UUID?, focus: UUID?): List<PollDto> {
        settleDue(tripId = tripId)
        val viewer = me ?: UUID(0, 0)
        return Polls.selectAll()
            .where { (Polls.tripId eq tripId) and Polls.deletedAt.isNull() }
            .orderBy(Polls.closesAt to SortOrder.ASC)
            .filter { it[Polls.status] == PollStatus.OPEN || it[Polls.id] == focus }
            .sortedByDescending { it[Polls.id] == focus }
            .map { toDto(it, viewer) }
    }

    private val shareRandom = java.security.SecureRandom()

    /** Six characters without look-alikes (no 0/O, 1/I/L). */
    private fun newShareCode(): String {
        val alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        return (1..6).map { alphabet[shareRandom.nextInt(alphabet.length)] }.joinToString("")
    }

    // ── closing and deciding ────────────────────────────────────────────────

    /** Ends voting now. */
    fun close(userId: UUID, pollId: UUID): PollDto {
        val row0 = loadRow(pollId)
        manager(userId, row0)
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.OPEN) throw closed()
            Polls.update({ Polls.id eq pollId }) { it[closesAt] = Instant.now() }
            settleDue(pollId = pollId)
            toDto(loadRow(pollId), callerMemberId(row[Polls.tripId], userId))
        }
    }

    /** Confirms the winner and, with "apply winner", runs its action; `optionId` null closes the poll without choosing. */
    fun decide(userId: UUID, pollId: UUID, body: DecidePollRequest): PollDto {
        val row0 = loadRow(pollId)
        manager(userId, row0)
        val tripId = row0[Polls.tripId]
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] == PollStatus.DECIDED) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "This poll was already decided")
            }
            val option = body.optionId?.let { raw ->
                val id = uuid(raw, "optionId")
                val found = activeOption(pollId, id)
                if (row[Polls.status] == PollStatus.TIED && id !in tiedIds(row)) {
                    throw validationFailed(mapOf("optionId" to "NOT_ONE_OF_THE_TIED"))
                }
                found
            }
            Polls.update({ Polls.id eq pollId }) {
                it[status] = PollStatus.DECIDED
                it[decidedOptionId] = option?.get(PollOptions.id)
                it[decidedAt] = Instant.now()
            }
            if (option != null && row[Polls.applyWinner]) applyAction(row, option)
            tell(NotificationType.POLL_RESULT, row, "poll-decided:$pollId", activeMemberUserIds(tripId).filter { it != userId }) {
                buildMap {
                    put("stage", "DECIDED")
                    put("actorName", nameOf(userId))
                    option?.let { put("winner", it[PollOptions.label]) }
                }
            }
            toDto(loadRow(pollId), callerMemberId(tripId, userId))
        }
    }

    /** A closed or tied poll takes votes again, until the new closing time. */
    fun reopen(userId: UUID, pollId: UUID, body: ReopenPollRequest): PollDto {
        val row0 = loadRow(pollId)
        manager(userId, row0)
        val closesAt = body.closesAt.toJavaInstant()
        requireValid(if (closesAt <= Instant.now()) mapOf("closesAt" to "MUST_BE_IN_THE_FUTURE") else emptyMap())
        return transaction {
            settleDue(pollId = pollId)
            val row = loadRow(pollId)
            if (row[Polls.status] != PollStatus.CLOSED && row[Polls.status] != PollStatus.TIED) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, "Only a closed or tied poll can reopen")
            }
            reopenRow(row, closesAt, nameOf(userId))
            toDto(loadRow(pollId), callerMemberId(row[Polls.tripId], userId))
        }
    }

    /** A creator who leaves or is removed hands their polls to the trip owner. Runs in the caller's transaction. */
    internal fun handOver(tripId: UUID, leavingMemberId: UUID) {
        val owner = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() and (TripMembers.role eq Role.OWNER) }
            .singleOrNull()?.get(TripMembers.id) ?: return
        Polls.update({
            (Polls.tripId eq tripId) and (Polls.createdByMemberId eq leavingMemberId) and
                (Polls.status inList listOf(PollStatus.OPEN, PollStatus.TIED, PollStatus.CLOSED))
        }) { it[createdByMemberId] = owner }
    }

    // ── the minute job ──────────────────────────────────────────────────────

    /** Settles polls that ran out, reopens ties nobody decided, and reminds members who have not voted. Safe to repeat. */
    fun tick(now: Instant = Instant.now()) {
        transaction {
            settleDue(now = now)
            Polls.selectAll()
                .where { (Polls.status eq PollStatus.TIED) and Polls.deletedAt.isNull() and (Polls.tiedAt lessEq now.minus(POLL_TIE_REOPEN_AFTER.toJavaDuration())) }
                .forEach { reopenRow(it, now.plus(POLL_TIE_REOPEN_FOR.toJavaDuration()), actor = null) }
            Polls.selectAll()
                .where {
                    (Polls.status eq PollStatus.OPEN) and Polls.deletedAt.isNull() and
                        (Polls.closesAt lessEq now.plus(POLL_REMINDER_BEFORE.toJavaDuration())) and (Polls.closesAt greaterEq now)
                }
                .forEach { remind(it) }
        }
    }

    // ── helpers: loading ────────────────────────────────────────────────────

    private fun loadRow(pollId: UUID): ResultRow = transaction {
        Polls.selectAll().where { (Polls.id eq pollId) and Polls.deletedAt.isNull() }.singleOrNull()
    } ?: throw notFound("Poll")

    private fun activeOptions(pollId: UUID): List<ResultRow> =
        PollOptions.selectAll().where { (PollOptions.pollId eq pollId) and PollOptions.deletedAt.isNull() }
            .orderBy(PollOptions.sortOrder to SortOrder.ASC, PollOptions.createdAt to SortOrder.ASC).toList()

    private fun activeOption(pollId: UUID, optionId: UUID): ResultRow =
        PollOptions.selectAll().where { (PollOptions.id eq optionId) and (PollOptions.pollId eq pollId) and PollOptions.deletedAt.isNull() }
            .singleOrNull() ?: throw notFound("Option")

    /** The creator of the poll, or the trip owner. */
    private fun manager(userId: UUID, row: ResultRow) {
        val tripId = row[Polls.tripId]
        val role = requireRole(tripId, userId, Role.VIEWER)
        if (role != Role.OWNER && row[Polls.createdByMemberId] != callerMemberId(tripId, userId)) throw forbidden()
    }

    private fun closed() = ApiException(HttpStatusCode.Conflict, ErrorCode.POLL_CLOSED, "Voting on this poll is over")

    private fun uuid(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw validationFailed(mapOf(field to "INVALID_UUID"))

    private fun optionProblems(kind: PollKind, option: PollOptionInput, checkId: Boolean = true): Map<String, String> = buildMap {
        putAll(validatePollOption(kind, option))
        if (checkId && runCatching { UUID.fromString(option.id) }.isFailure) put("id", "INVALID_UUID")
    }

    private fun insertOption(pollId: UUID, kind: PollKind, option: PollOptionInput, order: Int) {
        PollOptions.insert {
            it[id] = UUID.fromString(option.id)
            it[PollOptions.pollId] = pollId
            it[label] = pollOptionLabel(kind, option)
            it[startDate] = option.startDate?.toJavaLocalDate()
            it[endDate] = option.endDate?.toJavaLocalDate()
            it[placeId] = option.placeId
            it[url] = option.url
            it[imageUrl] = option.imageUrl
            it[priceMinor] = option.priceMinor
            it[priceUnit] = option.priceUnit
            it[minMinor] = option.minMinor
            it[maxMinor] = option.maxMinor
            it[sortOrder] = order
        }
    }

    private fun ResultRow.toInput() = PollOptionInput(
        id = this[PollOptions.id].toString(),
        label = this[PollOptions.label],
        startDate = this[PollOptions.startDate]?.toKotlinLocalDate(),
        endDate = this[PollOptions.endDate]?.toKotlinLocalDate(),
        placeId = this[PollOptions.placeId],
        url = this[PollOptions.url],
        imageUrl = this[PollOptions.imageUrl],
        priceMinor = this[PollOptions.priceMinor],
        priceUnit = this[PollOptions.priceUnit],
        minMinor = this[PollOptions.minMinor],
        maxMinor = this[PollOptions.maxMinor],
    )

    /** Votes that count: from current members, on the current version of an option that still exists. */
    private fun validVotes(row: ResultRow, options: List<ResultRow>): List<Pair<UUID, UUID>> {
        val active = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq row[Polls.tripId]) and TripMembers.active() }.map { it[TripMembers.id] }.toSet()
        val versions = options.associate { it[PollOptions.id] to it[PollOptions.version] }
        return PollVotes.selectAll().where { PollVotes.pollId eq row[Polls.id] }
            .filter { it[PollVotes.memberId] in active && versions[it[PollVotes.optionId]] == it[PollVotes.optionVersion] }
            .map { it[PollVotes.optionId] to it[PollVotes.memberId] }
    }

    private fun counts(row: ResultRow): Map<String, Int> {
        val options = activeOptions(row[Polls.id])
        val votes = validVotes(row, options)
        return options.associate { o -> o[PollOptions.id].toString() to votes.count { it.first == o[PollOptions.id] } }
    }

    private fun tiedIds(row: ResultRow): List<UUID> = (pollOutcome(counts(row)) as? PollOutcome.Tie)?.optionIds?.map(UUID::fromString).orEmpty()

    private fun toDto(row: ResultRow, me: UUID): PollDto {
        val pollId = row[Polls.id]
        val options = activeOptions(pollId)
        val votes = validVotes(row, options)
        val anonymous = row[Polls.isAnonymous]
        val names = memberNames(listOf(row[Polls.createdByMemberId]))
        val outcome = pollOutcome(options.associate { o -> o[PollOptions.id].toString() to votes.count { it.first == o[PollOptions.id] } })
        return PollDto(
            id = pollId.toString(),
            tripId = row[Polls.tripId].toString(),
            kind = row[Polls.kind],
            question = row[Polls.question],
            createdByMemberId = row[Polls.createdByMemberId].toString(),
            createdByName = names[row[Polls.createdByMemberId]].orEmpty(),
            createdAt = row[Polls.createdAt].toTimestamp(),
            closesAt = row[Polls.closesAt].toTimestamp(),
            multiChoice = row[Polls.multiChoice],
            anonymous = anonymous,
            applyWinner = row[Polls.applyWinner],
            dayDate = row[Polls.dayDate]?.toKotlinLocalDate(),
            startTime = row[Polls.startTime]?.toKotlinLocalTime(),
            status = row[Polls.status],
            decidedOptionId = row[Polls.decidedOptionId]?.toString(),
            tiedOptionIds = if (row[Polls.status] == PollStatus.TIED) (outcome as? PollOutcome.Tie)?.optionIds.orEmpty() else emptyList(),
            options = options.map { o ->
                val voters = votes.filter { it.first == o[PollOptions.id] }.map { it.second }
                PollOptionDto(
                    id = o[PollOptions.id].toString(),
                    label = o[PollOptions.label],
                    startDate = o[PollOptions.startDate]?.toKotlinLocalDate(),
                    endDate = o[PollOptions.endDate]?.toKotlinLocalDate(),
                    placeId = o[PollOptions.placeId],
                    url = o[PollOptions.url],
                    imageUrl = o[PollOptions.imageUrl],
                    priceMinor = o[PollOptions.priceMinor],
                    priceUnit = o[PollOptions.priceUnit],
                    minMinor = o[PollOptions.minMinor],
                    maxMinor = o[PollOptions.maxMinor],
                    version = o[PollOptions.version],
                    voteCount = voters.size,
                    // nobody sees who chose what in an anonymous poll, the creator and the owner included
                    voterIds = if (anonymous) emptyList() else voters.map(UUID::toString),
                )
            },
            myVotes = votes.filter { it.second == me }.map { it.first.toString() },
            votedMemberIds = votes.map { it.second.toString() }.distinct(),
            // members without an account vote on the guest page
            memberCount = TripMembers.selectAll()
                .where { (TripMembers.tripId eq row[Polls.tripId]) and TripMembers.active() }.count().toInt(),
        )
    }

    // ── helpers: status ─────────────────────────────────────────────────────

    /** Turns OPEN polls whose time is up into CLOSED or TIED; for one poll, a trip, or (the job) all of them. */
    private fun settleDue(tripId: UUID? = null, pollId: UUID? = null, now: Instant = Instant.now()) {
        Polls.selectAll()
            .where {
                var cond = (Polls.status eq PollStatus.OPEN) and Polls.deletedAt.isNull() and (Polls.closesAt lessEq now)
                if (tripId != null) cond = cond and (Polls.tripId eq tripId)
                if (pollId != null) cond = cond and (Polls.id eq pollId)
                cond
            }
            .toList()
            .forEach { settle(it, now) }
    }

    private fun settle(row: ResultRow, now: Instant) {
        val pollId = row[Polls.id]
        val outcome = pollOutcome(counts(row))
        if (outcome is PollOutcome.Tie) {
            val changed = Polls.update({ (Polls.id eq pollId) and (Polls.status eq PollStatus.OPEN) }) {
                it[status] = PollStatus.TIED
                it[tiedAt] = now
            }
            if (changed == 1) {
                creatorUser(row)?.let { creator ->
                    tell(NotificationType.POLL_RESULT, row, "poll-tied:$pollId:${now.toEpochMilli()}", listOf(creator)) {
                        mapOf("stage" to "TIED", "tiedCount" to outcome.optionIds.size.toString())
                    }
                }
            }
        } else {
            Polls.update({ (Polls.id eq pollId) and (Polls.status eq PollStatus.OPEN) }) { it[status] = PollStatus.CLOSED }
        }
    }

    private fun reopenRow(row: ResultRow, closesAt: Instant, actor: String?) {
        val pollId = row[Polls.id]
        val changed = Polls.update({ (Polls.id eq pollId) and (Polls.status inList listOf(PollStatus.CLOSED, PollStatus.TIED)) }) {
            it[status] = PollStatus.OPEN
            it[Polls.closesAt] = closesAt
            it[tiedAt] = null
        }
        if (changed == 1) {
            tell(NotificationType.POLL_REQUESTED, row, "poll-reopen:$pollId:${closesAt.toEpochMilli()}", activeMemberUserIds(row[Polls.tripId])) {
                buildMap {
                    put("stage", "REOPENED")
                    put("closesAt", closesAt.toTimestamp().toString())
                    actor?.let { put("actorName", it) }
                }
            }
        }
    }

    /** Members with an account who have not voted, one day before the poll closes. */
    private fun remind(row: ResultRow) {
        val voted = validVotes(row, activeOptions(row[Polls.id])).map { it.second }.toSet()
        val waiting = TripMembers.selectAll()
            .where { (TripMembers.tripId eq row[Polls.tripId]) and TripMembers.active() and TripMembers.userId.isNotNull() }
            .filter { it[TripMembers.id] !in voted }
            .mapNotNull { it[TripMembers.userId] }
        tell(NotificationType.POLL_REQUESTED, row, "poll-closing:${row[Polls.id]}:${row[Polls.closesAt].toEpochMilli()}", waiting) {
            mapOf("stage" to "CLOSING", "closesAt" to row[Polls.closesAt].toTimestamp().toString())
        }
    }

    /** The option was edited or removed: its votes are gone and those voters are asked to choose again. */
    private fun clearVotes(row: ResultRow, optionId: UUID, label: String, newVersion: Int) {
        val voters = PollVotes.selectAll().where { PollVotes.optionId eq optionId }.map { it[PollVotes.memberId] }
        PollVotes.deleteWhere { PollVotes.optionId eq optionId }
        val users = userIdsOfMembers(voters).values
        if (users.isNotEmpty()) {
            tell(NotificationType.POLL_REQUESTED, row, "poll-reset:$optionId:$newVersion", users.toList()) {
                mapOf("stage" to "RESET", "optionLabel" to label)
            }
        }
    }

    // ── helpers: the winner's action ────────────────────────────────────────

    private fun applyAction(poll: ResultRow, option: ResultRow) {
        val tripId = poll[Polls.tripId]
        when (actionFor(poll[Polls.kind])) {
            PollAction.SET_TRIP_DATES -> {
                val start = option[PollOptions.startDate] ?: return
                val end = option[PollOptions.endDate] ?: return
                Trips.update({ Trips.id eq tripId }) {
                    it[startDate] = start
                    it[endDate] = end
                }
                TripService.unscheduleOutsideRange(tripId, start.toKotlinLocalDate(), end.toKotlinLocalDate())
            }
            PollAction.SET_DESTINATION -> Trips.update({ Trips.id eq tripId }) { it[destination] = option[PollOptions.label] }
            PollAction.ADD_UNSCHEDULED_PLACE -> addItem(tripId, option, Category.ATTRACTION, day = null, time = null)
            PollAction.ADD_ACTIVITY -> addItem(tripId, option, Category.FOOD, poll[Polls.dayDate], poll[Polls.startTime])
            PollAction.ADD_ESTIMATE -> addEstimate(poll, option)
            PollAction.NONE -> Unit
        }
    }

    /**
     * STAY: an estimated lodging expense (price × nights by default), paid by the poll creator and split equally between
     * the members, left out of balances until someone records the real payment. An option without a price adds nothing.
     */
    private fun addEstimate(poll: ResultRow, option: ResultRow) {
        val price = option[PollOptions.priceMinor]?.takeIf { it > 0 } ?: return
        val tripId = poll[Polls.tripId]
        val trip = Trips.selectAll().where { Trips.id eq tripId }.single()
        val start = trip[Trips.startDate]
        val nights = java.time.temporal.ChronoUnit.DAYS.between(start, trip[Trips.endDate]).toInt()
        val members = TripMembers.select(TripMembers.id)
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt)
            .map { it[TripMembers.id].toString() }
        val amount = stayEstimateMinor(price, option[PollOptions.priceUnit], nights, members.size)
        val payer = poll[Polls.createdByMemberId]
        val expenseId = UUID.randomUUID()
        Expenses.insert {
            it[id] = expenseId
            it[Expenses.tripId] = tripId
            it[amountMinor] = amount
            it[currency] = trip[Trips.baseCurrency]
            it[category] = Category.LODGING
            it[paymentMethod] = PaymentMethod.TRANSFER
            it[note] = option[PollOptions.label]
            it[spentAt] = start.atStartOfDay(java.time.ZoneId.of("Asia/Jakarta")).toInstant()
            it[paidByMemberId] = payer
            it[paidFromKitty] = false
            it[isEstimate] = true
        }
        val shares = splitEqually(amount, members.ifEmpty { listOf(payer.toString()) }, payer.toString())
        ExpenseService.replaceSplits(expenseId, shares.map { ExpenseSplitDto(it.key, it.value) })
        // the booking page goes to "Dokumen trip" (Menginap)
        option[PollOptions.url]?.let { url -> DocumentService.addPollLink(tripId, payer, option[PollOptions.label], url) }
    }

    private fun addItem(tripId: UUID, option: ResultRow, category: Category, day: java.time.LocalDate?, time: java.time.LocalTime?) {
        val maxOrder = ItineraryItems.sortOrder.max()
        val last = ItineraryItems.select(maxOrder).where { ItineraryItems.tripId eq tripId }.singleOrNull()?.get(maxOrder) ?: 0.0
        ItineraryItems.insert {
            it[id] = UUID.randomUUID()
            it[ItineraryItems.tripId] = tripId
            it[title] = option[PollOptions.label]
            it[ItineraryItems.category] = category
            it[placeName] = option[PollOptions.label]
            it[placeId] = option[PollOptions.placeId]
            it[bookingUrl] = option[PollOptions.url]
            it[itemSource] = ItemSource.MANUAL
            it[sortOrder] = last + 1.0
            if (day != null && time != null) {
                it[dayDate] = day
                it[startTime] = time
                it[durationMin] = FOOD_MINUTES
                it[endTime] = time.plusMinutes(FOOD_MINUTES.toLong())
            }
        }
    }

    // ── helpers: notifications ──────────────────────────────────────────────

    private fun tell(type: NotificationType, poll: ResultRow, key: String, recipients: List<UUID>, extra: () -> Map<String, String>) {
        if (recipients.isEmpty()) return
        val title = Trips.selectAll().where { Trips.id eq poll[Polls.tripId] }.singleOrNull()?.get(Trips.title).orEmpty()
        val params = mapOf(
            "tripTitle" to title, "pollId" to poll[Polls.id].toString(), "pollKind" to poll[Polls.kind].name,
            "question" to poll[Polls.question],
        ) + extra()
        Notify.emit { notify(recipients, type, poll[Polls.tripId], key) { params } }
    }

    private fun creatorUser(poll: ResultRow): UUID? = userIdsOfMembers(listOf(poll[Polls.createdByMemberId])).values.singleOrNull()

    private fun nameOf(userId: UUID): String =
        app.ransel.server.db.Users.selectAll().where { app.ransel.server.db.Users.id eq userId }.singleOrNull()?.get(app.ransel.server.db.Users.displayName).orEmpty()

    private const val FOOD_MINUTES = 60
}
