package app.ransel.server

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * V5 replaces the force toggle. A minimum that was dismissable (force false, or no force row: the env default was
 * false) becomes the latest and the hard floor is cleared. A forced minimum stays the hard floor.
 */
class AndroidUpdateMigrationTest {
    private val cfg = TestDb.config()

    @Test
    fun aDismissableMinimumBecomesTheLatestAndAForcedOneStays() {
        val soft = migrated(min = "1.4.0", force = "false", iosMin = "9.0.0")
        assertEquals("", soft["ANDROID_MIN_VERSION"])
        assertEquals("1.4.0", soft["ANDROID_LATEST_VERSION"])
        assertFalse("ANDROID_FORCE_UPDATE" in soft)
        assertFalse("IOS_MIN_VERSION" in soft)

        val hard = migrated(min = "2.0.0", force = "true")
        assertEquals("2.0.0", hard["ANDROID_MIN_VERSION"])
        assertFalse("ANDROID_LATEST_VERSION" in hard)
        assertFalse("ANDROID_FORCE_UPDATE" in hard)

        val defaultOff = migrated(min = "1.1.0", force = null)
        assertEquals("", defaultOff["ANDROID_MIN_VERSION"])
        assertEquals("1.1.0", defaultOff["ANDROID_LATEST_VERSION"])

        val blank = migrated(min = "", force = "false")
        assertEquals("", blank["ANDROID_MIN_VERSION"])
        assertFalse("ANDROID_LATEST_VERSION" in blank)
    }

    private fun migrated(min: String, force: String?, iosMin: String? = null): Map<String, String> {
        val name = "upd_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val adminUrl = databaseUrl("postgres")
        val url = databaseUrl(name)
        DriverManager.getConnection(adminUrl, cfg.databaseUser, cfg.databasePassword).use { conn ->
            conn.createStatement().execute("CREATE DATABASE $name")
        }
        try {
            flyway(url).target(MigrationVersion.fromVersion("4")).load().migrate()
            DriverManager.getConnection(url, cfg.databaseUser, cfg.databasePassword).use { conn ->
                val userId = UUID.randomUUID()
                conn.prepareStatement("INSERT INTO users (id, display_name) VALUES (?, 'Migration')").use { st ->
                    st.setObject(1, userId)
                    st.execute()
                }
                fun put(key: String, value: String) {
                    conn.prepareStatement("INSERT INTO app_settings (key, value, updated_by) VALUES (?, ?, ?)").use { st ->
                        st.setString(1, key)
                        st.setString(2, value)
                        st.setObject(3, userId)
                        st.execute()
                    }
                }
                put("ANDROID_MIN_VERSION", min)
                if (force != null) put("ANDROID_FORCE_UPDATE", force)
                if (iosMin != null) put("IOS_MIN_VERSION", iosMin)
            }
            flyway(url).load().migrate()
            return DriverManager.getConnection(url, cfg.databaseUser, cfg.databasePassword).use { conn ->
                conn.createStatement().executeQuery(
                    "SELECT key, value FROM app_settings WHERE key LIKE 'ANDROID%' OR key LIKE 'IOS%'",
                ).use { rs ->
                    buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) }
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

    private fun flyway(url: String) = Flyway.configure().dataSource(url, cfg.databaseUser, cfg.databasePassword)

    private fun databaseUrl(name: String): String {
        val url = cfg.databaseUrl
        val query = url.substringAfter('?', "")
        val bare = url.substringBefore('?')
        val withName = bare.substringBeforeLast('/') + "/" + name
        return if (query.isEmpty()) withName else "$withName?$query"
    }
}
