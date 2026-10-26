package app.ransel.server.service

import app.ransel.server.db.ExpenseItems
import app.ransel.server.db.ExpensePayers
import app.ransel.server.db.ExpenseSplits
import app.ransel.server.db.Expenses
import app.ransel.server.db.MemberClaims
import app.ransel.server.db.Kitties
import app.ransel.server.db.KittyDeposits
import app.ransel.server.db.Settlements
import app.ransel.server.db.TripMembers
import app.ransel.server.db.Users
import app.ransel.server.db.toTimestamp
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.conflict
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.normalizePhone
import app.ransel.shared.domain.validateMemberName
import app.ransel.shared.dto.AddMemberRequest
import app.ransel.shared.dto.TripMemberDto
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/**
 * Members of a trip. A member is a `trip_members` row with its own id; it does not need an account (the OWNER adds
 * people by name, and one of them can later be claimed by an account). Removing a member, leaving, or being claimed
 * only sets `removed_at`: the row stays, so expenses and splits keep pointing at a member that still has a name and
 * balances stay correct.
 */
object MemberService {
    /** Everyone sees the members; only the OWNER sees their email addresses and the phone numbers of members added by name. */
    fun list(userId: UUID, tripId: UUID): List<TripMemberDto> {
        val myRole = requireRole(tripId, userId, Role.VIEWER)
        return transaction { members(tripId, isOwner = myRole == Role.OWNER) }
    }

