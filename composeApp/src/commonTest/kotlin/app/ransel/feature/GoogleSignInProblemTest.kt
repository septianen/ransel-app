package app.ransel.feature

import app.ransel.feature.auth.AuthProblem
import app.ransel.feature.auth.googleProblemOf
import app.ransel.shared.api.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleSignInProblemTest {
    @Test
    fun googleFailuresMapToTheDesignedCopy() {
        assertEquals(AuthProblem.GOOGLE_FAILED, googleProblemOf(ErrorCode.GOOGLE_TOKEN_INVALID))
        assertEquals(AuthProblem.GOOGLE_FAILED, googleProblemOf(ErrorCode.INTERNAL_ERROR))
        assertEquals(AuthProblem.CONNECTION, googleProblemOf(ErrorCode.NETWORK_UNAVAILABLE))
        assertEquals(AuthProblem.REGISTRATION_CLOSED, googleProblemOf(ErrorCode.FORBIDDEN))
        assertEquals(AuthProblem.ACCOUNT_DISABLED, googleProblemOf(ErrorCode.ACCOUNT_DISABLED))
    }
}
