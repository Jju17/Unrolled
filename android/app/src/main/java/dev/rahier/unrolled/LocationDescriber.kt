package dev.rahier.unrolled

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wraps [PhotoRepository.loadLocation] + [Geocoder] to resolve a single
 * `"Locality, Country"` string for a given photo, with an in-memory
 * cache keyed on the photo id. Designed for the photo card overlay.
 *
 * - The Geocoder is rate-limited and best-effort. We swallow every
 *   failure (no service, throttled, no result) and cache the absent
 *   value so recompositions don't retry.
 * - The cache lives for the lifetime of the singleton instance held
 *   by the Activity: restarting the app clears it, which is fine.
 */
class LocationDescriber(
    private val context: Context,
    private val photoRepository: PhotoRepository
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Long, String?>()

    suspend fun placeName(photoId: Long): String? {
        mutex.withLock { cache[photoId] }?.let { return it }
        val resolved = resolve(photoId)
        mutex.withLock { cache[photoId] = resolved }
        return resolved
    }

    private suspend fun resolve(photoId: Long): String? {
        val coords = photoRepository.loadLocation(photoId) ?: return null
        return reverseGeocode(coords.latitude, coords.longitude)
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull()?.let(::format))
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.let(::format)
                }
            }.getOrNull()
        }

    private fun format(address: android.location.Address): String? {
        val locality = address.locality ?: address.subAdminArea
        val country = address.countryName
        return when {
            locality != null && country != null -> "$locality, $country"
            locality != null -> locality
            country != null -> country
            else -> address.featureName
        }
    }
}

/**
 * Composition-scoped accessor for the shared [LocationDescriber].
 * `null` when location resolution isn't available (e.g. previews,
 * unit-test composables): callers must treat that as "no location".
 */
val LocalLocationDescriber = staticCompositionLocalOf<LocationDescriber?> { null }
