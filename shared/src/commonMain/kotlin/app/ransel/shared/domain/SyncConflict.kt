package app.ransel.shared.domain

/**
 * Last-write-wins helpers for offline-first sync (TRD "Offline-first sync").
 *
 * The server stores `updated_at` from its own clock (DB trigger). The client stores the device
 * clock of the edit. Mixing the two without these rules lets a pull of an older server copy
 * replace a row the user just changed (reorder snaps back, Undo bounces, a name/time/cost edit
 * reverts a few seconds later).
 */

/** Room / push wire value: local edit not yet accepted. */
const val SYNC_PENDING = "PENDING"

/** Room / pull: this device matches the server, or the row came from a pull. */
const val SYNC_SYNCED = "SYNCED"

/** Room: the last push was refused; the next full pull puts the server copy back. */
const val SYNC_REJECTED = "REJECTED"

/**
 * Stamp for a local write: never older than the row we already have, so a push is not rejected
 * as CONFLICT just because the device clock is behind the server clock of the last pull.
 */
fun nextUpdatedAtMillis(existingUpdatedAtMillis: Long?, nowMillis: Long): Long {
    val min = (existingUpdatedAtMillis ?: Long.MIN_VALUE) + 1L
    return maxOf(nowMillis, min)
}

/**
 * Whether a pulled row should replace the one in Room.
 *
 * - No local row: take the server copy.
 * - [SYNC_PENDING]: keep the unsent local write. The following push is last-write-wins; a
 *   CONFLICT marks the row [SYNC_REJECTED] and the next full pull applies the server copy
 *   (another device wrote later, or this stamp was still behind).
 * - [SYNC_REJECTED]: take the server copy (that is why the full pull ran).
 * - [SYNC_SYNCED]: only if the server row is strictly newer — an older or equal pull must not
 *   clobber a write that just became SYNCED.
 */
fun shouldApplyPulledRow(
    localUpdatedAtMillis: Long?,
    localSyncState: String?,
    remoteUpdatedAtMillis: Long,
): Boolean {
    if (localUpdatedAtMillis == null || localSyncState.isNullOrEmpty()) return true
    return when (localSyncState) {
        SYNC_PENDING -> false
        SYNC_REJECTED -> true
        else -> remoteUpdatedAtMillis > localUpdatedAtMillis
    }
}
