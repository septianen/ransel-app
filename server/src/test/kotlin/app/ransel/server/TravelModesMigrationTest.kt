package app.ransel.server

import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelModesMigrationTest {
    private val cfg = TestDb.config()

    @Test
    fun v10AddsTheTravelModesTableAndThePairIsUnique() {
        val name = "tm_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val adminUrl = databaseUrl("postgres")
        val url = databaseUrl(name)
        DriverManager.getConnection(adminUrl, cfg.databaseUser, cfg.databasePassword).use { conn ->
            conn.createStatement().execute("CREATE DATABASE $name")
        }
        try {
            Flyway.configure().dataSource(url, cfg.databaseUser, cfg.databasePassword).load().migrate()
            DriverManager.getConnection(url, cfg.databaseUser, cfg.databasePassword).use { conn ->
                conn.createStatement().executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'travel_modes' ORDER BY ordinal_position",
                ).use { rs ->
                    val columns = buildList { while (rs.next()) add(rs.getString(1)) }
                    assertEquals(
                        listOf("id", "trip_id", "from_item_id", "to_item_id", "mode", "created_at", "updated_at", "deleted_at"),
                        columns,
                    )
                }
                conn.createStatement().executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'travel_modes'",
                ).use { rs ->
                    val indexes = buildSet { while (rs.next()) add(rs.getString(1)) }
                    assertTrue("travel_modes_pair" in indexes)
                    assertTrue("travel_modes_pkey" in indexes)
                }
            }
        } finally {
            DriverManager.getConnection(adminUrl, cfg.databaseUser, cfg.databasePassword).use { conn ->
                conn.createStatement().execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$name' AND pid <> pg_backend_pid()",
                )
                conn.createStatement().execute("DROP DATABASE IF EXISTS $name")
            }
        }
    }

    private fun databaseUrl(name: String): String {
        val url = cfg.databaseUrl
        val query = url.substringAfter('?', "")
        val bare = url.substringBefore('?')
        val base = bare.substringBeforeLast('/')
        return if (query.isEmpty()) "$base/$name" else "$base/$name?$query"
    }
}
