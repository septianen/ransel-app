package app.ransel.server.service

import app.ransel.server.db.PaymentMethods
import app.ransel.server.db.toPaymentMethodDto
import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.requireValid
import app.ransel.shared.domain.MAX_PAYMENT_METHODS
import app.ransel.shared.domain.validatePaymentMethod
import app.ransel.shared.dto.PaymentMethodDto
import app.ransel.shared.dto.PaymentMethodInput
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

/** Account → Info pembayaran. Only the account itself reads this list; trip members get it through pay-info. */
object PaymentMethodService {
    fun list(userId: UUID): List<PaymentMethodDto> = transaction { mine(userId) }

    fun create(userId: UUID, body: PaymentMethodInput): PaymentMethodDto {
        requireValid(validatePaymentMethod(body))
        return transaction {
            if (mine(userId).size >= MAX_PAYMENT_METHODS) requireValid(mapOf("id" to "TOO_MANY"))
            val id = UUID.randomUUID()
            // the first one is the primary one
            val primary = body.isPrimary || mine(userId).isEmpty()
            if (primary) clearPrimary(userId, except = null)
            PaymentMethods.insert {
                it[PaymentMethods.id] = id
                it[PaymentMethods.userId] = userId
                fill(it, body)
                it[isPrimary] = primary
            }
            load(userId, id)
        }
    }

    fun update(userId: UUID, id: UUID, body: PaymentMethodInput): PaymentMethodDto {
        requireValid(validatePaymentMethod(body))
        return transaction {
            load(userId, id)
            if (body.isPrimary) clearPrimary(userId, except = id)
            PaymentMethods.update({ PaymentMethods.id eq id }) {
                fill(it, body)
                if (body.isPrimary) it[isPrimary] = true
            }
            load(userId, id)
        }
    }

    fun delete(userId: UUID, id: UUID) {
        transaction {
            load(userId, id)
            PaymentMethods.update({ PaymentMethods.id eq id }) {
                it[deletedAt] = Instant.now()
                it[isPrimary] = false
            }
        }
    }

    private fun mine(userId: UUID): List<PaymentMethodDto> =
        PaymentMethods.selectAll()
            .where { (PaymentMethods.userId eq userId) and PaymentMethods.deletedAt.isNull() }
            .orderBy(PaymentMethods.isPrimary to SortOrder.DESC, PaymentMethods.createdAt to SortOrder.ASC)
            .map { it.toPaymentMethodDto() }

    private fun load(userId: UUID, id: UUID): PaymentMethodDto =
        PaymentMethods.selectAll()
            .where { (PaymentMethods.id eq id) and (PaymentMethods.userId eq userId) and PaymentMethods.deletedAt.isNull() }
            .singleOrNull()?.toPaymentMethodDto() ?: throw notFound("Payment method")

    private fun clearPrimary(userId: UUID, except: UUID?) {
        PaymentMethods.update({
            val mine = (PaymentMethods.userId eq userId) and PaymentMethods.isPrimary
            if (except == null) mine else mine and (PaymentMethods.id neq except)
        }) { it[isPrimary] = false }
    }

    private fun fill(it: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>, body: PaymentMethodInput) {
        it[PaymentMethods.kind] = body.kind
        it[PaymentMethods.provider] = body.provider.trim()
        it[PaymentMethods.accountNumber] = body.accountNumber.trim()
        it[PaymentMethods.holderName] = body.holderName.trim()
    }
}
