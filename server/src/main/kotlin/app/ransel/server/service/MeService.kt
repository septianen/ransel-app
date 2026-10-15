package app.ransel.server.service

import app.ransel.server.db.Users
import app.ransel.server.plugins.ApiException
import app.ransel.server.plugins.requireValid
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.domain.isValidCurrency
import app.ransel.shared.dto.MeDto
import app.ransel.shared.dto.UpdateMeRequest
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

object MeService {
    fun get(userId: UUID): MeDto = transaction { load(userId) }

    fun update(userId: UUID, body: UpdateMeRequest): MeDto {
        requireValid(
            buildMap {
                if (body.displayName != null && body.displayName!!.isBlank()) put("displayName", "REQUIRED")
                if (body.defaultCurrency != null && !isValidCurrency(body.defaultCurrency!!)) put("defaultCurrency", "INVALID_CURRENCY")
            },
        )
        return transaction {
            load(userId)
            if (body.displayName != null || body.defaultCurrency != null) {
                Users.update({ Users.id eq userId }) {
                    body.displayName?.let { v -> it[displayName] = v.trim() }
                    body.defaultCurrency?.let { v -> it[defaultCurrency] = v }
                }
            }
            load(userId)
        }
    }

    private fun load(userId: UUID): MeDto =
        Users.selectAll().where { (Users.id eq userId) and Users.deletedAt.isNull() }.singleOrNull()?.toMeDto()
            ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "Account not found")
}
