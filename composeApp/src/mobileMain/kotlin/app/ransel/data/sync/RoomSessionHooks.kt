package app.ransel.data.sync

import androidx.room3.withWriteTransaction
import app.ransel.data.auth.SessionHooks
import app.ransel.data.local.RanselDatabase
import app.ransel.shared.domain.memberIdFor
import app.ransel.shared.dto.LOCAL_USER_ID

/**
 * Guest → account: on sign-in the guest's rows become the user's (owner / payer rewritten) and are all
 * marked pending so the first sync uploads them. On sign-out the account's data leaves the device.
 */
class RoomSessionHooks(
    private val db: RanselDatabase,
    private val coordinator: () -> SyncCoordinator,
) : SessionHooks {
    override suspend fun onSignedIn(userId: String) {
        val dao = db.syncDao()
        db.withWriteTransaction {
            // Only guest data is new to the account; signing in again as the same user must not resend all.
            if (dao.guestRowCount(LOCAL_USER_ID) == 0) return@withWriteTransaction
            // The guest's member in each trip becomes the account's, so who paid and who shares stay on the same person.
            dao.guestTripIds(LOCAL_USER_ID).forEach { tripId ->
                val guest = memberIdFor(tripId, LOCAL_USER_ID)
                val account = memberIdFor(tripId, userId)
                dao.reownExpenseMember(tripId, guest, account)
                dao.reownPersonalMember(tripId, guest, account)
                dao.reownPackingAssignee(tripId, guest, account)
            }
            dao.reownTrips(LOCAL_USER_ID, userId)
            dao.markAllTripsPending()
            dao.markAllItemsPending()
            dao.markAllPackingPending()
            dao.markAllPersonalPending()
            dao.markAllExpensesPending()
        }
    }

    override suspend fun onSignedOut() {
        // Try once to send what is pending, then wipe (the confirm dialog warned about unsent changes).
        runCatching { coordinator().flush() }
        coordinator().wipe()
    }
}
