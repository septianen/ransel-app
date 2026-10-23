package app.ransel.admin

import app.ransel.admin.data.ApiEnvironment
import app.ransel.admin.data.environmentsFor
import app.ransel.admin.data.refreshTokenKey
import app.ransel.admin.data.selected
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiEnvironmentTest {
    private val karung = environmentsFor("karung.mietek.tech", "https://karung.mietek.tech", "")

    @Test
    fun karungOffersProductionFirstThenStaging() {
        assertEquals(
            listOf(
                ApiEnvironment("production", "https://ranselapi.mietek.tech"),
                ApiEnvironment("staging", "https://ranselapi-dev.mietek.tech"),
            ),
            karung,
        )
    }

    @Test
    fun otherHostsHaveOneServer() {
        assertEquals(listOf(ApiEnvironment("development", "http://localhost:8080")), environmentsFor("localhost", "http://localhost:8082", ""))
        assertEquals(listOf(ApiEnvironment("production", "https://api.example")), environmentsFor("admin.example", "https://admin.example", "https://api.example"))
        assertEquals(listOf(ApiEnvironment("production", "https://admin.example")), environmentsFor("admin.example", "https://admin.example", ""))
    }

    @Test
    fun storedChoiceFallsBackToTheFirst() {
        assertEquals("staging", karung.selected("staging").id)
        assertEquals("production", karung.selected(null).id)
        assertEquals("production", karung.selected("development").id)
    }

    @Test
    fun refreshTokensAreKeptPerServer() {
        assertEquals("karung.refreshToken.production", refreshTokenKey(karung[0]))
        assertEquals("karung.refreshToken.staging", refreshTokenKey(karung[1]))
    }
}
