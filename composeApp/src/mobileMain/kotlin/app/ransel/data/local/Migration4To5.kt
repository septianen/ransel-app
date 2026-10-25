package app.ransel.data.local

import androidx.room3.RenameColumn
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import app.ransel.data.userIdsToMemberIds
import app.ransel.shared.domain.memberIdFor

/**
 * Trip members got their own id (contract change 2026-10-25): an expense now says who paid by member id, and the
 * splits and payers JSON use `memberId` instead of `userId`. A person's member id in a trip is
 * `memberIdFor(tripId, userId)`, so the old user ids of the rows are turned into member ids here.
 * The column rename is done by Room; this fixes the values.
 */
@RenameColumn(tableName = "expenses", fromColumnName = "paidByUserId", toColumnName = "paidByMemberId")
class Migration4To5 : AutoMigrationSpec {
    private data class Row(val id: String, val tripId: String, val paidBy: String, val splits: String?, val payers: String?)

    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        val rows = mutableListOf<Row>()
        connection.prepare("SELECT id, tripId, paidByMemberId, splitsJson, payersJson FROM expenses").use { st ->
            while (st.step()) {
                rows += Row(
                    id = st.getText(0), tripId = st.getText(1), paidBy = st.getText(2),
                    splits = if (st.isNull(3)) null else st.getText(3),
                    payers = if (st.isNull(4)) null else st.getText(4),
                )
            }
        }
        for (row in rows) {
            connection.prepare("UPDATE expenses SET paidByMemberId = ?, splitsJson = ?, payersJson = ? WHERE id = ?").use { st ->
                st.bindText(1, memberIdFor(row.tripId, row.paidBy))
                bindOrNull(st, 2, userIdsToMemberIds(row.splits, row.tripId))
                bindOrNull(st, 3, userIdsToMemberIds(row.payers, row.tripId))
                st.bindText(4, row.id)
                st.step()
            }
        }
    }

    private fun bindOrNull(st: androidx.sqlite.SQLiteStatement, index: Int, value: String?) {
        if (value == null) st.bindNull(index) else st.bindText(index, value)
    }
}
