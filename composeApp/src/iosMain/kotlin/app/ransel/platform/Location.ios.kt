package app.ransel.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ransel.shared.api.ErrorCode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject

/**
 * When-in-use permission and one reduced-accuracy fix through CLLocationManager. Not verified on a device (no macOS
 * host here); the Info.plist needs NSLocationWhenInUseUsageDescription.
 */
@Composable
actual fun rememberLocationProvider(): LocationProvider = remember {
    LocationProvider(
        request = { SingleFix().get() },
        openSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class SingleFix : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private val result = CompletableDeferred<LocationFix>()

    suspend fun get(): LocationFix {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> return LocationFix.Failed(ErrorCode.LOCATION_DENIED)
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            else -> manager.requestLocation()
        }
        return withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) { result.await() } ?: LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> result.complete(LocationFix.Failed(ErrorCode.LOCATION_DENIED))
            else -> Unit
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        location.coordinate.useContents { result.complete(LocationFix.Found(latitude, longitude)) }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        result.complete(LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT))
    }
}
