package app.ransel.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ransel.shared.api.ErrorCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** navigator.geolocation; the browser asks for the permission itself. Error code 1 = permission denied. */
@JsFun(
    """(ok, fail) => {
        if (!navigator.geolocation) { fail(2); return; }
        navigator.geolocation.getCurrentPosition(
            p => ok(p.coords.latitude, p.coords.longitude),
            e => fail(e.code),
            { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
    }""",
)
private external fun currentPosition(ok: (Double, Double) -> Unit, fail: (Int) -> Unit)

@Composable
actual fun rememberLocationProvider(): LocationProvider = remember {
    LocationProvider(
        request = {
            val result = CompletableDeferred<LocationFix>()
            currentPosition(
                ok = { lat, lng -> result.complete(LocationFix.Found(lat, lng)) },
                fail = { code -> result.complete(LocationFix.Failed(if (code == 1) ErrorCode.LOCATION_DENIED else ErrorCode.LOCATION_TIMEOUT)) },
            )
            withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS + 1_000) { result.await() } ?: LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT)
        },
        // a browser has no settings page to open from here
        openSettings = null,
    )
}
