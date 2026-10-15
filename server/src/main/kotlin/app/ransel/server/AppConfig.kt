package app.ransel.server

/** Server configuration, read from environment variables (never from git). */
data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val corsOrigins: List<String>,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = AppConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/ransel",
            databaseUser = env["DATABASE_USER"] ?: "ransel",
            databasePassword = env["DATABASE_PASSWORD"] ?: "ransel",
            corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("http://localhost:8080", "http://localhost:8081"),
        )
    }
}
