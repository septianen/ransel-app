package app.ransel.server

import app.ransel.shared.domain.memberIdFor
import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V15 on a database that already holds V1..V14 data: every member gets the id the apps compute offline
 * (`memberIdFor`), and every expense, split, payer and settlement stays on the same person, also for someone
 * who was removed from the trip before the migration.
 */
class MemberIdMigrationTest {
    private val config = TestDb.config()
    private val dbName = "mig15"
    private val url = config.databaseUrl.replace(Regex("/[^/?]+(\\?|$)"), "/$dbName$1")

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
        DriverManager.getConnection(url, config.databaseUser, config.databasePassword).use { conn ->
            conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(read(rs)) } } }
        }

    private fun exec(vararg statements: String) {
        DriverManager.getConnection(url, config.databaseUser, config.databasePassword).use { conn ->
            conn.createStatement().use { st -> statements.forEach { st.execute(it) } }
        }
    }

    private fun flyway(target: String) =
        Flyway.configure().dataSource(url, config.databaseUser, config.databasePassword).target(target).load()

    @Test
    fun v15KeepsEveryoneOnTheirExpenses() {
        TestDb.exec("DROP DATABASE IF EXISTS $dbName WITH (FORCE)")
        TestDb.exec("CREATE DATABASE $dbName")
        flyway("14").migrate()

        val trip = UUID.randomUUID().toString()
        val ayu = UUID.randomUUID().toString()
        val budi = UUID.randomUUID().toString()
        val citra = UUID.randomUUID().toString() // removed before the migration, still on an expense
        val expense = UUID.randomUUID().toString()
        val settlement = UUID.randomUUID().toString()
        exec(
            "INSERT INTO users (id, display_name, email, password_hash) VALUES ('$ayu','Ayu','ayu@example.com','x'),('$budi','Budi','budi@example.com','x'),('$citra','Citra','citra@example.com','x')",
            "INSERT INTO trips (id, owner_id, title, destination, start_date, end_date) VALUES ('$trip','$ayu','Bali','Bali','2026-11-20','2026-11-23')",
            "INSERT INTO trip_members (trip_id, user_id, role) VALUES ('$trip','$ayu','OWNER'),('$trip','$budi','EDITOR')",
            "INSERT INTO expenses (id, trip_id, amount_minor, currency, spent_at, paid_by_user_id) VALUES ('$expense','$trip',300000,'IDR',now(),'$ayu')",
            "INSERT INTO expense_payers (expense_id, user_id, amount_minor) VALUES ('$expense','$ayu',200000),('$expense','$citra',100000)",
            "INSERT INTO expense_splits (expense_id, user_id, share_minor) VALUES ('$expense','$ayu',100000),('$expense','$budi',100000),('$expense','$citra',100000)",
            "INSERT INTO settlements (id, trip_id, from_user_id, to_user_id, amount_minor) VALUES ('$settlement','$trip','$budi','$ayu',50000)",
        )

        flyway("15").migrate()

        // The ids equal what the apps compute offline.
        val members = query("SELECT user_id::text, id::text, removed_at IS NOT NULL FROM trip_members WHERE trip_id = '$trip'") {
            Triple(it.getString(1), it.getString(2), it.getBoolean(3))
        }.associateBy { it.first }
        assertEquals(memberIdFor(trip, ayu), members.getValue(ayu).second)
        assertEquals(memberIdFor(trip, budi), members.getValue(budi).second)
        assertEquals(memberIdFor(trip, citra), members.getValue(citra).second)
        assertEquals(false, members.getValue(ayu).third)
        assertEquals(true, members.getValue(citra).third) // the person who had left is kept, but not a current member

        // Everything stays on the same person.
        assertEquals(listOf(memberIdFor(trip, ayu)), query("SELECT paid_by_member_id::text FROM expenses WHERE id = '$expense'") { it.getString(1) })
        assertEquals(
            setOf(memberIdFor(trip, ayu) to 200000L, memberIdFor(trip, citra) to 100000L),
            query("SELECT member_id::text, amount_minor FROM expense_payers WHERE expense_id = '$expense'") { it.getString(1) to it.getLong(2) }.toSet(),
        )
        assertEquals(
            setOf(memberIdFor(trip, ayu), memberIdFor(trip, budi), memberIdFor(trip, citra)),
            query("SELECT member_id::text FROM expense_splits WHERE expense_id = '$expense'") { it.getString(1) }.toSet(),
        )
        assertEquals(
            listOf(memberIdFor(trip, budi) to memberIdFor(trip, ayu)),
            query("SELECT from_member_id::text, to_member_id::text FROM settlements WHERE id = '$settlement'") { it.getString(1) to it.getString(2) },
        )
    }
}
