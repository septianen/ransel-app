package app.ransel.server.ai

import app.ransel.server.db.BuddyInterest
import app.ransel.server.db.InspirationRules
import app.ransel.server.db.InspirationSessionLog
import app.ransel.server.db.InspirationSessions
import app.ransel.server.db.SavedIdeas
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.server.service.CreditService
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.DEFAULT_INSPIRATION_RULES
import app.ransel.shared.domain.InspirationFacts
import app.ransel.shared.domain.InspirationLimits
import app.ransel.shared.domain.InspirationRule
import app.ransel.shared.domain.canAskAnotherInspirationQuestion
import app.ransel.shared.domain.canFinishInspirationEarly
import app.ransel.shared.domain.checkCombinationOptions
import app.ransel.shared.domain.fallbackInspirationQuestion
import app.ransel.shared.domain.inspirationDistanceFromOptionId
import app.ransel.shared.domain.inspirationFreeRefinesLeft
import app.ransel.shared.domain.inspirationInterestLabel
import app.ransel.shared.domain.inspirationRefineCharge
import app.ransel.shared.domain.needsInspirationVisaNote
import app.ransel.shared.domain.nextInspirationSlot
import app.ransel.shared.domain.pendingInspirationQuestions
import app.ransel.shared.domain.skipsCombination
import app.ransel.shared.domain.validateInspirationAnswer
import app.ransel.shared.domain.validateStartInspiration
import app.ransel.shared.dto.BuddyInterestRequest
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.InspirationAnswerRequest
import app.ransel.shared.dto.InspirationCardDto
import app.ransel.shared.dto.InspirationChipDto
import app.ransel.shared.dto.InspirationCostRangeDto
import app.ransel.shared.dto.InspirationEditChipRequest
import app.ransel.shared.dto.InspirationOptionDto
import app.ransel.shared.dto.InspirationQuestionDto
import app.ransel.shared.dto.InspirationResultDto
import app.ransel.shared.dto.InspirationSessionDto
import app.ransel.shared.dto.SaveIdeaRequest
import app.ransel.shared.dto.SaveIdeaResponse
import app.ransel.shared.dto.SavedIdeaDto
import app.ransel.shared.dto.SavedIdeasResponse
import app.ransel.shared.dto.StartInspirationRequest
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.InspirationChipKey
import app.ransel.shared.model.InspirationDistance
import app.ransel.shared.model.InspirationQuestionSource
import app.ransel.shared.model.InspirationRefine
import app.ransel.shared.model.InspirationRequirement
import app.ransel.shared.model.InspirationSlot
import app.ransel.shared.model.InspirationStatus
import app.ransel.shared.model.Interest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/**
 * Explore Inspirasi (PRD v0.8): an AI interview that ends in activity type + region cards, charged once per session and
 * only when the cards are valid; "Tanya lagi" and chip edits are free a few times; saved ideas live per account.
 *
 * The server decides every rule; the LLM only writes questions and cards. Mandatory questions (level and buddy for a
 * skill activity, budget for shopping) are enforced here from the `inspiration_rules` table, never left to the prompt:
 * the spike had a freediving session without them (21 Nov 2026). LLM calls run outside database transactions; a
 * per-session lock keeps a double tap from building cards twice, and the ledger key keeps it from charging twice.
 */
