package dev.rahier.unrolled

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/** GPS coordinates extracted from a photo's EXIF / MediaStore metadata. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Abstraction over the device photo library. The real implementation
 * ([MediaStorePhotoRepository]) queries Android's `MediaStore`. Tests
 * supply a fake implementation via the [SwipeViewModel.Factory] to
 * drive fixtures and failure modes.
 */
interface PhotoRepository {
    fun hasReadPermission(): Boolean
    /** `true` on API 34+ when the user granted `READ_MEDIA_VISUAL_USER_SELECTED`
     *  but NOT `READ_MEDIA_IMAGES` — i.e. they picked a subset of photos
     *  through the system picker. The UI branches on this to offer a
     *  "change selection" affordance when no photos are visible. */
    fun hasPartialPhotoAccess(): Boolean
    suspend fun loadAlbums(): List<Album>
    suspend fun loadPhotos(albumId: String? = null): List<PhotoAsset>
    /**
     * Lazily reads EXIF GPS for a single photo. Returns `null` when the
     * photo has no location, when ACCESS_MEDIA_LOCATION wasn't granted,
     * or when the file can't be opened. Callers should treat absence as
     * the common case, not an error.
     */
    suspend fun loadLocation(photoId: Long): GeoPoint?
}

/**
 * MediaStore-backed [PhotoRepository]. Wraps `applicationContext` only —
 * never the Activity — so the repository can live as long as the VM
 * (which survives config changes) without leaking the current Activity.
 */
class MediaStorePhotoRepository(private val context: Context) : PhotoRepository {

    override fun hasReadPermission(): Boolean {
        // API 34+ : accept EITHER the full `READ_MEDIA_IMAGES` grant OR
        //          the partial `READ_MEDIA_VISUAL_USER_SELECTED` grant.
        //          MediaStore naturally filters the query result to
        //          whatever the app is actually allowed to see, so the
        //          downstream code doesn't need to branch.
        // API 33  : only READ_MEDIA_IMAGES exists.
        // API ≤32 : legacy READ_EXTERNAL_STORAGE.
        val perms = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.any {
            ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    override fun hasPartialPhotoAccess(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val visual = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        val full = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        return visual && !full
    }

    /**
     * Queries the list of buckets ("albums") that contain images, plus a
     * synthetic first entry [Album.ALL_PHOTOS_ID] covering the whole
     * library. MediaStore doesn't support SQL `DISTINCT` via
     * ContentResolver, so we dedupe + count in-memory.
     */
    override suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val collection = imagesCollection()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val ordered = linkedMapOf<String, Pair<String, Int>>()
        var total = 0
        context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                total++
                val bucketId = cursor.getLong(idCol).toString()
                val bucketName = if (!cursor.isNull(nameCol)) cursor.getString(nameCol) else null
                val existing = ordered[bucketId]
                ordered[bucketId] = Pair(
                    existing?.first ?: bucketName ?: "Unknown",
                    (existing?.second ?: 0) + 1
                )
            }
        }

        val bucketAlbums = ordered.map { (id, value) ->
            Album(id = id, name = value.first, count = value.second, icon = "📁")
        }

        listOf(
            Album(
                id = Album.ALL_PHOTOS_ID,
                name = "All Photos",
                count = total,
                icon = "🕐"
            )
        ) + bucketAlbums
    }

    /**
     * Queries photos, newest first. If [albumId] is `null` or
     * [Album.ALL_PHOTOS_ID], returns all images; otherwise filters by
     * MediaStore bucket id.
     */
    override suspend fun loadPhotos(albumId: String?): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val collection = imagesCollection()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val selection: String?
        val selectionArgs: Array<String>?
        if (albumId != null && albumId != Album.ALL_PHOTOS_ID) {
            selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            selectionArgs = arrayOf(albumId)
        } else {
            selection = null
            selectionArgs = null
        }

        val out = mutableListOf<PhotoAsset>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val takenMs = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else 0L
                val addedSec = cursor.getLong(addedCol)
                val dateMs = if (takenMs > 0) takenMs else addedSec * 1000L
                out += PhotoAsset(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    creationDate = if (dateMs > 0) Date(dateMs) else null
                )
            }
        }
        out
    }

    /**
     * API 29+ : EXIF read through `MediaStore.setRequireOriginal()` which
     *           requires the `ACCESS_MEDIA_LOCATION` runtime permission.
     *           Throws `SecurityException` if the perm isn't granted —
     *           we swallow it and return null so the caller's UI hides
     *           the location strip silently.
     * API ≤28 : the legacy `LATITUDE`/`LONGITUDE` columns aren't redacted
     *           and can be read via a normal cursor query.
     */
    override suspend fun loadLocation(photoId: Long): GeoPoint? = withContext(Dispatchers.IO) {
        val collection = imagesCollection()
        val uri = ContentUris.withAppendedId(collection, photoId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val originalUri = MediaStore.setRequireOriginal(uri)
                context.contentResolver.openInputStream(originalUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val coords = exif.latLong ?: return@use null
                    GeoPoint(latitude = coords[0], longitude = coords[1])
                }
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            val projection = arrayOf(
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE
            )
            context.contentResolver.query(
                uri, projection, null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val lat = cursor.getDouble(0)
                val lng = cursor.getDouble(1)
                if (lat == 0.0 && lng == 0.0) null
                else GeoPoint(latitude = lat, longitude = lng)
            }
        }
    }

    private fun imagesCollection() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
