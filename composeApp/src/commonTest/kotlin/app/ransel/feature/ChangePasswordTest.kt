package app.ransel.feature

import app.ransel.feature.account.PasswordFieldError
import app.ransel.feature.account.ChangePasswordFormProblem
import app.ransel.feature.account.ChangePasswordServerError
import app.ransel.feature.account.changePasswordFailure
import app.ransel.feature.account.clientChangePasswordErrors
import app.ransel.shared.api.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChangePasswordTest {
    @Test
    fun emptyFieldsAreReportedPerBox() {
        val errors = clientChangePasswordErrors("", "", "")
        assertEquals(PasswordFieldError.EMPTY, errors.current)
        assertEquals(PasswordFieldError.EMPTY, errors.newPassword)
        assertEquals(PasswordFieldError.EMPTY, errors.confirm)
    }

    @Test
    fun mismatchSameAndWeakFollowTheRegisterRule() {
        val mismatch = clientChangePasswordErrors("OldPassw0rd", "NewPassw0rd", "OtherPass1")
        assertEquals(null, mismatch.current)
        assertEquals(null, mismatch.newPassword)
        assertEquals(PasswordFieldError.MISMATCH, mismatch.confirm)

        val same = clientChangePasswordErrors("OldPassw0rd", "OldPassw0rd", "OldPassw0rd")
        assertEquals(PasswordFieldError.SAME, same.newPassword)
        assertEquals(null, same.confirm)

        // shorter than 8, or missing a letter/digit: the shared checkPassword rule, not a new one
        val weak = clientChangePasswordErrors("OldPassw0rd", "short", "short")
        assertEquals(PasswordFieldError.WEAK, weak.newPassword)
        val noDigit = clientChangePasswordErrors("OldPassw0rd", "longpassword", "longpassword")
        assertEquals(PasswordFieldError.WEAK, noDigit.newPassword)
        assertFalse(clientChangePasswordErrors("OldPassw0rd", "NewPassw0rd", "NewPassw0rd").any)
    }

    @Test
    fun serverCodesMapOntoTheFields() {
        assertEquals(ChangePasswordServerError.CurrentWrong, changePasswordFailure(ErrorCode.CURRENT_PASSWORD_WRONG, null))
        assertEquals(
            ChangePasswordServerError.Weak,
            changePasswordFailure(ErrorCode.VALIDATION_FAILED, mapOf("newPassword" to "WEAK_PASSWORD")),
        )
        assertEquals(
            ChangePasswordServerError.Weak,
            changePasswordFailure(ErrorCode.VALIDATION_FAILED, mapOf("newPassword" to "TOO_LONG")),
        )
        assertEquals(
            ChangePasswordServerError.Same,
            changePasswordFailure(ErrorCode.VALIDATION_FAILED, mapOf("newPassword" to "SAME_AS_CURRENT")),
        )
        assertEquals(
            ChangePasswordServerError.Form(ChangePasswordFormProblem.CONNECTION),
            changePasswordFailure(ErrorCode.NETWORK_UNAVAILABLE, null),
        )
    }
}
