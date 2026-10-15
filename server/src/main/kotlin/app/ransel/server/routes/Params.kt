package app.ransel.server.routes

import app.ransel.server.plugins.notFound
import app.ransel.server.plugins.tripNotFound
import app.ransel.server.plugins.validationFailed
import io.ktor.server.application.ApplicationCall
import java.util.UUID

private fun parseUuid(raw: String?): UUID? = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** `{id}` of /trips/{id}/...; a malformed id can only be a trip that does not exist. */
fun ApplicationCall.tripId(): UUID = parseUuid(parameters["id"]) ?: throw tripNotFound()

/** Another path id (`itemId`, `expenseId`); malformed = that resource does not exist. */
fun ApplicationCall.pathUuid(name: String, what: String): UUID =
    parseUuid(parameters[name]) ?: throw notFound(what)

/** An id from a request body: malformed = 422 on that field. */
fun bodyUuid(raw: String, field: String): UUID =
    parseUuid(raw) ?: throw validationFailed(mapOf(field to "INVALID_UUID"))

fun bodyUuidOrNull(raw: String?, field: String): UUID? = raw?.let { bodyUuid(it, field) }
