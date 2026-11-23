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

    @Test fun onlyAWholeVersionIsAcceptedForKarung() {
        assertTrue(isValidAppVersion("10.20.30"))
        assertFalse(isValidAppVersion("1.2"))
        assertFalse(isValidAppVersion("1.2.3-beta"))
        assertFalse(isValidAppVersion(""))
    }

    @Test fun belowTheMinimumIsRequiredEvenWhenLatestIsBlank() {
        assertEquals(AppUpdateStatus.REQUIRED, appUpdateStatus("1.0.0", "1.1.0", "1.2.0"))
        assertEquals(AppUpdateStatus.REQUIRED, appUpdateStatus("1.0.0", "1.1.0", ""))
        assertEquals(AppUpdateStatus.REQUIRED, appUpdateStatus("1.0.0-debug", "1.1.0", "1.2.0"))
    }

    @Test fun betweenMinimumAndLatestIsOptional() {
        assertEquals(AppUpdateStatus.OPTIONAL, appUpdateStatus("1.1.0", "1.1.0", "1.2.0"))
        assertEquals(AppUpdateStatus.OPTIONAL, appUpdateStatus("1.1.5", "1.1.0", "1.2.0"))
        assertEquals(AppUpdateStatus.OPTIONAL, appUpdateStatus("1.0.0", "", "1.2.0"))
    }

    @Test fun theLatestAndAnythingNewerStayQuiet() {
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.2.0", "1.1.0", "1.2.0"))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.3.0", "1.1.0", "1.2.0"))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.0.0", "", ""))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.2.0", "", "1.2.0"))
    }

    @Test fun aLatestThatIsNotNewerThanTheMinimumHasNoSoftWindow() {
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.2.0", "1.2.0", "1.2.0"))
        assertEquals(AppUpdateStatus.REQUIRED, appUpdateStatus("1.0.0", "1.2.0", "1.1.0"))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.3.0", "1.2.0", "1.1.0"))
    }

    @Test fun anUnreadableVersionNeverPrompts() {
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("dev", "1.0.0", "1.2.0"))
        // a minimum Karung would refuse is off, so a valid latest still offers
        assertEquals(AppUpdateStatus.OPTIONAL, appUpdateStatus("1.0.0", "soon", "1.2.0"))
        assertEquals(AppUpdateStatus.NONE, appUpdateStatus("1.0.0", "1.0.0", "soon"))
    }

    @Test fun snoozingHidesThatLatestUntilItChangesAndNeverHidesTheGate() {
        assertEquals(AppUpdateStatus.NONE, promptedUpdate(AppUpdateStatus.OPTIONAL, "1.2.0", snoozedLatest = "1.2.0"))
        assertEquals(AppUpdateStatus.OPTIONAL, promptedUpdate(AppUpdateStatus.OPTIONAL, "1.3.0", snoozedLatest = "1.2.0"))
        assertEquals(AppUpdateStatus.OPTIONAL, promptedUpdate(AppUpdateStatus.OPTIONAL, "1.2.0", snoozedLatest = null))
        assertEquals(AppUpdateStatus.REQUIRED, promptedUpdate(AppUpdateStatus.REQUIRED, "1.2.0", snoozedLatest = "1.2.0"))
    }

    @Test fun theHttpsStoreLinkPrefersKarungThenThePlayListing() {
        assertEquals(PLAY_STORE_HTTPS, playStoreHttps(null))
        assertEquals(PLAY_STORE_HTTPS, playStoreHttps("  "))
        assertEquals(PLAY_STORE_HTTPS, playStoreHttps("market://details?id=app.ransel"))
        assertEquals("https://play.google.com/store/apps/details?id=app.ransel.beta", playStoreHttps(" https://play.google.com/store/apps/details?id=app.ransel.beta "))
    }
}
