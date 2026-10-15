package app.ransel.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** Server configuration, read from environment variables (never from git). */
data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val corsOrigins: List<String>,
    val jwtSecret: String = DEV_JWT_SECRET,
    val jwtIssuer: String = "ransel",
    val accessTokenTtl: Duration = 15.minutes,
    val refreshTokenTtl: Duration = 60.days,
    /** bcrypt work factor; tests lower it for speed. */
    val bcryptCost: Int = 12,
    /** Login/register attempts per client address per minute. */
    val authRateLimitPerMinute: Int = 10,
    /** Used to build links in emails (password reset). */
    val appBaseUrl: String = "https://ransel.app",
    val isProduction: Boolean = false,
    val passwordResetTtl: Duration = 30.minutes,
) {
    companion object {
        const val DEV_JWT_SECRET = "dev-only-secret-never-use-in-production-0123456789"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val production = env["APP_ENV"] == "production"
            val secret = env["JWT_SECRET"]
                ?: if (production) error("JWT_SECRET is required when APP_ENV=production") else DEV_JWT_SECRET
            if (production) check(secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/ransel",
                databaseUser = env["DATABASE_USER"] ?: "ransel",
                databasePassword = env["DATABASE_PASSWORD"] ?: "ransel",
                corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: listOf("http://localhost:8080", "http://localhost:8081"),
                jwtSecret = secret,
                jwtIssuer = env["JWT_ISSUER"] ?: "ransel",
                appBaseUrl = (env["APP_BASE_URL"] ?: "https://ransel.app").trimEnd('/'),
                isProduction = production,
            )
        }
    }
}
