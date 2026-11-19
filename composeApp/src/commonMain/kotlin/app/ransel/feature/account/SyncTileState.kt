package app.ransel.feature.account

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Visual states of the Account sync tile (Figma: idle, offline, syncing). */
enum class SyncTileKind { IDLE, OFFLINE, SYNCING }

/**
 * What the sync tile shows. [clock] is `HH:mm` in [timeZone] when a last-sync instant exists.
 * The action is hidden while [kind] is [SyncTileKind.SYNCING]; it uses the error colour when offline.
 */
data class SyncTileSnapshot(
    val kind: SyncTileKind,
    val clock: String?,
) {
    val actionVisible: Boolean get() = kind != SyncTileKind.SYNCING
    val actionIsError: Boolean get() = kind == SyncTileKind.OFFLINE
}

fun syncTileSnapshot(
    syncing: Boolean,
    offline: Boolean,
    lastSyncAt: Instant?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): SyncTileSnapshot {
    val kind = when {
        syncing -> SyncTileKind.SYNCING
        offline -> SyncTileKind.OFFLINE
        else -> SyncTileKind.IDLE
    }
    return SyncTileSnapshot(kind = kind, clock = lastSyncAt?.let { formatAccountClock(it, timeZone) })
}

/** "14:32" in the given zone (the app writes times as HH:mm in both languages, owner 13 Nov 2026). */
fun formatAccountClock(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = instant.toLocalDateTime(timeZone)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
