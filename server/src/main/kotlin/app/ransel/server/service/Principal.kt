package app.ransel.server.service

import io.ktor.server.application.ApplicationCall
import java.util.UUID

/** Dummy user seeded in V1. Replaced by the JWT subject in Phase 3 (B3.3); only this file changes. */
val LOCAL_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

@Suppress("UnusedReceiverParameter")
fun ApplicationCall.userId(): UUID = LOCAL_USER_ID
