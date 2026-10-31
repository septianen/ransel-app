package app.ransel.server

import app.ransel.shared.model.AiBudgetMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
    /** GET /invites/{code} is public: requests per client address per minute. */
    val invitePreviewRateLimitPerMinute: Int = 30,
    /** Used to build links in emails (password reset). */
    val appBaseUrl: String = "https://ransel.mietek.tech",
    val isProduction: Boolean = false,
    val passwordResetTtl: Duration = 30.minutes,
    /** Link in the invite email when an admin creates an account (it sets the first password). */
    val inviteTtl: Duration = 7.days,
    /** How long the sign-up verification link works. */
    val emailVerificationTtl: Duration = 24.hours,
    /** Null = no provider: emails are only logged ([app.ransel.server.security.LoggingEmailSender]). */
    val smtp: Smtp? = null,
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
    val llmBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    val llmModel: String = "gemini-3.1-flash-lite",
    /** Used when the primary model answers 429/5xx. Blank = none. */
    val llmFallbackModel: String = "gemini-3.5-flash",
    /** Models Karung may pick for [llmModel] (`LLM_MODELS`, comma separated); always holds [llmModel]. */
    val llmModels: List<String> = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash"),
    val placesApiKey: String = "",
    /** Trip albums: Drive API v3 key that lists public folders. Blank = albums still save, the gallery answers 503 ALBUM_UNAVAILABLE. */
    val googleDriveApiKey: String = "",
    /** HMAC key for the signed place-photo links. Required in production, separate from [jwtSecret]. */
    val photoSigningSecret: String = DEV_PHOTO_SIGNING_SECRET,
    /** Where trip document files live (a Docker volume in production). */
    val documentsDir: String = "./data/documents",
    /** Trip covers and profile photos (B19.1); by default inside [documentsDir], so the same volume and backup hold them. */
    val imagesDir: String = "./data/documents/images",
    /** Photo proxy requests per client address per minute (a card stack loads several at once). */
    val photoRateLimitPerMinute: Int = 120,
    /** `GET /explore/suggestions` per user per hour (soft limit; it never uses the daily search quota). */
    val suggestionsRateLimitPerHour: Int = 60,
    val aiCacheTtl: Duration = 3.days,
    /** Explore credits (docs/TRD.md "Quota"). All tunable by env; the app reads them from `GET /credits`. */
    val creditSignupGrant: Int = 30,
    /** Free credits per WIB day. Do not accumulate. */
    val creditDailyFree: Int = 3,
    val creditCostSearch: Int = 3,
    val creditCostNextBatch: Int = 1,
    val creditCostPlaceDetail: Int = 0,
    /** One receipt photo read by Gemini: about Rp50 of tokens (Rp130 on the fallback model) against Rp550–667 per credit. */
    val creditCostReceipt: Int = 1,
    /** Top-up packs shown by `GET /credits/packages`. Buying is not possible until a payment provider is connected. */
    val creditPackages: List<CreditPack> = DEFAULT_CREDIT_PACKS,
    /** Push (FCM HTTP v1). Blank service account = pushes are only logged; the inbox works either way. */
    val fcmProjectId: String = "",
    val fcmServiceAccountJson: String = "",
    /** How often the outbox of pending pushes is sent. */
    val pushDispatchIntervalMillis: Long = 5_000,
    /** Packing reminder: the evening before the trip starts, at this hour (WIB). */
    val notifyPackingHour: Int = 18,
    /** Activity reminder: this many minutes before it starts (WIB). */
    val notifyActivityLeadMinutes: Int = 30,
    /** Voting: how long a round is open for votes. */
    val votingHours: Int = 24,
    /** Voting: a round nobody decided disappears this many days after it was created. */
    val roundExpiryDays: Int = 3,
    /** Voting: open (not decided, not expired) rounds a trip may have; one more search is 409 ROUND_LIMIT. */
    val maxOpenRounds: Int = 3,
    /** Voting: the "closes soon" reminder goes out this many hours before a round closes. */
    val notifyVoteClosingHours: Int = 3,
    /** Admin app "Karung": these accounts (lower-case emails) may call `/admin/...`. Empty = nobody. */
    val adminEmails: Set<String> = emptySet(),
    /** `production`, `staging` or `development`; shown in Karung so an admin knows which data they change. */
    val appEnv: String = "development",
    /** Karung's "send password reset"; defaults to on exactly when [smtp] is configured. */
    val adminPasswordResetEnabled: Boolean = false,
    /** Karung's cost estimates: list price per call to each paid service, in micro-USD. An estimate, not the invoice. */
    val providerPrices: ProviderPrices = ProviderPrices(),
    /** "Server dan database" on Karung's cost card: the VPS per month, in micro-USD. */
    val serverMonthlyCostMicroUsd: Long = 0,
    /** LLM + Places budget per month, in micro-USD; 0 = none. Karung can override it (`SettingsService`). */
    val aiMonthlyBudgetMicroUsd: Long = 0,
    /** Karung warns from this share of [aiMonthlyBudgetMicroUsd]: 80, 90 or 100. */
    val aiBudgetAlertPercent: Int = 80,
    /** What happens once the month's spend reaches [aiMonthlyBudgetMicroUsd] (`AI_BUDGET_MODE`). */
    val aiBudgetMode: AiBudgetMode = AiBudgetMode.KEEP_GOING,
    /** The deployed commit, shown on Karung's system page; blank = unknown. */
    val appVersion: String = "",
    /** Written by `deploy/backup-db.sh` after a good backup (an ISO instant); read for "Backup terakhir". */
    val backupMarkerFile: String = "./data/documents/.last-backup",
) {
    /**
     * Defaults: Gemini Flash-Lite with a search-sized prompt (about 2k tokens in, 6k out), Places API (New) Essentials
     * list prices. Text Search with an IDs-only field mask is free.
     */
    data class ProviderPrices(
        val llmCall: Long = 3_000,
        val placesFind: Long = 0,
        val placesDetails: Long = 5_000,
        val placesPhoto: Long = 7_000,
        val placesAutocomplete: Long = 2_830,
        val placesGeocode: Long = 5_000,
    )

    data class CreditPack(val id: String, val credits: Int, val priceMinor: Long, val currency: String = "IDR")

    data class SeedUser(val email: String, val password: String, val displayName: String)

    /** `SMTP_*` env. Port 587 = STARTTLS, 465 = implicit TLS. [from] = `EMAIL_FROM`. */
    data class Smtp(val host: String, val port: Int, val username: String, val password: String, val from: String)

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

        val DEFAULT_CREDIT_PACKS = listOf(
            CreditPack("small", 15, 10_000),
            CreditPack("medium", 40, 25_000),
            CreditPack("large", 100, 55_000),
        )

        /** `CREDIT_PACKAGES` = `id:credits:priceMinor;id:credits:priceMinor` (currency IDR). */
        fun parseCreditPacks(raw: String?): List<CreditPack> =
            raw.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, entry ->
                val parts = entry.split(':').map { it.trim() }
                val credits = parts.getOrNull(1)?.toIntOrNull()
                val price = parts.getOrNull(2)?.toLongOrNull()
                require(parts.size == 3 && parts[0].isNotEmpty() && credits != null && credits > 0 && price != null && price > 0) {
                    "CREDIT_PACKAGES entry ${index + 1} must look like id:credits:priceMinor"
                }
                CreditPack(parts[0], credits, price)
            }

        /**
         * The service-account JSON, either as is or base64-encoded (a safe single line for a `.env` file and a
         * GitHub secret). Blank stays blank.
         */
        fun serviceAccountJson(raw: String?): String {
            val value = raw.orEmpty().trim()
            if (value.isEmpty() || value.startsWith("{")) return value
            return String(java.util.Base64.getMimeDecoder().decode(value)).trim()
        }

        private fun Map<String, String>.microUsd(key: String): Long? = this[key]?.trim()?.toLongOrNull()?.coerceAtLeast(0)

        /** "24.60" (USD) as micro-USD; blank, negative or not a number = null. */
        private fun Map<String, String>.usdAsMicro(key: String): Long? =
            this[key]?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.movePointRight(6)?.toLong()

        private fun String?.csv() = this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        /**
         * `keytool -list` prints `SHA256: AA:BB:...`; Android wants only the colon-separated hex, upper-case.
         * Accept the pasted form so a copy-paste slip cannot silently break link verification.
         */
        fun normalizeSha256(raw: String): String =
            raw.trim().removePrefix("SHA256:").removePrefix("SHA-256:").removePrefix("sha256:").trim().uppercase()

        const val DEV_JWT_SECRET = "dev-only-secret-never-use-in-production-0123456789"

        private val DEPLOYED_ENVS = setOf("production", "staging")

        const val DEV_PHOTO_SIGNING_SECRET = "dev-only-photo-secret-never-use-in-production-0123456789"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            // Staging is deployed and reachable from the internet, so it gets the same secret checks as production.
            val production = env["APP_ENV"] in DEPLOYED_ENVS
            val secret = env["JWT_SECRET"]
                ?: if (production) error("JWT_SECRET is required when APP_ENV=production or staging") else DEV_JWT_SECRET
            if (production) check(secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
            val smtp = env["SMTP_HOST"]?.trim()?.takeIf { it.isNotEmpty() }?.let { host ->
                Smtp(
                    host = host,
                    port = env["SMTP_PORT"]?.toIntOrNull() ?: 587,
                    username = env["SMTP_USERNAME"].orEmpty().trim(),
                    password = env["SMTP_PASSWORD"].orEmpty(),
                    from = env["EMAIL_FROM"]?.takeIf { it.isNotBlank() } ?: "RANSEL <no-reply@mietek.tech>",
                )
            }
            val photoSecret = env["PHOTO_SIGNING_SECRET"]?.takeIf { it.isNotBlank() }
                ?: if (production) error("PHOTO_SIGNING_SECRET is required when APP_ENV=production or staging") else DEV_PHOTO_SIGNING_SECRET
            if (production) check(photoSecret.length >= 32) { "PHOTO_SIGNING_SECRET must be at least 32 characters" }
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/ransel",
                databaseUser = env["DATABASE_USER"] ?: "ransel",
                databasePassword = env["DATABASE_PASSWORD"] ?: "ransel",
                corsOrigins = (
                    env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: listOf("http://localhost:8080", "http://localhost:8081")
                    ) + env["ADMIN_APP_ORIGIN"].csv(),
                jwtSecret = secret,
                jwtIssuer = env["JWT_ISSUER"] ?: "ransel",
                appBaseUrl = (env["APP_BASE_URL"] ?: "https://ransel.mietek.tech").trimEnd('/'),
                isProduction = production,
                registrationEnabled = env["REGISTRATION_ENABLED"]?.lowercase() != "false",
                llmApiKey = env["LLM_API_KEY"].orEmpty().trim(),
                llmBaseUrl = (env["LLM_BASE_URL"]?.takeIf { it.isNotBlank() } ?: "https://generativelanguage.googleapis.com/v1beta").trimEnd('/'),
                llmModel = env["LLM_MODEL"]?.takeIf { it.isNotBlank() } ?: "gemini-3.1-flash-lite",
                llmFallbackModel = env["LLM_FALLBACK_MODEL"]?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash",
                llmModels = run {
                    val model = env["LLM_MODEL"]?.takeIf { it.isNotBlank() } ?: "gemini-3.1-flash-lite"
                    val fallback = env["LLM_FALLBACK_MODEL"]?.takeIf { it.isNotBlank() } ?: "gemini-3.5-flash"
                    (listOf(model) + env["LLM_MODELS"].csv().ifEmpty { listOf(fallback) }).distinct()
                },
                placesApiKey = env["PLACES_API_KEY"].orEmpty().trim(),
                googleDriveApiKey = env["GOOGLE_DRIVE_API_KEY"].orEmpty().trim(),
                photoSigningSecret = photoSecret,
                documentsDir = env["DOCUMENTS_DIR"]?.takeIf { it.isNotBlank() } ?: "./data/documents",
                imagesDir = env["IMAGES_DIR"]?.takeIf { it.isNotBlank() }
                    ?: ((env["DOCUMENTS_DIR"]?.takeIf { it.isNotBlank() } ?: "./data/documents").trimEnd('/') + "/images"),
                androidPackage = env["ANDROID_PACKAGE"]?.takeIf { it.isNotBlank() } ?: "app.ransel",
                androidCertSha256 = env["ANDROID_CERT_SHA256"].csv().map(::normalizeSha256),
                iosAppIds = env["IOS_APP_IDS"].csv(),
                creditSignupGrant = env["CREDIT_SIGNUP_GRANT"]?.toIntOrNull()?.coerceAtLeast(0) ?: 30,
                creditDailyFree = env["CREDIT_DAILY_FREE"]?.toIntOrNull()?.coerceAtLeast(0) ?: 3,
                creditCostSearch = env["CREDIT_COST_SEARCH"]?.toIntOrNull()?.coerceAtLeast(0) ?: 3,
                creditCostNextBatch = env["CREDIT_COST_NEXT_BATCH"]?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                creditCostPlaceDetail = env["CREDIT_COST_PLACE_DETAIL"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                creditCostReceipt = env["CREDIT_COST_RECEIPT"]?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                creditPackages = parseCreditPacks(env["CREDIT_PACKAGES"]).ifEmpty { DEFAULT_CREDIT_PACKS },
                fcmProjectId = env["FCM_PROJECT_ID"].orEmpty().trim(),
                fcmServiceAccountJson = serviceAccountJson(env["FCM_SERVICE_ACCOUNT_JSON"]),
                notifyPackingHour = env["NOTIFY_PACKING_HOUR"]?.toIntOrNull()?.coerceIn(0, 23) ?: 18,
                notifyActivityLeadMinutes = env["NOTIFY_ACTIVITY_LEAD_MINUTES"]?.toIntOrNull()?.coerceIn(1, 240) ?: 30,
                votingHours = env["VOTING_HOURS"]?.toIntOrNull()?.coerceIn(1, 168) ?: 24,
                roundExpiryDays = env["ROUND_EXPIRY_DAYS"]?.toIntOrNull()?.coerceIn(1, 60) ?: 3,
                maxOpenRounds = env["MAX_OPEN_ROUNDS"]?.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                notifyVoteClosingHours = env["NOTIFY_VOTE_CLOSING_HOURS"]?.toIntOrNull()?.coerceIn(1, 23) ?: 3,
                providerPrices = ProviderPrices(
                    llmCall = env.microUsd("COST_LLM_CALL_MICRO_USD") ?: 3_000,
                    placesFind = env.microUsd("COST_PLACES_FIND_MICRO_USD") ?: 0,
                    placesDetails = env.microUsd("COST_PLACES_DETAILS_MICRO_USD") ?: 5_000,
                    placesPhoto = env.microUsd("COST_PLACES_PHOTO_MICRO_USD") ?: 7_000,
                    placesAutocomplete = env.microUsd("COST_PLACES_AUTOCOMPLETE_MICRO_USD") ?: 2_830,
                    placesGeocode = env.microUsd("COST_PLACES_GEOCODE_MICRO_USD") ?: 5_000,
                ),
                serverMonthlyCostMicroUsd = env.usdAsMicro("SERVER_MONTHLY_COST_USD") ?: 0,
                aiMonthlyBudgetMicroUsd = env.usdAsMicro("AI_MONTHLY_BUDGET_USD") ?: 0,
                aiBudgetAlertPercent = env["AI_BUDGET_ALERT_PERCENT"]?.toIntOrNull()?.takeIf { it in setOf(80, 90, 100) } ?: 80,
                aiBudgetMode = env["AI_BUDGET_MODE"]?.let { v -> AiBudgetMode.entries.firstOrNull { it.name == v.trim() } } ?: AiBudgetMode.KEEP_GOING,
                suggestionsRateLimitPerHour = env["SUGGESTIONS_RATE_LIMIT_PER_HOUR"]?.toIntOrNull()?.coerceIn(1, 1_000) ?: 60,
                appVersion = env["APP_VERSION"].orEmpty().trim(),
                backupMarkerFile = env["BACKUP_MARKER_FILE"]?.takeIf { it.isNotBlank() }
                    ?: ((env["DOCUMENTS_DIR"]?.takeIf { it.isNotBlank() } ?: "./data/documents").trimEnd('/') + "/.last-backup"),
                adminEmails = env["ADMIN_EMAILS"].csv().map { it.lowercase() }.toSet(),
                appEnv = env["APP_ENV"]?.takeIf { it.isNotBlank() } ?: "development",
                smtp = smtp,
                // On by default once email can actually be sent; ADMIN_PASSWORD_RESET_ENABLED overrides either way.
                adminPasswordResetEnabled = env["ADMIN_PASSWORD_RESET_ENABLED"]?.takeIf { it.isNotBlank() }?.lowercase()?.let { it == "true" } ?: (smtp != null),
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
