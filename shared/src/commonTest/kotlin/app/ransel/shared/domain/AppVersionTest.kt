package app.ransel.shared.domain

import app.ransel.shared.model.AppUpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test fun versionsCompareNumberByNumber() {
        assertEquals(true, compareAppVersions("0.9.0", "0.10.0")!! < 0)
        assertEquals(true, compareAppVersions("1.0.0", "0.99.99")!! > 0)
        assertEquals(0, compareAppVersions("1.2.3", "1.2.3"))
    }

    @Test fun aDebugSuffixAndMissingPartsAreTolerated() {
        assertEquals(0, compareAppVersions("0.1.0-debug", "0.1.0"))
        assertEquals(0, compareAppVersions("2.1", "2.1.0"))
        assertNull(compareAppVersions("dev", "1.0.0"))
    }

    @Test fun onlyAWholeVersionIsAcceptedForTheMinimum() {
        assertTrue(isValidAppVersion("10.20.30"))
        assertFalse(isValidAppVersion("1.2"))
        assertFalse(isValidAppVersion("1.2.3-beta"))
        assertFalse(isValidAppVersion(""))
    }

    @Test fun statusFollowsTheMinimumAndTheForceFlag() {
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("0.3.0", "0.3.0", force = true))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("0.4.0", "0.3.0", force = true))
        assertEquals(AppUpdateStatus.OPTIONAL, appUpdateStatus("0.1.0", "0.3.0", force = false))
        assertEquals(AppUpdateStatus.REQUIRED, appUpdateStatus("0.1.0", "0.3.0", force = true))
    }

    @Test fun aBlankMinimumOrAnUnreadableVersionNeverPrompts() {
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("0.1.0", "", force = true))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("dev", "0.3.0", force = true))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("0.1.0", "soon", force = true))
    }
}
