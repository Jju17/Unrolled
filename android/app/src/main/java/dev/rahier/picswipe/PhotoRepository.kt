package dev.rahier.picswipe

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Reads the user's image library via `MediaStore`. Constructor-injected
 * into [SwipeViewModel] so tests can swap in a fake implementation.
 *
 * Wraps `applicationContext` — never the Activity — so the repository
 * can live as long as the VM (which survives config changes) without
 * leaking the current Activity.
 */
class PhotoRepository(private val context: Context) {

    /**
     * Read-permission check. Owned by the repository rather than the
     * Activity so passing it as a callback into the VM doesn't capture
     * the Activity and cause a leak on config change.
     */
    fun hasReadPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries the list of buckets ("albums") that contain images, plus a
     * synthetic first entry [Album.ALL_PHOTOS_ID] covering the whole
     * library. MediaStore doesn't support SQL `DISTINCT` via
     * ContentResolver, so we dedupe + count in-memory.
     */
    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val collection = imagesCollection()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // Preserve "most recently added to bucket" order for the picker
        // by walking photos newest-first and keeping first-seen bucket.
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

        // Prepend synthetic "All Photos" entry.
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
    suspend fun loadPhotos(albumId: String? = null): List<PhotoAsset> = withContext(Dispatchers.IO) {
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
                    uri = ContentUris.withAppendedId(collection, id),
                    creationDate = if (dateMs > 0) Date(dateMs) else null
                )
            }
        }
        out
    }

    private fun imagesCollection() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
