package app.ransel.server.service

import app.ransel.server.plugins.ApiException
import app.ransel.shared.api.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

/** The signed-in user: the JWT subject. Only valid inside `authenticate("jwt")`. */
fun ApplicationCall.userId(): UUID =
    principal<JWTPrincipal>()?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, "Sign in required")
