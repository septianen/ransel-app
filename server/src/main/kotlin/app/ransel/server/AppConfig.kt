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
    val appBaseUrl: String = "https://ransel.mietek.tech",
    val isProduction: Boolean = false,
    val passwordResetTtl: Duration = 30.minutes,
    /** false = POST /auth/register answers 403; accounts exist only through [seedUsers]. */
    val registrationEnabled: Boolean = true,
    /** Accounts created at startup when missing (never overwritten). Come from env, not from git. */
    val seedUsers: List<SeedUser> = emptyList(),
    /** Android App Links: `/.well-known/assetlinks.json`. Empty fingerprints = the file answers 404. */
    val androidPackage: String = "app.ransel",
    val androidCertSha256: List<String> = emptyList(),
    /** iOS Universal Links: `/.well-known/apple-app-site-association`, e.g. `ABCDE12345.app.ransel`. Empty = 404. */
    val iosAppIds: List<String> = emptyList(),
    /** Explore AI. Blank keys = the feature answers 503 AI_UNAVAILABLE; the rest of the API is unaffected. */
    val llmApiKey: String = "",
    val llmBaseUrl: String = "https://api.deepseek.com",
    val llmModel: String = "deepseek-chat",
    val placesApiKey: String = "",
    val aiCacheTtl: Duration = 3.days,
) {
    data class SeedUser(val email: String, val password: String, val displayName: String)

    companion object {
        /**
         * `SEED_USERS` = `email|password|name;email|password|name` (name optional). Passwords must not contain
         * `|` or `;`. A bad entry stops the server at startup; the message names the position, never the password.
         */
        fun parseSeedUsers(raw: String?): List<SeedUser> =
            raw.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, entry ->
                val parts = entry.split('|').map { it.trim() }
                require(parts.size in 2..3 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    "SEED_USERS entry ${index + 1} must look like email|password|name"
                }
                SeedUser(parts[0], parts[1], parts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "Test User")
            }

        private fun String?.csv() = this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        /**
         * `keytool -list` prints `SHA256: AA:BB:...`; Android wants only the colon-separated hex, upper-case.
         * Accept the pasted form so a copy-paste slip cannot silently break link verification.
         */
        fun normalizeSha256(raw: String): String =
            raw.trim().removePrefix("SHA256:").removePrefix("SHA-256:").removePrefix("sha256:").trim().uppercase()

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
                appBaseUrl = (env["APP_BASE_URL"] ?: "https://ransel.mietek.tech").trimEnd('/'),
                isProduction = production,
                registrationEnabled = env["REGISTRATION_ENABLED"]?.lowercase() != "false",
                llmApiKey = env["LLM_API_KEY"].orEmpty().trim(),
                llmBaseUrl = (env["LLM_BASE_URL"]?.takeIf { it.isNotBlank() } ?: "https://api.deepseek.com").trimEnd('/'),
                llmModel = env["LLM_MODEL"]?.takeIf { it.isNotBlank() } ?: "deepseek-chat",
                placesApiKey = env["PLACES_API_KEY"].orEmpty().trim(),
                androidPackage = env["ANDROID_PACKAGE"]?.takeIf { it.isNotBlank() } ?: "app.ransel",
                androidCertSha256 = env["ANDROID_CERT_SHA256"].csv().map(::normalizeSha256),
                iosAppIds = env["IOS_APP_IDS"].csv(),
                seedUsers = listOfNotNull(
                    env["SEED_USER_EMAIL"]?.takeIf { it.isNotBlank() }?.let { email ->
                        SeedUser(
                            email = email.trim(),
                            password = env["SEED_USER_PASSWORD"]?.takeIf { it.isNotBlank() }
                                ?: error("SEED_USER_PASSWORD is required when SEED_USER_EMAIL is set"),
                            displayName = env["SEED_USER_NAME"]?.takeIf { it.isNotBlank() } ?: "Test User",
                        )
                    },
                ) + parseSeedUsers(env["SEED_USERS"]),
            )
        }
    }
}
