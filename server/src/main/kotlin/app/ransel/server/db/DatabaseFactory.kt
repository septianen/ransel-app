package app.ransel.server.db

import app.ransel.server.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

object DatabaseFactory {
    /** Connects, runs every Flyway migration (V1..V4) and returns the pool. */
    fun connect(config: AppConfig): DataSource {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                maximumPoolSize = 10
            },
        )
        Flyway.configure().dataSource(pool).load().migrate()
        Database.connect(pool)
        return pool
    }
}
