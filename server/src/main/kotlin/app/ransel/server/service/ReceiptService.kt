package app.ransel.server.service

import app.ransel.server.ai.AiServices
import app.ransel.server.ai.LlmReceipt
import app.ransel.server.db.Trips
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.tripNotFound
import app.ransel.server.plugins.validationFailed
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.currencyDecimals
import app.ransel.shared.domain.isValidCurrency
import app.ransel.shared.dto.CreditsDto
import app.ransel.shared.dto.ReceiptDraftDto
import app.ransel.shared.dto.ReceiptLineDto
import app.ransel.shared.model.CreditAction
import app.ransel.shared.model.Role
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Receipt photos (PRD "Receipt photo"): Gemini reads the photo, nothing is stored, the app fills the expense form.
 * Credits are checked before the call and charged only when a total was read, like an Explore search.
 */
class ReceiptService(private val ai: AiServices, private val credits: CreditService) {
    private val log = LoggerFactory.getLogger(ReceiptService::class.java)

    suspend fun parse(userId: UUID, tripId: UUID, image: ByteArray, mimeType: String?): ReceiptDraftDto {
        requireRole(tripId, userId, Role.EDITOR)
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (type !in IMAGE_TYPES) throw validationFailed(mapOf("image" to "UNSUPPORTED_TYPE"))
        if (image.size > MAX_BYTES) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.FILE_TOO_LARGE, "Receipt photo is too large", mapOf("maxBytes" to MAX_BYTES.toString()))
        }
        if (image.isEmpty()) throw validationFailed(mapOf("image" to "REQUIRED"))
        requireVerifiedEmail(userId)
        credits.requireAffordable(userId, CreditAction.RECEIPT)
        val base = transaction {
            Trips.select(Trips.baseCurrency).where { Trips.id eq tripId }.singleOrNull()?.get(Trips.baseCurrency)?.trim()
        } ?: throw tripNotFound()

        val read = readWithRetry(image, type!!)?.takeIf { it.total != null } ?: throw unreadable()
        val draft = toDraft(read, base, credits.charge(userId, CreditAction.RECEIPT, tripId.toString()))
        log.info("receipt.read lines={} currency={}", draft.items.size, draft.currency)
        return draft
    }

    /** One retry on a parse or transport problem; still failing = 503 AI_UNAVAILABLE. Null = the model saw no receipt. */
    private suspend fun readWithRetry(image: ByteArray, type: String): LlmReceipt? {
        var last: Exception? = null
        repeat(2) {
            try {
                return ai.llm.readReceipt(image, type)
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                last = e
                log.warn("receipt.read failed: {}", e.toString())
            }
        }
        throw ApiException(HttpStatusCode.ServiceUnavailable, ErrorCode.AI_UNAVAILABLE, "Receipt reading is unavailable").also {
            log.warn("receipt.read gave up: {}", last?.toString())
        }
    }

    private fun unreadable() = ApiException(HttpStatusCode.UnprocessableEntity, ErrorCode.RECEIPT_UNREADABLE, "No total could be read from the photo")

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        /** Amounts move to minor units of the receipt's currency (the base when it is unknown). */
        internal fun toDraft(read: LlmReceipt, baseCurrency: String, wallet: CreditsDto): ReceiptDraftDto {
            val currency = read.currency?.takeIf { isValidCurrency(it) } ?: baseCurrency
            val factor = 10.0.pow(currencyDecimals(currency))
            fun minor(major: Double?) = ((major ?: 0.0) * factor).roundToLong()
            return ReceiptDraftDto(
                placeName = read.placeName,
                spentOn = read.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                currency = currency,
                items = read.items.map { ReceiptLineDto(it.name, it.quantity, minor(it.amount)) },
                taxMinor = minor(read.tax),
                serviceMinor = minor(read.service),
                totalMinor = minor(read.total),
                credits = wallet,
            )
        }
    }
}
