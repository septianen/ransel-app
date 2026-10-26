package app.ransel.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.ransel.shared.api.ErrorCode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Coarse location (city level is all the suggestions need), balanced power, one fix. */
@Composable
actual fun rememberLocationProvider(): LocationProvider {
    val context = LocalContext.current
    val answer = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        answer[0]?.complete(granted)
    }
    return remember(context, launcher) {
        LocationProvider(
            request = {
                val granted = hasPermission(context) || CompletableDeferred<Boolean>().let { pending ->
                    answer[0] = pending
                    launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    pending.await()
                }
                if (granted) currentFix(context) else LocationFix.Failed(ErrorCode.LOCATION_DENIED)
            },
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

private fun hasPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // checked by the caller
private suspend fun currentFix(context: Context): LocationFix = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
    suspendCancellableCoroutine { cont ->
        val cancel = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { location ->
                cont.resume(location?.let { LocationFix.Found(it.latitude, it.longitude) } ?: LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT))
            }
            .addOnFailureListener { cont.resume(LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT)) }
        cont.invokeOnCancellation { cancel.cancel() }
    }
} ?: LocationFix.Failed(ErrorCode.LOCATION_TIMEOUT)
