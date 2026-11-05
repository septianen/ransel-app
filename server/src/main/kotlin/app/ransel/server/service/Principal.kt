package app.ransel.server.service

import app.ransel.server.plugins.ApiException
import app.ransel.server.security.TokenService
import app.ransel.shared.api.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.time.Instant
import java.util.UUID

/** The signed-in user: the JWT subject. Only valid inside `authenticate("jwt")`. */
fun ApplicationCall.userId(): UUID =
    principal<JWTPrincipal>()?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "Sign in required")

/** Refresh-token row id from the access token, when it was issued with one. */
fun ApplicationCall.sessionId(): UUID? =
    principal<JWTPrincipal>()?.payload?.getClaim(TokenService.SESSION_ID_CLAIM)?.asString()
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** When this access token was issued. Used only to recognise a session minted before [sessionId] existed. */
fun ApplicationCall.accessIssuedAt(): Instant? =
    principal<JWTPrincipal>()?.payload?.issuedAt?.toInstant()
