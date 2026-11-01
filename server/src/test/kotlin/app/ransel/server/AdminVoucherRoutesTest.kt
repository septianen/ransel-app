package app.ransel.server

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminVoucherRoutesTest {
    private val adminEmail = "admin@example.com"
    private val config = TestDb.config().copy(adminEmails = setOf(adminEmail))

    private fun Api.signIn(user: Session) {
        token = user.token
        refreshToken = user.refreshToken
        userId = user.userId
    }

    private suspend fun Api.me() = Session(token, refreshToken, userId)

    private suspend fun Api.create(body: String = """{"credits":15,"maxUses":2,"validDays":30,"note":" Promo "}""") = post("/admin/vouchers", body)

    private suspend fun io.ktor.client.statement.HttpResponse.field(name: String) = data().jsonObject.getValue(name).jsonPrimitive.content

    @Test
    fun aPromoCodeWorksForEachUserOnceThenIsUsedUp() = apiTest(config) {
        val first = me()
        signUp(email = adminEmail)
        val admin = me()
        val created = create()
        assertEquals(HttpStatusCode.Created, created.status)
        val code = created.field("code")
        assertTrue(Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}").matches(code), code)
        assertEquals("ACTIVE", created.field("status"))
        assertEquals("Promo", created.field("note"))

        signIn(first)
        assertEquals(HttpStatusCode.OK, post("/credits/redeem", """{"code":"$code"}""").status)
        assertEquals("VOUCHER_USED", post("/credits/redeem", """{"code":"$code"}""").errorCode())
        signUp()
        assertEquals(HttpStatusCode.OK, post("/credits/redeem", """{"code":"$code"}""").status)

        signIn(admin)
        val row = get("/admin/vouchers").data().jsonArray.single().jsonObject
        assertEquals("2", row.getValue("usedCount").jsonPrimitive.content)
        assertEquals("USED_UP", row.getValue("status").jsonPrimitive.content)
        val audit = get("/admin/audit?targetId=$code&action=VOUCHER_CREATE").data().jsonArray
        assertEquals("VOUCHER", audit.single().jsonObject.getValue("targetType").jsonPrimitive.content)
    }

    @Test
    fun aRevokedCodeCannotBeRedeemedAndRevokingTwiceLogsOnce() = apiTest(config) {
        val user = me()
        signUp(email = adminEmail)
        val admin = me()
        val code = create().field("code")
        assertEquals("REVOKED", post("/admin/vouchers/$code/revoke").field("status"))
        assertEquals("REVOKED", post("/admin/vouchers/$code/revoke").field("status"))
        assertEquals(1, get("/admin/audit?targetId=$code&action=VOUCHER_REVOKE").data().jsonArray.size)

        signIn(user)
        assertEquals("VOUCHER_INVALID", post("/credits/redeem", """{"code":"$code"}""").errorCode())
        signIn(admin)
        assertEquals("VOUCHER_INVALID", post("/admin/vouchers/NOPE/revoke").errorCode())
    }

    @Test
    fun outOfRangeNumbersAreRejected() = apiTest(config) {
        signUp(email = adminEmail)
        val bad = create("""{"credits":0,"maxUses":100001,"validDays":3651}""")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        val details = bad.jsonBody().getValue("error").jsonObject.getValue("details").jsonObject
        assertEquals(setOf("credits", "maxUses", "validDays"), details.keys)
    }
}
