package app.ransel.util

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** IDs are UUID strings created on the client (CLAUDE.md "Ground rules"). */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

fun now(): Instant = Clock.System.now()
