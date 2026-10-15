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
    /** false = POST /auth/register answers 403; accounts exist only through [seedUser]. */
    val registrationEnabled: Boolean = true,
    /** Account created at startup when missing (never overwritten). Comes from env, not from git. */
    val seedUser: SeedUser? = null,
    /** Android App Links: `/.well-known/assetlinks.json`. Empty fingerprints = the file answers 404. */
    val androidPackage: String = "app.ransel",
    val androidCertSha256: List<String> = emptyList(),
    /** iOS Universal Links: `/.well-known/apple-app-site-association`, e.g. `ABCDE12345.app.ransel`. Empty = 404. */
    val iosAppIds: List<String> = emptyList(),
) {
    data class SeedUser(val email: String, val password: String, val displayName: String)

    companion object {
        private fun String?.csv() = this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

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
                registrationEnabled = env["REGISTRATION_ENABLED"]?.lowercase() != "false",
                androidPackage = env["ANDROID_PACKAGE"]?.takeIf { it.isNotBlank() } ?: "app.ransel",
                androidCertSha256 = env["ANDROID_CERT_SHA256"].csv(),
                iosAppIds = env["IOS_APP_IDS"].csv(),
                seedUser = env["SEED_USER_EMAIL"]?.takeIf { it.isNotBlank() }?.let { email ->
                    SeedUser(
                        email = email.trim(),
                        password = env["SEED_USER_PASSWORD"]?.takeIf { it.isNotBlank() }
                            ?: error("SEED_USER_PASSWORD is required when SEED_USER_EMAIL is set"),
                        displayName = env["SEED_USER_NAME"]?.takeIf { it.isNotBlank() } ?: "Test User",
                    )
                },
            )
        }
    }
}
