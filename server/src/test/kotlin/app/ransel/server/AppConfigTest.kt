package app.ransel.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun stagingRequiresSecretsLikeProduction() {
        for (env in listOf("production", "staging")) {
            assertFailsWith<IllegalStateException>(env) { AppConfig.fromEnv(mapOf("APP_ENV" to env)) }
        }
    }

    @Test
    fun developmentFallsBackToDevSecrets() {
        assertEquals(AppConfig.DEV_JWT_SECRET, AppConfig.fromEnv(emptyMap()).jwtSecret)
    }
}
