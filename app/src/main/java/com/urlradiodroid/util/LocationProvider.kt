package com.urlradiodroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Resolves a single, one-shot device location fix for the "near me" Discover search mode — no
 * continuous updates requested, nothing persisted beyond the single [getCurrentLocation] call.
 * Wraps the plain [LocationManager] rather than play-services-location's
 * FusedLocationProviderClient, to avoid pulling in a new Gradle/Google-Play-Services dependency —
 * matches this project's existing low-ceremony style (RadioBrowserApi/StreamValidator build their
 * own OkHttpClient rather than an injected one). [locationManager] is a constructor param purely
 * so tests can substitute a Robolectric ShadowLocationManager-backed instance.
 */
class LocationProvider(
    private val context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns the best available fix within [timeoutMs], or null if permission is missing, no
     * location provider is enabled, or no fix arrives in time. Prefers a recent cached fix
     * ([LocationManager.getLastKnownLocation]) over waiting for a fresh GPS/network fix, since
     * "near me" station search doesn't need live-tracking accuracy.
     */
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Location? {
        if (!hasPermission()) return null
        lastKnownLocation()?.let { return it }
        return withTimeoutOrNull(timeoutMs) { requestSingleUpdate() }
    }

    private fun lastKnownLocation(): Location? =
        enabledProviders().firstNotNullOfOrNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
        }

    private suspend fun requestSingleUpdate(): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = enabledProviders().firstOrNull()
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = SingleFixListener(continuation)
            try {
                // Explicit main looper: this suspend fun may be resumed off a Looper-less
                // dispatcher thread, and requestSingleUpdate(provider, listener, null) requires
                // the calling thread itself to have a prepared Looper.
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }

    private fun enabledProviders(): List<String> =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter {
                try {
                    locationManager.isProviderEnabled(it)
                } catch (e: IllegalArgumentException) {
                    false
                }
            }

    private class SingleFixListener(
        private val continuation: CancellableContinuation<Location?>,
    ) : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (continuation.isActive) continuation.resume(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?,
        ) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
