package app.ransel.server

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun healthReturnsOk() = apiTest {
        val response = get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"data":{"status":"ok"}}""", response.bodyAsText())
    }
}