    /** OWNER only: adds a person without an account. They can be picked in "Dibayar oleh" and "Bagi dengan" but cannot edit the trip. */
    fun addGuest(userId: UUID, tripId: UUID, body: AddMemberRequest): TripMemberDto {
        requireRole(tripId, userId, Role.OWNER)
        val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: throw validationFailed(mapOf("id" to "INVALID_UUID"))
        val name = body.displayName.trim()
        val phone = body.phone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) ?: throw validationFailed(mapOf("phone" to "INVALID_PHONE")) }
        requireValid(validateMemberName(name))
        return transaction {
            if (TripMembers.selectAll().where { TripMembers.id eq id }.any()) throw conflict("Member id already exists")
            TripMembers.insert {
                it[TripMembers.id] = id
                it[TripMembers.tripId] = tripId
                it[role] = Role.VIEWER
                it[displayName] = name
                it[TripMembers.phone] = phone
            }
            members(tripId, isOwner = true).single { it.memberId == id.toString() }
        }
    }

    fun updateRole(userId: UUID, tripId: UUID, targetMemberId: UUID, newRole: Role): TripMemberDto {
        requireRole(tripId, userId, Role.OWNER)
        if (newRole == Role.OWNER) throw validationFailed(mapOf("role" to "USE_TRANSFER_OWNERSHIP"))
        return transaction {
            val target = activeMember(tripId, targetMemberId) ?: throw notFound("Member")
            val current = target[TripMembers.role]
            if (current == Role.OWNER) throw validationFailed(mapOf("memberId" to "OWNER_ROLE_IS_FIXED"))
            if (target[TripMembers.userId] == null) throw validationFailed(mapOf("role" to "GUEST_ROLE_IS_FIXED"))
            TripMembers.update({ TripMembers.id eq targetMemberId }) { it[role] = newRole }
            if (current != newRole) Notify.emit { roleChanged(tripId, target[TripMembers.userId]!!, newRole) }
            members(tripId, isOwner = true).single { it.memberId == targetMemberId.toString() }
        }
    }

    fun remove(userId: UUID, tripId: UUID, targetMemberId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val target = activeMember(tripId, targetMemberId) ?: throw notFound("Member")
            if (target[TripMembers.role] == Role.OWNER) throw ownerCannotLeave()
            softRemove(targetMemberId)
            PollService.handOver(tripId, targetMemberId)
            target[TripMembers.userId]?.let { removed -> Notify.emit { memberRemoved(tripId, removed) } }
        }
    }

    fun leave(userId: UUID, tripId: UUID) {
        val role = requireRole(tripId, userId, Role.VIEWER)
        if (role == Role.OWNER) throw ownerCannotLeave()
        transaction {
            softRemove(callerMemberId(tripId, userId))
            PollService.handOver(tripId, callerMemberId(tripId, userId))
        }
    }

    /** Old owner becomes EDITOR, the new owner OWNER, in one transaction (a trip never has zero or two owners). */
    fun transferOwnership(userId: UUID, tripId: UUID, newOwnerId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        if (newOwnerId == userId) throw validationFailed(mapOf("newOwnerUserId" to "ALREADY_OWNER"))
        transaction {
            val newOwner = activeMember(tripId, callerMemberId(tripId, newOwnerId))
                ?: throw validationFailed(mapOf("newOwnerUserId" to "NOT_A_MEMBER"))
            check(newOwner[TripMembers.userId] == newOwnerId)
            // Demote first: the unique index allows one OWNER row per trip.
            TripMembers.update({ TripMembers.id eq callerMemberId(tripId, userId) }) { it[role] = Role.EDITOR }
            TripMembers.update({ TripMembers.id eq newOwner[TripMembers.id] }) { it[role] = Role.OWNER }
        }
    }

    // ── claiming a member added by name ("Kamu yang mana?") ─────────────────

    /**
     * The caller's own member takes over [guestMemberId]: paid by, payers, splits and settlements move to the caller in
     * one transaction, and the claimed member is hidden. The ids of the moved rows are logged so the OWNER can undo it.
     */
    fun claim(userId: UUID, tripId: UUID, guestMemberId: UUID) {
        requireRole(tripId, userId, Role.VIEWER)
        transaction {
            val me = callerMemberId(tripId, userId)
            val guest = activeMember(tripId, guestMemberId) ?: throw notFound("Member")
            if (guest[TripMembers.userId] != null) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.MEMBER_HAS_ACCOUNT, "This member already has an account")
            }
            val alreadyClaimed = MemberClaims.selectAll()
                .where { (MemberClaims.tripId eq tripId) and (MemberClaims.claimedByMemberId eq me) and MemberClaims.undoneAt.isNull() }.any()
            if (alreadyClaimed) {
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.MEMBER_ALREADY_CLAIMED, "You already claimed a member of this trip")
            }

            val moved = MemberMoves.move(tripId, from = guestMemberId, to = me)
            val now = Instant.now()
            TripMembers.update({ TripMembers.id eq guestMemberId }) {
                it[claimedAt] = now
                it[claimedByMemberId] = me
                it[removedAt] = now
            }
            MemberClaims.insert {
                it[id] = UUID.randomUUID()
                it[MemberClaims.tripId] = tripId
                it[MemberClaims.guestMemberId] = guestMemberId
                it[claimedByMemberId] = me
                it[MemberClaims.moved] = moved
                it[claimedAt] = now
            }
            Notify.emit { memberClaimed(tripId, userId, guestMemberId, guest[TripMembers.displayName].orEmpty()) }
        }
    }

    /** OWNER only: a wrong claim goes back, the rows that were moved return to the member added by name. */
    fun undoClaim(userId: UUID, tripId: UUID, guestMemberId: UUID) {
        requireRole(tripId, userId, Role.OWNER)
        transaction {
            val claim = MemberClaims.selectAll()
                .where { (MemberClaims.tripId eq tripId) and (MemberClaims.guestMemberId eq guestMemberId) and MemberClaims.undoneAt.isNull() }
                .singleOrNull() ?: throw notFound("Claim")
            MemberMoves.undo(tripId, guest = guestMemberId, claimer = claim[MemberClaims.claimedByMemberId], moved = claim[MemberClaims.moved])
            TripMembers.update({ TripMembers.id eq guestMemberId }) {
                it[claimedAt] = null
                it[claimedByMemberId] = null
                it[removedAt] = null
            }
            MemberClaims.update({ MemberClaims.id eq claim[MemberClaims.id] }) { it[undoneAt] = Instant.now() }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    internal fun members(tripId: UUID, isOwner: Boolean): List<TripMemberDto> =
        TripMembers.join(Users, JoinType.LEFT, TripMembers.userId, Users.id)
            .selectAll()
            .where { (TripMembers.tripId eq tripId) and TripMembers.active() }
            .orderBy(TripMembers.joinedAt to SortOrder.ASC, TripMembers.id to SortOrder.ASC)
            .map { it.toMemberDto(isOwner) }

    internal fun ResultRow.toMemberDto(isOwner: Boolean) = TripMemberDto(
        tripId = this[TripMembers.tripId].toString(),
        memberId = this[TripMembers.id].toString(),
        userId = this[TripMembers.userId]?.toString(),
        displayName = this[TripMembers.displayName] ?: this[Users.displayName],
        email = if (isOwner) this[Users.email] else null,
        role = this[TripMembers.role],
        joinedAt = this[TripMembers.joinedAt].toTimestamp(),
        phone = if (isOwner) this[TripMembers.phone] else null,
        claimedAt = this[TripMembers.claimedAt]?.toTimestamp(),
    )

    private fun activeMember(tripId: UUID, memberId: UUID): ResultRow? =
        TripMembers.selectAll()
            .where { (TripMembers.id eq memberId) and (TripMembers.tripId eq tripId) and TripMembers.active() }
            .singleOrNull()

    private fun softRemove(memberId: UUID) {
        TripMembers.update({ TripMembers.id eq memberId }) { it[removedAt] = Instant.now() }
    }

    private fun ownerCannotLeave() =
        ApiException(HttpStatusCode.Conflict, ErrorCode.OWNER_CANNOT_LEAVE, "Transfer ownership before leaving the trip")
}

/**
 * Rewrites every row that points at one member so it points at another, and remembers which rows it touched.
 * Where both members are on the same expense the two parts are added together (and split apart again on undo).
 */
internal object MemberMoves {
    private const val PAID_BY = "paidBy"
    private const val SPLITS = "splits"
    private const val SPLITS_MERGED = "splitsMerged"
    private const val PAYERS = "payers"
    private const val PAYERS_MERGED = "payersMerged"
    private const val SETTLEMENTS_FROM = "settlementsFrom"
    private const val SETTLEMENTS_TO = "settlementsTo"
    private const val SETTLEMENTS_DROPPED = "settlementsDropped"
    private const val ITEMS = "items"
    private const val ITEMS_MERGED = "itemsMerged"
    private const val DEPOSITS = "deposits"
    private const val DEPOSITS_RECEIVED = "depositsReceived"
    private const val TREASURER = "treasurer"

    fun move(tripId: UUID, from: UUID, to: UUID): Map<String, List<String>> {
        val log = linkedMapOf<String, List<String>>()
        val touched = mutableSetOf<UUID>()

        val tripExpenses = Expenses.selectAll().where { Expenses.tripId eq tripId }.map { it[Expenses.id] }
        if (tripExpenses.isNotEmpty()) {
            val paidBy = Expenses.selectAll().where { (Expenses.id inList tripExpenses) and (Expenses.paidByMemberId eq from) }.map { it[Expenses.id] }
            if (paidBy.isNotEmpty()) {
                Expenses.update({ Expenses.id inList paidBy }) { it[paidByMemberId] = to }
                log[PAID_BY] = paidBy.map(UUID::toString)
                touched += paidBy
            }

            val splitRows = ExpenseSplits.selectAll().where { (ExpenseSplits.expenseId inList tripExpenses) and (ExpenseSplits.memberId eq from) }.toList()
            val moved = mutableListOf<String>()
            val merged = mutableListOf<String>()
            for (row in splitRows) {
                val expenseId = row[ExpenseSplits.expenseId]
                val mine = ExpenseSplits.selectAll().where { (ExpenseSplits.expenseId eq expenseId) and (ExpenseSplits.memberId eq to) }.singleOrNull()
                if (mine == null) {
                    ExpenseSplits.update({ (ExpenseSplits.expenseId eq expenseId) and (ExpenseSplits.memberId eq from) }) { it[memberId] = to }
                    moved += expenseId.toString()
                } else {
                    ExpenseSplits.update({ (ExpenseSplits.expenseId eq expenseId) and (ExpenseSplits.memberId eq to) }) {
                        it[shareMinor] = mine[ExpenseSplits.shareMinor] + row[ExpenseSplits.shareMinor]
                    }
                    ExpenseSplits.deleteWhere { (ExpenseSplits.expenseId eq expenseId) and (ExpenseSplits.memberId eq from) }
                    merged += "$expenseId:${row[ExpenseSplits.shareMinor]}"
                }
                touched += expenseId
            }
            if (moved.isNotEmpty()) log[SPLITS] = moved
            if (merged.isNotEmpty()) log[SPLITS_MERGED] = merged

            val payerRows = ExpensePayers.selectAll().where { (ExpensePayers.expenseId inList tripExpenses) and (ExpensePayers.memberId eq from) }.toList()
            val movedPayers = mutableListOf<String>()
            val mergedPayers = mutableListOf<String>()
            for (row in payerRows) {
                val expenseId = row[ExpensePayers.expenseId]
                val mine = ExpensePayers.selectAll().where { (ExpensePayers.expenseId eq expenseId) and (ExpensePayers.memberId eq to) }.singleOrNull()
                if (mine == null) {
                    ExpensePayers.update({ (ExpensePayers.expenseId eq expenseId) and (ExpensePayers.memberId eq from) }) { it[memberId] = to }
                    movedPayers += expenseId.toString()
                } else {
                    ExpensePayers.update({ (ExpensePayers.expenseId eq expenseId) and (ExpensePayers.memberId eq to) }) {
                        it[amountMinor] = mine[ExpensePayers.amountMinor] + row[ExpensePayers.amountMinor]
                    }
                    ExpensePayers.deleteWhere { (ExpensePayers.expenseId eq expenseId) and (ExpensePayers.memberId eq from) }
                    mergedPayers += "$expenseId:${row[ExpensePayers.amountMinor]}"
                }
                touched += expenseId
            }
            if (movedPayers.isNotEmpty()) log[PAYERS] = movedPayers
            if (mergedPayers.isNotEmpty()) log[PAYERS_MERGED] = mergedPayers
        }

        val live = Settlements.tripId eq tripId
        // A payment between the two would become a payment to oneself: it is set aside (soft deleted) and comes back on undo.
        val between = Settlements.selectAll().where {
            live and Settlements.deletedAt.isNull() and (((Settlements.fromMemberId eq from) and (Settlements.toMemberId eq to)) or ((Settlements.fromMemberId eq to) and (Settlements.toMemberId eq from)))
        }.map { it[Settlements.id] }
        if (between.isNotEmpty()) {
            Settlements.update({ Settlements.id inList between }) { it[deletedAt] = Instant.now() }
            log[SETTLEMENTS_DROPPED] = between.map(UUID::toString)
        }
        val fromRows = Settlements.selectAll().where { live and (Settlements.fromMemberId eq from) and (Settlements.id notInList between) }.map { it[Settlements.id] }
        if (fromRows.isNotEmpty()) {
            Settlements.update({ Settlements.id inList fromRows }) { it[fromMemberId] = to }
            log[SETTLEMENTS_FROM] = fromRows.map(UUID::toString)
        }
        val toRows = Settlements.selectAll().where { live and (Settlements.toMemberId eq from) and (Settlements.id notInList between) }.map { it[Settlements.id] }
        if (toRows.isNotEmpty()) {
            Settlements.update({ Settlements.id inList toRows }) { it[toMemberId] = to }
            log[SETTLEMENTS_TO] = toRows.map(UUID::toString)
        }

        // receipt lines name members inside JSON: "expenseId:sortOrder", merged when the claimer was on the line already
        if (tripExpenses.isNotEmpty()) {
            val replaced = mutableListOf<String>()
            val mergedItems = mutableListOf<String>()
            ExpenseItems.selectAll().where { ExpenseItems.expenseId inList tripExpenses }.forEach { row ->
                val ids = row[ExpenseItems.memberIds]
                if (from.toString() !in ids) return@forEach
                val key = "${row[ExpenseItems.expenseId]}:${row[ExpenseItems.sortOrder]}"
                val next = if (to.toString() in ids) ids - from.toString() else ids.map { if (it == from.toString()) to.toString() else it }
                if (to.toString() in ids) mergedItems += key else replaced += key
                ExpenseItems.update({ (ExpenseItems.expenseId eq row[ExpenseItems.expenseId]) and (ExpenseItems.sortOrder eq row[ExpenseItems.sortOrder]) }) {
                    it[memberIds] = next
                }
                touched += row[ExpenseItems.expenseId]
            }
            if (replaced.isNotEmpty()) log[ITEMS] = replaced
            if (mergedItems.isNotEmpty()) log[ITEMS_MERGED] = mergedItems
        }

        val deposits = KittyDeposits.selectAll().where { (KittyDeposits.tripId eq tripId) and (KittyDeposits.memberId eq from) }.map { it[KittyDeposits.id] }
        if (deposits.isNotEmpty()) {
            KittyDeposits.update({ KittyDeposits.id inList deposits }) { it[memberId] = to }
            log[DEPOSITS] = deposits.map(UUID::toString)
        }
        val received = KittyDeposits.selectAll().where { (KittyDeposits.tripId eq tripId) and (KittyDeposits.receivedByMemberId eq from) }.map { it[KittyDeposits.id] }
        if (received.isNotEmpty()) {
            KittyDeposits.update({ KittyDeposits.id inList received }) { it[receivedByMemberId] = to }
            log[DEPOSITS_RECEIVED] = received.map(UUID::toString)
        }
        if (Kitties.update({ (Kitties.tripId eq tripId) and (Kitties.treasurerMemberId eq from) }) { it[treasurerMemberId] = to } > 0) {
            log[TREASURER] = listOf(tripId.toString())
        }

        bump(touched)
        return log
    }

    /** Puts back what [move] changed. Rows that were edited or deleted since are left alone. */
    fun undo(tripId: UUID, guest: UUID, claimer: UUID, moved: Map<String, List<String>>) {
        val touched = mutableSetOf<UUID>()
        fun ids(key: String) = moved[key].orEmpty().map(UUID::fromString)

        ids(PAID_BY).also { touched += it }.takeIf { it.isNotEmpty() }?.let { list ->
            Expenses.update({ (Expenses.id inList list) and (Expenses.paidByMemberId eq claimer) }) { it[paidByMemberId] = guest }
        }
        ids(SPLITS).forEach { expenseId ->
            ExpenseSplits.update({ (ExpenseSplits.expenseId eq expenseId) and (ExpenseSplits.memberId eq claimer) }) { it[memberId] = guest }
            touched += expenseId
        }
        moved[SPLITS_MERGED].orEmpty().forEach { entry ->
            val (expense, share) = entry.split(":").let { UUID.fromString(it[0]) to it[1].toLong() }
            val mine = ExpenseSplits.selectAll().where { (ExpenseSplits.expenseId eq expense) and (ExpenseSplits.memberId eq claimer) }.singleOrNull() ?: return@forEach
            val left = mine[ExpenseSplits.shareMinor] - share
            if (left > 0) ExpenseSplits.update({ (ExpenseSplits.expenseId eq expense) and (ExpenseSplits.memberId eq claimer) }) { it[shareMinor] = left }
            else ExpenseSplits.deleteWhere { (ExpenseSplits.expenseId eq expense) and (ExpenseSplits.memberId eq claimer) }
            ExpenseSplits.insert { it[expenseId] = expense; it[memberId] = guest; it[shareMinor] = share }
            touched += expense
        }
        ids(PAYERS).forEach { expenseId ->
            ExpensePayers.update({ (ExpensePayers.expenseId eq expenseId) and (ExpensePayers.memberId eq claimer) }) { it[memberId] = guest }
            touched += expenseId
        }
        moved[PAYERS_MERGED].orEmpty().forEach { entry ->
            val (expense, amount) = entry.split(":").let { UUID.fromString(it[0]) to it[1].toLong() }
            val mine = ExpensePayers.selectAll().where { (ExpensePayers.expenseId eq expense) and (ExpensePayers.memberId eq claimer) }.singleOrNull() ?: return@forEach
            val left = mine[ExpensePayers.amountMinor] - amount
            if (left > 0) ExpensePayers.update({ (ExpensePayers.expenseId eq expense) and (ExpensePayers.memberId eq claimer) }) { it[amountMinor] = left }
            else ExpensePayers.deleteWhere { (ExpensePayers.expenseId eq expense) and (ExpensePayers.memberId eq claimer) }
            ExpensePayers.insert { it[expenseId] = expense; it[memberId] = guest; it[amountMinor] = amount }
            touched += expense
        }
        ids(SETTLEMENTS_FROM).takeIf { it.isNotEmpty() }?.let { list ->
            Settlements.update({ (Settlements.id inList list) and (Settlements.fromMemberId eq claimer) }) { it[fromMemberId] = guest }
        }
        ids(SETTLEMENTS_TO).takeIf { it.isNotEmpty() }?.let { list ->
            Settlements.update({ (Settlements.id inList list) and (Settlements.toMemberId eq claimer) }) { it[toMemberId] = guest }
        }
        ids(SETTLEMENTS_DROPPED).takeIf { it.isNotEmpty() }?.let { list ->
            Settlements.update({ Settlements.id inList list }) { it[deletedAt] = null }
        }
        fun itemRow(entry: String) = entry.split(":").let { UUID.fromString(it[0]) to it[1].toInt() }
        moved[ITEMS].orEmpty().forEach { entry ->
            val (expense, order) = itemRow(entry)
            val where = (ExpenseItems.expenseId eq expense) and (ExpenseItems.sortOrder eq order)
            val ids = ExpenseItems.selectAll().where { where }.singleOrNull()?.get(ExpenseItems.memberIds) ?: return@forEach
            ExpenseItems.update({ where }) { it[memberIds] = ids.map { id -> if (id == claimer.toString()) guest.toString() else id } }
            touched += expense
        }
        moved[ITEMS_MERGED].orEmpty().forEach { entry ->
            val (expense, order) = itemRow(entry)
            val where = (ExpenseItems.expenseId eq expense) and (ExpenseItems.sortOrder eq order)
            val ids = ExpenseItems.selectAll().where { where }.singleOrNull()?.get(ExpenseItems.memberIds) ?: return@forEach
            if (guest.toString() !in ids) ExpenseItems.update({ where }) { it[memberIds] = ids + guest.toString() }
            touched += expense
        }
        ids(DEPOSITS).takeIf { it.isNotEmpty() }?.let { list ->
            KittyDeposits.update({ (KittyDeposits.id inList list) and (KittyDeposits.memberId eq claimer) }) { it[memberId] = guest }
        }
        ids(DEPOSITS_RECEIVED).takeIf { it.isNotEmpty() }?.let { list ->
            KittyDeposits.update({ (KittyDeposits.id inList list) and (KittyDeposits.receivedByMemberId eq claimer) }) { it[receivedByMemberId] = guest }
        }
        if (moved[TREASURER] != null) {
            Kitties.update({ (Kitties.tripId eq tripId) and (Kitties.treasurerMemberId eq claimer) }) { it[treasurerMemberId] = guest }
        }
        bump(touched)
    }

    /** Sync pulls an expense when its own row changed: touch it so phones fetch the new splits and payers. */
    private fun bump(expenseIds: Collection<UUID>) {
        if (expenseIds.isEmpty()) return
        Expenses.update({ Expenses.id inList expenseIds.toList() }) { it[note] = Expenses.note }
    }
}