class InspirationService(
    private val ai: AiServices,
    private val credits: CreditService,
    private val photoSigner: PhotoSigner,
    /** `AiBudget.check`: 503 AI_PAUSED before any AI work once Karung's monthly budget is spent. */
    private val budget: () -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    private val log = LoggerFactory.getLogger(InspirationService::class.java)
    private val sessionLocks = Array(64) { Mutex() }
    private val userLocks = Array(64) { Mutex() }
    private fun lockFor(locks: Array<Mutex>, id: UUID) = locks[(id.hashCode() % locks.size).absoluteValue]

    /** Photo place per "region|activity"; kept small, the photo itself is cached by the photo proxy. */
    private val photoCache = ConcurrentHashMap<String, String>()
    private val noPhoto = ConcurrentHashMap.newKeySet<String>()

    // ── sessions ────────────────────────────────────────────────────────────

    suspend fun start(userId: UUID, body: StartInspirationRequest, language: String): InspirationSessionDto {
        val id = parseId(body.id, "id")
        val interests = body.interests.distinct()
        val text = body.text?.trim()?.takeIf { it.isNotEmpty() }
        requireValid(
            validateStartInspiration(interests, text) +
                (if (interests.isEmpty() && text == null) mapOf("interests" to "REQUIRED") else emptyMap()),
        )
        return lockFor(userLocks, userId).withLock {
            val existing = transaction { InspirationSessions.selectAll().where { InspirationSessions.id eq id }.singleOrNull() }
            if (existing != null) {
                if (existing[InspirationSessions.userId] != userId) throw conflict("Session id already used")
                return@withLock dto(existing.toSession())
            }
            requireUnderSessionLimit(userId)
            credits.requireAffordable(userId, CreditAction.INSPIRATION)
            budget()

            val done = buildSet {
                add(InspirationSlot.INTEREST)
                if (skipsCombination(interests)) add(InspirationSlot.COMBINATION)
            }
            var state = InspirationState(interests = interests, text = text?.let(::scrub), done = done)
            val started = now()
            transaction {
                InspirationSessions.insert {
                    it[InspirationSessions.id] = id
                    it[InspirationSessions.userId] = userId
                    it[status] = InspirationStatus.ACTIVE
                    it[InspirationSessions.language] = language
                    it[InspirationSessions.state] = state
                    it[askAgainUsed] = 0
                    it[chipEditsUsed] = 0
                    it[createdAt] = started
                    it[updatedAt] = started
                    it[expiresAt] = started.plus(Duration.ofHours(InspirationLimits.SESSION_RESUME_HOURS.toLong()))
                }
            }
            state = try {
                advance(userId, id, state, language)
            } catch (e: Exception) {
                save(id, InspirationStatus.FAILED, state)
                throw e
            }
            save(id, if (state.current == null) InspirationStatus.READY else InspirationStatus.ACTIVE, state)
            dto(load(userId, id))
        }
    }

    fun get(userId: UUID, rawId: String): InspirationSessionDto = dto(load(userId, parseId(rawId, "sessionId")))

    suspend fun answer(userId: UUID, rawId: String, body: InspirationAnswerRequest): InspirationSessionDto {
        val id = parseId(rawId, "sessionId")
        requireValid(validateInspirationAnswer(body.optionId, body.otherText))
        return lockFor(sessionLocks, id).withLock {
            val session = load(userId, id)
            val question = session.state.current
            if (session.status != InspirationStatus.ACTIVE || question == null) throw conflict("No question is waiting")
            val option = body.optionId?.takeIf { it.isNotBlank() }?.let { optionId ->
                question.options.firstOrNull { it.id == optionId } ?: throw validationFailed(mapOf("optionId" to "INVALID"))
            }
            val answerText = option?.title ?: scrub(body.otherText.orEmpty().trim())
            val before = session.state
            var state = record(before, question, option, answerText)
            if (before.mode == InspirationMode.INTERVIEW) {
                state = state.copy(history = (before.history + before.copy(history = emptyList())).takeLast(MAX_HISTORY))
            }
            logChoice(id, state.stepsAsked, answerText)

            when (state.mode) {
                InspirationMode.INTERVIEW -> {
                    state = advance(userId, id, state, session.language)
                    save(id, if (state.current == null) InspirationStatus.READY else InspirationStatus.ACTIVE, state)
                    dto(load(userId, id))
                }
                InspirationMode.ASK_AGAIN -> {
                    val used = session.askAgainUsed
                    val action = inspirationRefineCharge(InspirationRefine.ASK_AGAIN, used)
                    if (action != null) credits.requireAffordable(userId, action)
                    val old = session.result ?: throw conflict("No cards yet")
                    val built = buildCards(userId, id, state, session.language, count = 3, avoid = regions(old), kind = "ASK_AGAIN")
                    val merged = old.copy(
                        result = old.result.copy(cards = old.result.cards + built.cards.map { it.card }),
                        cards = old.cards + built.cards,
                    ).renumbered()
                    val paid = action?.let { credits.charge(userId, it, ref = id.toString(), idempotencyKey = "inspiration:$id:ask:${used + 1}") }
                    state = state.copy(mode = InspirationMode.INTERVIEW, current = null)
                    saveResult(id, InspirationStatus.CHARGED, state, merged, askAgainUsed = used + 1)
                    dto(load(userId, id), paid ?: credits.credits(userId))
                }
                InspirationMode.CHIP_EDIT -> {
                    val pending = pending(state)
                    if (pending.isNotEmpty()) {
                        state = advance(userId, id, state, session.language, mandatoryOnly = true)
                        save(id, InspirationStatus.ACTIVE, state)
                        dto(load(userId, id))
                    } else {
                        rebuildAfterEdit(userId, session, state)
                    }
                }
            }
        }
    }

    /**
     * "Back" in the interview: the last answer goes and the question before it is shown again, as it was. Only while the
     * interview runs (ACTIVE or READY, never once the cards are paid for) and not on the first question (409 CONFLICT).
     */
    suspend fun back(userId: UUID, rawId: String): InspirationSessionDto {
        val id = parseId(rawId, "sessionId")
        return lockFor(sessionLocks, id).withLock {
            val session = load(userId, id)
            val state = session.state
            val open = session.status == InspirationStatus.ACTIVE || session.status == InspirationStatus.READY
            if (!open || state.mode != InspirationMode.INTERVIEW || state.history.isEmpty()) throw conflict("There is no earlier question")
            val previous = state.history.last().copy(history = state.history.dropLast(1))
            save(id, InspirationStatus.ACTIVE, previous)
            dto(load(userId, id))
        }
    }

    suspend fun finish(userId: UUID, rawId: String): InspirationSessionDto {
        val id = parseId(rawId, "sessionId")
        return lockFor(sessionLocks, id).withLock {
            val session = load(userId, id)
            when (session.status) {
                InspirationStatus.CHARGED -> return@withLock dto(session)
                InspirationStatus.ACTIVE -> {
                    if (session.state.mode != InspirationMode.INTERVIEW) throw conflict("A question is waiting")
                    if (pending(session.state).isNotEmpty()) throw validationFailed(mapOf("session" to "MANDATORY_PENDING"))
                    if (session.state.stepsAsked < InspirationLimits.FINISH_EARLY_FROM_STEP) throw validationFailed(mapOf("session" to "TOO_EARLY"))
                }
                InspirationStatus.READY -> Unit
                InspirationStatus.FAILED, InspirationStatus.EXPIRED -> throw notFound("Session")
            }
            // no affordability check here: the cards are built first and the charge checks the balance, so a 402 keeps
            // them and a retry after a top-up needs no second AI call (PRD "Credit")
            val state = session.state.copy(current = null)
            val stored = session.result ?: run {
                val built = buildCards(userId, id, state, session.language, count = 5, avoid = emptyList(), kind = "CARDS")
                StoredInspirationResult(
                    result = InspirationResultDto(
                        profile = profile(built.profile, state, session.language),
                        cards = built.cards.map { it.card },
                        surprise = built.surprise?.card,
                        showFindBuddy = state.noBuddy,
                        createdAt = now().toTimestamp(),
                    ),
                    cards = built.cards,
                    surprise = built.surprise,
                ).renumbered()
            }
            // the cards are kept before paying: a 402 here keeps the session READY and a retry pays without a new AI call
            saveResult(id, InspirationStatus.READY, state, stored)
            val paid = credits.charge(userId, CreditAction.INSPIRATION, ref = id.toString(), idempotencyKey = "inspiration:$id")
            transaction {
                InspirationSessions.update({ InspirationSessions.id eq id }) {
                    it[status] = InspirationStatus.CHARGED
                    it[chargedAt] = now()
                    it[updatedAt] = now()
                }
                InspirationSessionLog.update({ (InspirationSessionLog.sessionId eq id) and (InspirationSessionLog.kind eq "CARDS") }) {
                    it[creditsCharged] = credits.cost(CreditAction.INSPIRATION)
                }
            }
            dto(load(userId, id), paid)
        }
    }

    suspend fun askAgain(userId: UUID, rawId: String): InspirationSessionDto {
        val id = parseId(rawId, "sessionId")
        return lockFor(sessionLocks, id).withLock {
            val session = load(userId, id)
            if (session.status != InspirationStatus.CHARGED) throw conflict("The session has no cards yet")
            inspirationRefineCharge(InspirationRefine.ASK_AGAIN, session.askAgainUsed)?.let { credits.requireAffordable(userId, it) }
            budget()
            val state = askQuestion(
                userId, id, session.state.copy(mode = InspirationMode.ASK_AGAIN), session.language,
                slot = InspirationSlot.OPTIONAL, requirement = null, askAgain = true,
            ) ?: ask(session.state.copy(mode = InspirationMode.ASK_AGAIN), askAgainFallback(session.language), null)
            save(id, InspirationStatus.ACTIVE, state)
            dto(load(userId, id))
        }
    }

    suspend fun editChip(userId: UUID, rawId: String, body: InspirationEditChipRequest): InspirationSessionDto {
        val id = parseId(rawId, "sessionId")
        val value = scrub(body.value.trim())
        if (value.isEmpty()) throw validationFailed(mapOf("value" to "REQUIRED"))
        if (value.length > InspirationLimits.OTHER_TEXT_MAX) throw validationFailed(mapOf("value" to "TOO_LONG"))
        return lockFor(sessionLocks, id).withLock {
            val session = load(userId, id)
            if (session.status != InspirationStatus.CHARGED) throw conflict("The session has no cards yet")
            inspirationRefineCharge(InspirationRefine.CHIP_EDIT, session.chipEditsUsed)?.let { credits.requireAffordable(userId, it) }
            var state = applyChip(session.state, body.key, value).copy(mode = InspirationMode.CHIP_EDIT)
            if (pending(state).isNotEmpty()) {
                // a chip that switches a mandatory rule on asks its question first (PRD "Output")
                state = advance(userId, id, state, session.language, mandatoryOnly = true)
                save(id, InspirationStatus.ACTIVE, state)
                dto(load(userId, id))
            } else {
                rebuildAfterEdit(userId, session, state)
            }
        }
    }

    // ── saved ideas ─────────────────────────────────────────────────────────

    fun savedIdeas(userId: UUID): SavedIdeasResponse = transaction {
        SavedIdeasResponse(
            ideas = SavedIdeas.selectAll().where { (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }
                .orderBy(SavedIdeas.savedAt to SortOrder.DESC).map { it.toIdeaDto() },
            max = InspirationLimits.SAVED_MAX,
        )
    }

    fun save(userId: UUID, body: SaveIdeaRequest): SaveIdeaResponse {
        val id = parseId(body.id, "id")
        val sessionId = parseId(body.sessionId, "sessionId")
        val session = transaction {
            InspirationSessions.selectAll().where { (InspirationSessions.id eq sessionId) and (InspirationSessions.userId eq userId) }.singleOrNull()
        }?.toSession() ?: throw notFound("Session")
        val result = session.result?.takeIf { session.chargedAt != null } ?: throw notFound("Session")
        val card = (result.cards + listOfNotNull(result.surprise)).firstOrNull { it.card.id == body.cardId } ?: throw notFound("Card")
        return transaction {
            SavedIdeas.selectAll().where { (SavedIdeas.id eq id) and (SavedIdeas.userId eq userId) }.singleOrNull()?.let {
                return@transaction SaveIdeaResponse(it.toIdeaDto())
            }
            // every live idea of this user is locked: two parallel saves cannot both see 29
            val live = SavedIdeas.selectAll().where { (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }
                .orderBy(SavedIdeas.savedAt to SortOrder.ASC, SavedIdeas.id to SortOrder.ASC).forUpdate().toList()
            live.firstOrNull { it[SavedIdeas.sessionId] == sessionId && it[SavedIdeas.cardId] == body.cardId }?.let {
                return@transaction SaveIdeaResponse(it.toIdeaDto())
            }
            val oldest = live.firstOrNull()?.takeIf { live.size >= InspirationLimits.SAVED_MAX }
            val at = now()
            if (oldest != null) {
                SavedIdeas.update({ SavedIdeas.id eq oldest[SavedIdeas.id] }) {
                    it[removedAt] = at
                    it[removedBy] = id
                }
            }
            SavedIdeas.insert {
                it[SavedIdeas.id] = id
                it[SavedIdeas.userId] = userId
                it[SavedIdeas.sessionId] = sessionId
                it[cardId] = body.cardId
                it[SavedIdeas.card] = card
                it[profile] = result.result.profile
                it[language] = session.language
                it[savedAt] = at
                it[refreshedAt] = result.result.createdAt.toJavaInstantCompat()
            }
            SaveIdeaResponse(
                idea = SavedIdeas.selectAll().where { SavedIdeas.id eq id }.single().toIdeaDto(),
                removedOldestId = oldest?.get(SavedIdeas.id)?.toString(),
            )
        }
    }

    /** "Urungkan" of the save that pushed the oldest idea out: this save goes, the oldest comes back. */
    fun undoSave(userId: UUID, rawId: String) {
        val id = parseId(rawId, "id")
        transaction {
            val deleted = SavedIdeas.deleteWhere { (SavedIdeas.id eq id) and (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }
            if (deleted == 0) throw notFound("Saved idea")
            SavedIdeas.update({ (SavedIdeas.userId eq userId) and (SavedIdeas.removedBy eq id) }) {
                it[removedAt] = null
                it[removedBy] = null
            }
        }
    }

    /** Removes an idea from the list; it stays restorable for 24 h ("Urungkan"), then the purge deletes it. */
    fun delete(userId: UUID, rawId: String) {
        val id = parseId(rawId, "id")
        val removed = transaction {
            SavedIdeas.update({ (SavedIdeas.id eq id) and (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }) {
                it[removedAt] = now()
                it[removedBy] = null
            }
        }
        if (removed == 0) throw notFound("Saved idea")
    }

    /**
     * "Urungkan" after a removal: the idea comes back in its old place (its `saved_at` is kept). 404 once it is gone
     * (24 h) or when it was pushed out by a 31st save (that one has its own undo); 409 when the list is full again or the
     * same card was saved since.
     */
    fun restore(userId: UUID, rawId: String) {
        val id = parseId(rawId, "id")
        transaction {
            val row = SavedIdeas.selectAll().where {
                (SavedIdeas.id eq id) and (SavedIdeas.userId eq userId) and SavedIdeas.removedBy.isNull() and
                    (SavedIdeas.removedAt greater now().minus(Duration.ofDays(1)))
            }.forUpdate().singleOrNull() ?: throw notFound("Saved idea")
            val live = SavedIdeas.selectAll().where { (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }.forUpdate().toList()
            val sameCard = live.any { it[SavedIdeas.sessionId] == row[SavedIdeas.sessionId] && it[SavedIdeas.cardId] == row[SavedIdeas.cardId] }
            if (live.size >= InspirationLimits.SAVED_MAX || sameCard) throw conflict("The idea cannot come back: the list is full or it was saved again")
            SavedIdeas.update({ SavedIdeas.id eq id }) { it[removedAt] = null }
        }
    }

    /** "Perbarui": free, once per card per [hours]; the attempt counts even if the AI fails, but a failure gives it back. */
    suspend fun refresh(userId: UUID, rawId: String): SavedIdeaDto {
        val id = parseId(rawId, "id")
        val hours = credits.config.inspirationRefreshHours.toLong()
        return lockFor(sessionLocks, id).withLock {
            val row = transaction {
                SavedIdeas.selectAll().where { (SavedIdeas.id eq id) and (SavedIdeas.userId eq userId) and SavedIdeas.removedAt.isNull() }.singleOrNull()
            } ?: throw notFound("Saved idea")
            val previous = row[SavedIdeas.refreshRequestedAt]
            val availableAt = previous?.plus(Duration.ofHours(hours))
            if (availableAt != null && now().isBefore(availableAt)) {
                throw rateLimited("This idea was refreshed less than $hours hours ago", Duration.between(now(), availableAt).seconds)
            }
            budget()
            transaction { SavedIdeas.update({ SavedIdeas.id eq id }) { it[refreshRequestedAt] = now() } }
            try {
                val stored = row[SavedIdeas.card]
                val language = row[SavedIdeas.language]
                // the refreshed card keeps its safety line and its visa note
                val state = InspirationState(
                    interests = emptyList(),
                    skillActivity = stored.card.safetyLine != null,
                    distance = if (stored.card.visaNote != null) InspirationDistance.ABROAD else null,
                )
                val built = buildCards(
                    userId, row[SavedIdeas.sessionId], state, language, count = 1, avoid = emptyList(), kind = "REFRESH",
                    refresh = stored.card, minCards = 1,
                )
                val fresh = built.cards.first()
                val card = fresh.copy(card = fresh.card.copy(id = stored.card.id, surprise = stored.card.surprise))
                transaction {
                    SavedIdeas.update({ SavedIdeas.id eq id }) {
                        it[SavedIdeas.card] = card
                        it[refreshedAt] = now()
                    }
                    SavedIdeas.selectAll().where { SavedIdeas.id eq id }.single().toIdeaDto()
                }
            } catch (e: Exception) {
                transaction { SavedIdeas.update({ SavedIdeas.id eq id }) { it[refreshRequestedAt] = previous } }
                throw e
            }
        }
    }

    fun buddyInterest(userId: UUID, body: BuddyInterestRequest) {
        val sessionId = body.sessionId?.let { parseId(it, "sessionId") }
        transaction {
            BuddyInterest.insert {
                it[id] = UUID.randomUUID()
                it[BuddyInterest.userId] = userId
                it[BuddyInterest.sessionId] = sessionId
                it[interest] = body.interest
                it[consentAt] = now()
                it[createdAt] = now()
            }
        }
    }

    /**
     * Sessions and their log after 90 days; "Kabari saya" rows after 90 days (the consent line promises it); ideas pushed
     * out by a 31st save once their undo is long gone (1 day).
     */
    fun purge() = transaction {
        val logCutoff = now().minus(Duration.ofDays(InspirationLimits.LOG_RETENTION_DAYS.toLong()))
        InspirationSessionLog.deleteWhere { InspirationSessionLog.createdAt less logCutoff }
        InspirationSessions.deleteWhere { InspirationSessions.createdAt less logCutoff }
        BuddyInterest.deleteWhere { BuddyInterest.createdAt less logCutoff }
        val removedCutoff = now().minus(Duration.ofDays(1))
        SavedIdeas.deleteWhere { SavedIdeas.removedAt less removedCutoff }
    }

    // ── interview engine ────────────────────────────────────────────────────

    private fun rules(): List<InspirationRule> = transaction {
        InspirationRules.selectAll().where { InspirationRules.active eq true }.toList()
    }.groupBy { it[InspirationRules.trigger] }
        .map { (trigger, rows) -> InspirationRule(trigger, rows.map { it[InspirationRules.requirement] }) }
        .ifEmpty { DEFAULT_INSPIRATION_RULES }

    private fun facts(state: InspirationState) = InspirationFacts(state.interests, state.skillActivity, state.distance)

    private fun pending(state: InspirationState, rules: List<InspirationRule> = rules()) =
        pendingInspirationQuestions(rules, facts(state), state.answeredRequirements)

    /**
     * Puts the next question on [state], or none when the interview is complete. Mandatory questions come right after the
     * activity detail (or as soon as no other slot is left) and are asked even past the 6-step cap; "Dalam kota" asks
     * "Kota mana?" next. A slot whose AI call
     * fails or is slow gets its pre-written fallback; DETAIL and OPTIONAL have none and are skipped.
     */
    private suspend fun advance(
        userId: UUID, sessionId: UUID, start: InspirationState, language: String, mandatoryOnly: Boolean = false,
    ): InspirationState {
        var state = start.copy(current = null)
        val rules = rules()
        repeat(MAX_ADVANCE_ROUNDS) {
            val slot = nextInspirationSlot(state.done)
            val capped = !canAskAnotherInspirationQuestion(state.stepsAsked)
            // mandatory questions follow the activity detail (level and budget belong there), or come now when no slot is left
            val requirement = pending(state, rules).firstOrNull()?.takeIf {
                mandatoryOnly || InspirationSlot.DETAIL in state.done || slot == null || capped || slot.ordinal > InspirationSlot.DETAIL.ordinal
            }
            val cityDue = state.distance == InspirationDistance.SAME_CITY && state.city == null && !state.cityAsked
            when {
                cityDue && !mandatoryOnly -> return ask(state.copy(cityAsked = true), cityQuestion(language), null)
                requirement == InspirationRequirement.BUDDY -> return ask(state, buddyQuestion(language), requirement)
                requirement == InspirationRequirement.BUDGET -> return ask(state, budgetQuestion(language), requirement)
                requirement == InspirationRequirement.LEVEL -> {
                    val asked = askQuestion(userId, sessionId, state, language, InspirationSlot.DETAIL, requirement)
                        ?: return ask(state, levelQuestion(language), requirement)
                    if (asked.current != null) return asked
                    state = asked // the text already gave the level ("freediving pemula")
                    return@repeat
                }
            }
            if (mandatoryOnly || capped || slot == null) return state
            if (slot == InspirationSlot.DISTANCE && state.text != null && !state.textRead) {
                // "street photography di Bangkok" already says how far: let the AI read the text before the fixed question
                state = (readFacts(userId, sessionId, state, language) ?: state).copy(textRead = true)
                return@repeat
            }
            if (slot == InspirationSlot.DISTANCE) {
                // fixed options: the answer must map to InspirationDistance, so no AI call is needed
                val q = fallbackInspirationQuestion(slot, state.interests, state.distance, language)!!
                return ask(state, q, null)
            }
            val before = state
            val asked = askQuestion(userId, sessionId, state, language, slot, null)
            if (asked != null) {
                if (asked.current != null) return asked
                state = asked // the model said the slot was already answered: go on with what it read
                if (state.done == before.done && state.answeredRequirements == before.answeredRequirements) state = state.copy(done = state.done + slot)
                return@repeat
            }
            val fallback = fallbackInspirationQuestion(slot, state.interests, state.distance, language)
            if (fallback != null) return ask(state, fallback, null)
            state = state.copy(done = state.done + slot)
        }
        return state
    }

    /** Only what the AI reads from the hero text and the answers so far (no question is shown). Null = the call failed. */
    private suspend fun readFacts(userId: UUID, sessionId: UUID, state: InspirationState, language: String): InspirationState? =
        askQuestion(userId, sessionId, state, language, InspirationSlot.DISTANCE, null, factsOnly = true)

    /**
     * One AI question. Null = the call failed, timed out, or gave fewer than 3 usable options (the caller falls back).
     * A state without `current` = the model reported the slot as already answered (its facts are applied).
     */
    private suspend fun askQuestion(
        userId: UUID, sessionId: UUID, state: InspirationState, language: String,
        slot: InspirationSlot, requirement: InspirationRequirement?, askAgain: Boolean = false, factsOnly: Boolean = false,
    ): InspirationState? {
        val started = System.nanoTime()
        val input = InspirationQuestionInput(
            language = language, interests = state.interests, text = state.text, answers = state.answers, slot = slot,
            requirement = requirement, distance = state.distance, city = state.city, skillActivity = state.skillActivity,
            askAgain = askAgain,
        )
        val answer = try {
            withTimeout(InspirationLimits.STEP_TIMEOUT_MS.toLong()) { ai.llm.inspirationQuestion(input) }
        } catch (e: ApiException) {
            throw e
        } catch (e: UnsupportedOperationException) {
            throw unavailable()
        } catch (e: Exception) {
            log.warn("inspiration question failed ({}): {}", slot, e.javaClass.simpleName)
            writeLog(sessionId, userId, state.stepsAsked + 1, "QUESTION", slot, InspirationQuestionSource.FALLBACK, "FALLBACK", elapsed(started), null)
            return null
        }
        var next = applyFacts(state, answer, slot, requirement, askAgain)
        if (factsOnly) {
            writeLog(sessionId, userId, state.stepsAsked, "QUESTION", slot, InspirationQuestionSource.LLM, "OK", elapsed(started), null)
            return next.copy(current = null)
        }
        val alreadyAnswered = !askAgain && when (requirement) {
            null -> slot in next.done
            else -> requirement in next.answeredRequirements
        }
        if (alreadyAnswered) return next.copy(current = null)
        val options = cleanOptions(answer.options, slot, state.interests, language)
        if (options.size < InspirationLimits.MIN_OPTIONS || answer.question.isBlank()) {
            writeLog(sessionId, userId, state.stepsAsked + 1, "QUESTION", slot, InspirationQuestionSource.FALLBACK, "FALLBACK", elapsed(started), null)
            return null
        }
        val q = InspirationQuestionDto(slot = slot, text = sentence(answer.question.trim().take(200)), options = options, source = InspirationQuestionSource.LLM)
        next = ask(next, q, requirement)
        writeLog(sessionId, userId, next.stepsAsked, "QUESTION", slot, InspirationQuestionSource.LLM, "OK", elapsed(started), q.text)
        return next
    }

    private fun applyFacts(
        state: InspirationState, answer: LlmInspirationQuestion, asked: InspirationSlot,
        requirement: InspirationRequirement?, askAgain: Boolean,
    ): InspirationState {
        if (askAgain) return state.copy(skillActivity = state.skillActivity || answer.skillActivity)
        val distance = state.distance ?: answer.distance?.let { d -> InspirationDistance.entries.firstOrNull { it.name == d.trim().uppercase() } }
        val city = state.city ?: answer.city?.trim()?.takeIf { it.isNotEmpty() && it.length <= 80 }?.let(::scrub)
        val answeredSlots = answer.answeredSlots.mapNotNull { s -> InspirationSlot.entries.firstOrNull { it.name == s.trim().uppercase() } }
            .filter { it != InspirationSlot.INTEREST && it !in state.done }
            // DISTANCE only counts when the model also says which distance; otherwise the abroad rule could never switch on
            .filter { it != InspirationSlot.DISTANCE || distance != null }
            // the slot being asked is only skipped when it is not a mandatory question
            .filter { requirement == null || it != asked }
        val requirements = answer.answeredRequirements.mapNotNull(InspirationPrompt::requirementOf)
            .filter { it == InspirationRequirement.LEVEL || it == InspirationRequirement.BUDGET }
        return state.copy(
            skillActivity = state.skillActivity || answer.skillActivity,
            distance = distance,
            city = city,
            done = state.done + answeredSlots,
            skipped = state.skipped + answeredSlots,
            answeredRequirements = state.answeredRequirements + requirements,
        )
    }

    private fun cleanOptions(raw: List<LlmInspirationOption>, slot: InspirationSlot, interests: List<Interest>, language: String): List<InspirationOptionDto> {
        val seen = mutableSetOf<String>()
        val options = raw.mapIndexedNotNull { i, o ->
            val title = sentence(o.title.trim().take(80))
            if (title.isEmpty() || !seen.add(title.lowercase())) return@mapIndexedNotNull null
            val id = o.id.trim().takeIf { it.isNotEmpty() && it.length <= 40 } ?: "o${i + 1}"
            InspirationOptionDto(
                id = id,
                title = title,
                subtitle = o.subtitle?.trim()?.take(80)?.takeIf { it.isNotEmpty() }?.let(::sentence),
                interests = o.interests.mapNotNull { n -> Interest.entries.firstOrNull { it.name == n.trim().uppercase() } }.distinct(),
            )
        }.distinctBy { it.id }
        val checked = if (slot == InspirationSlot.COMBINATION) checkCombinationOptions(options, interests, language) else options
        return checked.take(InspirationLimits.MAX_OPTIONS)
    }

    /** Shows [question] as the next step. */
    private fun ask(state: InspirationState, question: InspirationQuestionDto, requirement: InspirationRequirement?): InspirationState {
        val step = state.stepsAsked + 1
        val pendingNow = pending(state)
        val q = question.copy(
            requirement = requirement,
            stepsDone = state.answers.size,
            skippedSlots = state.skipped.distinct(),
            canFinishEarly = state.mode == InspirationMode.INTERVIEW && canFinishInspirationEarly(step, pendingNow),
        )
        return state.copy(stepsAsked = step, current = q)
    }

    private fun record(state: InspirationState, question: InspirationQuestionDto, option: InspirationOptionDto?, text: String): InspirationState {
        val requirement = question.requirement
        val isCity = question.slot == InspirationSlot.DISTANCE && option?.id?.startsWith("city:") == true ||
            question.slot == InspirationSlot.DISTANCE && state.cityAsked && state.city == null && state.distance != null
        val answer = InspirationAnswer(question.slot, requirement, question.text, text, option?.id, city = isCity)
        var next = state.copy(answers = state.answers + answer, current = null)
        when {
            requirement != null -> {
                next = next.copy(answeredRequirements = next.answeredRequirements + requirement)
                if (requirement == InspirationRequirement.BUDDY && option?.id == "buddy_no") next = next.copy(noBuddy = true)
            }
            isCity -> next = next.copy(city = text.take(80))
            question.slot == InspirationSlot.DISTANCE -> next = next.copy(
                distance = option?.id?.let(::inspirationDistanceFromOptionId) ?: InspirationDistance.ANY,
                done = next.done + InspirationSlot.DISTANCE,
            )
            state.mode == InspirationMode.INTERVIEW -> next = next.copy(done = next.done + question.slot)
        }
        return next
    }

    // ── cards ───────────────────────────────────────────────────────────────

    private class Built(val profile: List<LlmInspirationChip>, val cards: List<StoredInspirationCard>, val surprise: StoredInspirationCard?)

    /** Asks for cards, keeps the valid ones, retries once below [minCards]; 503 AI_UNAVAILABLE after that (nothing charged). */
    private suspend fun buildCards(
        userId: UUID, sessionId: UUID, state: InspirationState, language: String, count: Int, avoid: List<String>, kind: String,
        refresh: InspirationCardDto? = null, minCards: Int = InspirationLimits.MIN_OPTIONS,
    ): Built {
        val rules = rules()
        val visa = needsInspirationVisaNote(rules, facts(state))
        val input = InspirationCardsInput(
            language = language, interests = state.interests, text = state.text, answers = state.answers, distance = state.distance,
            city = state.city, skillActivity = state.skillActivity, needsVisaNote = visa, count = count, avoidRegions = avoid,
            overrides = state.overrides, refresh = refresh,
        )
        val avoidKeys = avoid.map { it.trim().lowercase() }.toSet()
        repeat(CARD_ATTEMPTS) { attempt ->
            val started = System.nanoTime()
            val answer = try {
                withTimeout(CARDS_TIMEOUT_MS) { ai.llm.inspirationCards(input) }
            } catch (e: ApiException) {
                throw e
            } catch (e: UnsupportedOperationException) {
                throw unavailable()
            } catch (e: Exception) {
                log.warn("inspiration cards failed (attempt {}): {}", attempt + 1, e.javaClass.simpleName)
                writeLog(sessionId, userId, state.stepsAsked, kind, null, null, "FAILED", elapsed(started), null)
                return@repeat
            }
            val main = answer.cards.mapNotNull { sanitize(it, state, language, visa) }
                .distinctBy { it.region.lowercase() }
                .filter { it.region.lowercase() !in avoidKeys }
                .take(if (refresh != null) 1 else InspirationLimits.MAX_OPTIONS)
            if (main.size < minCards) {
                writeLog(sessionId, userId, state.stepsAsked, kind, null, null, "FAILED", elapsed(started), null)
                return@repeat
            }
            val used = avoidKeys + main.map { it.region.lowercase() }
            val surprise = if (refresh != null) null else answer.surprise?.let { sanitize(it, state, language, visa) }
                ?.takeIf { it.region.lowercase() !in used }?.copy(surprise = true)
            val withPhotos = withPhotos(main + listOfNotNull(surprise), language)
            writeLog(sessionId, userId, state.stepsAsked, kind, null, null, "OK", elapsed(started), null)
            return Built(
                profile = answer.profile,
                cards = withPhotos.filter { !it.card.surprise },
                surprise = withPhotos.firstOrNull { it.card.surprise },
            )
        }
        throw unavailable()
    }

    private fun sanitize(c: LlmInspirationCard, state: InspirationState, language: String, visa: Boolean): InspirationCardDto? {
        val region = c.region.trim().take(80)
        // the model sometimes writes "Activity · Region" into activity; the card shows the region on its own
        val activity = sentence(app.ransel.shared.domain.ideaActivityTitle(c.activity, region).take(80))
        val why = sentence(c.why.trim().take(200))
        if (activity.isEmpty() || region.isEmpty() || why.isEmpty()) return null
        val min = c.costMinIdr?.takeIf { it > 0 }
        val max = c.costMaxIdr?.takeIf { it > 0 }
        val cost = if (min != null && max != null) InspirationCostRangeDto(minOf(min, max), maxOf(min, max), "IDR") else null
        return InspirationCardDto(
            id = "",
            activity = activity,
            region = region,
            countryCode = c.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) },
            why = why,
            access = c.access?.trim()?.take(80)?.takeIf { it.isNotEmpty() }?.let(::sentence),
            bestSeason = c.bestSeason?.trim()?.take(80)?.takeIf { it.isNotEmpty() }?.let(::sentence),
            level = c.level?.trim()?.take(60)?.takeIf { it.isNotEmpty() }?.let(::sentence),
            requirements = c.requirements.map { sentence(it.trim().take(120)) }.filter { it.isNotEmpty() }.take(6),
            costRange = cost,
            visaNote = if (visa) (c.visaNote?.trim()?.take(200)?.takeIf { it.isNotEmpty() }?.let(::sentence) ?: defaultVisaNote(language)) else null,
            safetyLine = if (state.skillActivity) safetyLine(language) else null,
            durationDays = c.durationDays?.takeIf { it in 1..60 },
        )
    }

    private suspend fun withPhotos(cards: List<InspirationCardDto>, language: String): List<StoredInspirationCard> = coroutineScope {
        cards.map { card ->
            async {
                val key = "${card.region.lowercase()}|${card.activity.lowercase()}"
                val placeId = photoCache[key] ?: if (key in noPhoto) null else runCatching {
                    ai.places.findPlaceId(card.activity, card.region)
                }.getOrNull().also { found ->
                    if (photoCache.size > PHOTO_CACHE_MAX) { photoCache.clear(); noPhoto.clear() }
                    if (found != null) photoCache[key] = found else noPhoto += key
                }
                StoredInspirationCard(
                    card.copy(photoAttribution = if (placeId != null) (if (language == "en") "Photo: Google" else "Foto: Google") else null),
                    placeId,
                )
            }
        }.awaitAll()
    }

    private suspend fun rebuildAfterEdit(userId: UUID, session: Session, edited: InspirationState): InspirationSessionDto {
        val used = session.chipEditsUsed
        val action = inspirationRefineCharge(InspirationRefine.CHIP_EDIT, used)
        if (action != null) credits.requireAffordable(userId, action)
        val state = edited.copy(mode = InspirationMode.INTERVIEW, current = null)
        val built = buildCards(userId, session.id, state, session.language, count = 5, avoid = emptyList(), kind = "CHIP_EDIT")
        val old = session.result ?: throw conflict("No cards yet")
        val stored = StoredInspirationResult(
            result = old.result.copy(
                profile = profile(built.profile, state, session.language),
                cards = built.cards.map { it.card },
                surprise = built.surprise?.card,
                showFindBuddy = state.noBuddy,
            ),
            cards = built.cards,
            surprise = built.surprise,
        ).renumbered()
        val paid = action?.let { credits.charge(userId, it, ref = session.id.toString(), idempotencyKey = "inspiration:${session.id}:edit:${used + 1}") }
        saveResult(session.id, InspirationStatus.CHARGED, state, stored, chipEditsUsed = used + 1)
        return dto(load(userId, session.id), paid ?: credits.credits(userId))
    }

    /** The profile header: the model's chips, the user's own edits on top, and always the interests. */
    private fun profile(raw: List<LlmInspirationChip>, state: InspirationState, language: String): List<InspirationChipDto> {
        val chips = linkedMapOf<InspirationChipKey, String>()
        if (state.interests.isNotEmpty()) chips[InspirationChipKey.INTEREST] = InspirationPrompt.interestLabels(state.interests, language)
        raw.forEach { chip ->
            val key = InspirationChipKey.entries.firstOrNull { it.name == chip.key.trim().uppercase() } ?: return@forEach
            val value = sentence(chip.value.trim().take(40))
            if (value.isNotEmpty() && key !in chips) chips[key] = value
        }
        state.overrides.forEach { (k, v) -> chips[k] = v }
        return chips.map { (k, v) -> InspirationChipDto(k, v) }
    }

    private fun applyChip(state: InspirationState, key: InspirationChipKey, value: String): InspirationState {
        val lower = value.lowercase()
        var next = state.copy(overrides = state.overrides + (key to value))
        when (key) {
            InspirationChipKey.DISTANCE -> {
                val distance = inspirationDistanceFromOptionId(value) ?: when {
                    "luar negeri" in lower || "abroad" in lower -> InspirationDistance.ABROAD
                    "dalam kota" in lower || "my city" in lower -> InspirationDistance.SAME_CITY
                    "luar kota" in lower || "indonesia" in lower -> InspirationDistance.DOMESTIC
                    else -> null
                }
                if (distance != null) next = next.copy(distance = distance)
            }
            InspirationChipKey.INTEREST -> {
                val found = Interest.entries.filter { i ->
                    listOf("id", "en").any { inspirationInterestLabel(i, it).lowercase() in lower } || i.name.lowercase() in lower
                }
                if (found.isNotEmpty()) next = next.copy(interests = found.take(InspirationLimits.MAX_INTERESTS))
            }
            else -> Unit
        }
        return next
    }

    // ── fixed questions (server copy, Accept-Language) ─────────────────────

    private fun fixed(slot: InspirationSlot, text: String, vararg options: Pair<String, String>) =
        InspirationQuestionDto(slot = slot, text = text, options = options.map { (id, title) -> InspirationOptionDto(id, title) }, source = InspirationQuestionSource.FALLBACK)

    private fun cityQuestion(language: String) = if (language == "en") {
        fixed(InspirationSlot.DISTANCE, "Which city?", "city:jakarta" to "Jakarta", "city:bandung" to "Bandung", "city:surabaya" to "Surabaya", "city:yogyakarta" to "Yogyakarta", "city:denpasar" to "Denpasar")
    } else {
        fixed(InspirationSlot.DISTANCE, "Kota mana?", "city:jakarta" to "Jakarta", "city:bandung" to "Bandung", "city:surabaya" to "Surabaya", "city:yogyakarta" to "Yogyakarta", "city:denpasar" to "Denpasar")
    }

    private fun buddyQuestion(language: String) = if (language == "en") {
        fixed(InspirationSlot.DETAIL, "Do you have a buddy for this activity?", "buddy_yes" to "Yes, I have one", "buddy_no" to "Not yet", "buddy_group" to "I will join a group or open trip")
    } else {
        fixed(InspirationSlot.DETAIL, "Sudah punya buddy untuk aktivitas ini?", "buddy_yes" to "Sudah punya", "buddy_no" to "Belum punya", "buddy_group" to "Ikut grup atau open trip")
    }

    private fun budgetQuestion(language: String) = if (language == "en") {
        fixed(InspirationSlot.DETAIL, "What is your shopping budget?", "budget_low" to "Tight (under Rp500,000)", "budget_mid" to "Medium (Rp500,000–2 million)", "budget_high" to "Generous (over Rp2 million)")
    } else {
        fixed(InspirationSlot.DETAIL, "Berapa budget belanjamu?", "budget_low" to "Hemat (di bawah Rp500 ribu)", "budget_mid" to "Sedang (Rp500 ribu–2 juta)", "budget_high" to "Leluasa (di atas Rp2 juta)")
    }

    private fun levelQuestion(language: String) = if (language == "en") {
        fixed(InspirationSlot.DETAIL, "How experienced are you in this activity?", "level_none" to "Never tried it", "level_beginner" to "Beginner", "level_mid" to "Intermediate or basic licence", "level_adv" to "Advanced or higher licence")
    } else {
        fixed(InspirationSlot.DETAIL, "Seberapa berpengalaman kamu di aktivitas ini?", "level_none" to "Belum pernah coba", "level_beginner" to "Pemula", "level_mid" to "Menengah atau punya sertifikat dasar", "level_adv" to "Mahir atau bersertifikat lanjut")
    }

    private fun askAgainFallback(language: String) = if (language == "en") {
        fixed(InspirationSlot.OPTIONAL, "What would you like to change?", "closer" to "Closer", "further" to "Further away", "calmer" to "Calmer", "harder" to "More challenging", "cheaper" to "Cheaper")
    } else {
        fixed(InspirationSlot.OPTIONAL, "Apa yang mau kamu ubah?", "closer" to "Lebih dekat", "further" to "Lebih jauh", "calmer" to "Lebih santai", "harder" to "Lebih menantang", "cheaper" to "Lebih hemat")
    }

    private fun safetyLine(language: String) =
        if (language == "en") "Ransel is not an operator. Check licences and your buddy before you go."
        else "Ransel bukan operator. Pastikan lisensi dan buddy sebelum berangkat."

    private fun defaultVisaNote(language: String) =
        if (language == "en") "AI estimate: check the passport and visa rules for your nationality before you go."
        else "Perkiraan AI: cek lagi aturan paspor dan visa untuk kewarganegaraanmu sebelum berangkat."

    // ── storage ─────────────────────────────────────────────────────────────

    private class Session(
        val id: UUID, val userId: UUID, val status: InspirationStatus, val language: String, val state: InspirationState,
        val result: StoredInspirationResult?, val askAgainUsed: Int, val chipEditsUsed: Int, val chargedAt: Instant?,
        val expiresAt: Instant,
    )

    private fun ResultRow.toSession() = Session(
        id = this[InspirationSessions.id], userId = this[InspirationSessions.userId], status = this[InspirationSessions.status],
        language = this[InspirationSessions.language], state = this[InspirationSessions.state], result = this[InspirationSessions.result],
        askAgainUsed = this[InspirationSessions.askAgainUsed], chipEditsUsed = this[InspirationSessions.chipEditsUsed],
        chargedAt = this[InspirationSessions.chargedAt], expiresAt = this[InspirationSessions.expiresAt],
    )

    /** 404 for a session of another user, an unknown id, or one past its 24 h. */
    private fun load(userId: UUID, id: UUID): Session {
        val session = transaction {
            InspirationSessions.selectAll().where { (InspirationSessions.id eq id) and (InspirationSessions.userId eq userId) }.singleOrNull()
        }?.toSession() ?: throw notFound("Session")
        if (now().isAfter(session.expiresAt)) {
            if (session.status != InspirationStatus.EXPIRED) {
                transaction { InspirationSessions.update({ InspirationSessions.id eq id }) { it[status] = InspirationStatus.EXPIRED } }
            }
            throw notFound("Session")
        }
        return session
    }

    private fun save(id: UUID, status: InspirationStatus, state: InspirationState) = transaction {
        InspirationSessions.update({ InspirationSessions.id eq id }) {
            it[InspirationSessions.status] = status
            it[InspirationSessions.state] = state
            it[updatedAt] = now()
        }
    }

    private fun saveResult(
        id: UUID, status: InspirationStatus, state: InspirationState, result: StoredInspirationResult,
        askAgainUsed: Int? = null, chipEditsUsed: Int? = null,
    ) = transaction {
        InspirationSessions.update({ InspirationSessions.id eq id }) {
            it[InspirationSessions.status] = status
            it[InspirationSessions.state] = state
            it[InspirationSessions.result] = result
            askAgainUsed?.let { n -> it[InspirationSessions.askAgainUsed] = n }
            chipEditsUsed?.let { n -> it[InspirationSessions.chipEditsUsed] = n }
            it[updatedAt] = now()
        }
    }

    private fun dto(session: Session, paid: CreditsDto? = null): InspirationSessionDto {
        val showResult = session.chargedAt != null || paid != null
        return InspirationSessionDto(
            id = session.id.toString(),
            status = session.status,
            question = session.state.current?.takeIf { session.status == InspirationStatus.ACTIVE },
            result = session.result?.takeIf { showResult }?.signed(),
            askAgainFreeLeft = inspirationFreeRefinesLeft(InspirationRefine.ASK_AGAIN, session.askAgainUsed),
            chipEditsFreeLeft = inspirationFreeRefinesLeft(InspirationRefine.CHIP_EDIT, session.chipEditsUsed),
            extraCost = credits.cost(CreditAction.NEXT_BATCH),
            expiresAt = session.expiresAt.toTimestamp(),
            credits = paid,
        )
    }

    /** Photo links are signed when sent (they expire); the stored result has none. */
    private fun StoredInspirationResult.signed(): InspirationResultDto {
        fun StoredInspirationCard.dto() = card.copy(photoUrl = photoPlaceId?.let(photoSigner::signedPath))
        return result.copy(cards = cards.map { it.dto() }, surprise = surprise?.dto())
    }

    /** Card ids in order: c1, c2, ... then the surprise (`s1`); appended cards keep counting. */
    private fun StoredInspirationResult.renumbered(): StoredInspirationResult {
        val main = cards.mapIndexed { i, c -> c.copy(card = c.card.copy(id = "c${i + 1}", surprise = false)) }
        val sur = surprise?.let { it.copy(card = it.card.copy(id = "s1", surprise = true)) }
        return copy(result = result.copy(cards = main.map { it.card }, surprise = sur?.card), cards = main, surprise = sur)
    }

    private fun regions(result: StoredInspirationResult) = (result.cards + listOfNotNull(result.surprise)).map { it.card.region }

    private fun ResultRow.toIdeaDto(): SavedIdeaDto {
        val stored = this[SavedIdeas.card]
        val hours = credits.config.inspirationRefreshHours.toLong()
        val canRefreshAt = this[SavedIdeas.refreshRequestedAt]?.plus(Duration.ofHours(hours))?.takeIf { it.isAfter(now()) }
        return SavedIdeaDto(
            id = this[SavedIdeas.id].toString(),
            card = stored.card.copy(photoUrl = stored.photoPlaceId?.let(photoSigner::signedPath)),
            profile = this[SavedIdeas.profile],
            savedAt = this[SavedIdeas.savedAt].toTimestamp(),
            refreshedAt = this[SavedIdeas.refreshedAt].toTimestamp(),
            canRefreshAt = canRefreshAt?.toTimestamp(),
        )
    }

    private fun requireUnderSessionLimit(userId: UUID) {
        val limit = credits.config.inspirationSessionsPerHour
        val windowStart = now().minus(Duration.ofHours(1))
        val recent = transaction {
            InspirationSessions.selectAll()
                .where { (InspirationSessions.userId eq userId) and (InspirationSessions.createdAt greater windowStart) }
                .orderBy(InspirationSessions.createdAt to SortOrder.ASC)
                .map { it[InspirationSessions.createdAt] }
        }
        if (recent.size >= limit) {
            val freeAt = recent[recent.size - limit].plus(Duration.ofHours(1))
            throw rateLimited("At most $limit new sessions per hour", Duration.between(now(), freeAt).seconds.coerceAtLeast(1))
        }
    }

    private fun writeLog(
        sessionId: UUID, userId: UUID, step: Int, kind: String, slot: InspirationSlot?, source: InspirationQuestionSource?,
        outcome: String, latencyMs: Int, question: String?,
    ) {
        runCatching {
            transaction {
                InspirationSessionLog.insert {
                    it[id] = UUID.randomUUID()
                    it[InspirationSessionLog.sessionId] = sessionId
                    it[InspirationSessionLog.userId] = userId
                    it[InspirationSessionLog.step] = step
                    it[InspirationSessionLog.kind] = kind
                    it[InspirationSessionLog.slot] = slot?.name
                    it[InspirationSessionLog.questionSource] = source?.name
                    it[InspirationSessionLog.outcome] = outcome
                    it[InspirationSessionLog.latencyMs] = latencyMs
                    it[InspirationSessionLog.question] = question?.let(::scrub)
                    it[creditsCharged] = 0
                    it[createdAt] = now()
                }
            }
        }.onFailure { log.warn("inspiration log write failed: {}", it.javaClass.simpleName) }
    }

    private fun logChoice(sessionId: UUID, step: Int, choice: String) {
        runCatching {
            transaction {
                InspirationSessionLog.update({
                    (InspirationSessionLog.sessionId eq sessionId) and (InspirationSessionLog.step eq step) and (InspirationSessionLog.kind eq "QUESTION")
                }) { it[InspirationSessionLog.choice] = scrub(choice) }
            }
        }
    }

    private fun elapsed(startedNanos: Long) = ((System.nanoTime() - startedNanos) / 1_000_000).toInt()

    private fun unavailable() = ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "The ideas could not be built; nothing was charged")

    private fun parseId(raw: String, field: String): UUID =
        runCatching { UUID.fromString(raw.trim()) }.getOrNull() ?: throw validationFailed(mapOf(field to "INVALID_UUID"))

    private fun kotlin.time.Instant.toJavaInstantCompat(): Instant = Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

    companion object {
        private const val MAX_ADVANCE_ROUNDS = 6
        /** Answers that "Back" can undo: more than a whole interview (6 steps + city + mandatory questions). */
        private const val MAX_HISTORY = 12
        private const val CARD_ATTEMPTS = 2
        private const val CARDS_TIMEOUT_MS = 30_000L
        private const val PHOTO_CACHE_MAX = 5_000

        private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
        private val PHONE = Regex("""\+?\d[\d\s().-]{7,}\d""")

        /**
         * Sentence case on the first letter: the model sometimes writes everything lowercase ("kombinasi pengalaman …",
         * owner's device test, 22 Nov 2026). Only the first letter changes; names inside keep their case.
         */
        fun sentence(text: String): String = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        /** Free text keeps its meaning but loses anything that looks like an email or a phone number (PRD "Tracking"). */
        fun scrub(text: String): String = PHONE.replace(EMAIL.replace(text, "[email]"), "[phone]")

        fun rateLimited(message: String, retryAfterSeconds: Long) = ApiException(
            HttpStatusCode.TooManyRequests, ErrorCode.RATE_LIMITED, message,
            mapOf("retryAfterSeconds" to retryAfterSeconds.toString()), retryAfterSeconds = retryAfterSeconds,
        )
    }
}
