package dev.rahier.unrolled

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** GPS coordinates extracted from a photo's EXIF / MediaStore metadata. */
data class GeoPoint(val latitude: Double, val longitude: Double)

interface PhotoRepository {
    fun hasReadPermission(): Boolean
    /** `true` on API 34+ when only `READ_MEDIA_VISUAL_USER_SELECTED` is granted. */
    fun hasPartialPhotoAccess(): Boolean
    suspend fun loadPiles(nowMillis: Long, classifiedIds: Set<Long>, includeVideos: Boolean): List<Pile>
    suspend fun loadPhotos(selector: PileSelector, includeVideos: Boolean): List<PhotoAsset>
    /** Resolves specific items by MediaStore id; missing ids are absent. */
    suspend fun loadPhotosByIds(ids: List<Long>): List<PhotoAsset>
    /** 64-bit difference hashes of 9x8 grayscale thumbnails, keyed by id. */
    suspend fun loadSimilarityHashes(ids: List<Long>): Map<Long, Long>
    /** Lazily reads EXIF GPS; `null` is the common case, not an error. */
    suspend fun loadLocation(photoId: Long): GeoPoint?
}

private data class MediaRow(
    val id: Long,
    val dateMs: Long,
    val isVideo: Boolean,
    val bucketId: String?,
    val bucketName: String?,
    val path: String?,
    val uri: String
) {
    val isScreenshot: Boolean
        get() = bucketName.equals("Screenshots", ignoreCase = true) ||
            path?.contains("Screenshots", ignoreCase = true) == true
}

class MediaStorePhotoRepository(private val context: Context) : PhotoRepository {

    override fun hasReadPermission(): Boolean {
        val perms = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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

    override suspend fun loadPiles(nowMillis: Long, classifiedIds: Set<Long>, includeVideos: Boolean): List<Pile> =
        withContext(Dispatchers.IO) {
            val includeVideos = SharedPrefsSettingsRepository(context).load().includeVideos
            val rows = queryRows(mediaTypeSelection(includeVideos), null, DEFAULT_SORT)
            buildPiles(rows, nowMillis, classifiedIds, includeVideos)
        }

    private fun buildPiles(
        rows: List<MediaRow>,
        nowMillis: Long,
        classifiedIds: Set<Long>,
        includeVideos: Boolean
    ): List<Pile> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val yearAgo = today.minusMonths(12)

        class Bucket(val selector: PileSelector, val name: String? = null) {
            var total = 0
            var remaining = 0
            var keyUri: String? = null
            fun add(row: MediaRow) {
                total++
                if (row.id !in classifiedIds) remaining++
                if (keyUri == null) keyUri = row.uri
            }
            fun toPile() = Pile(selector, name, total, remaining, keyUri)
        }

        val all = Bucket(PileSelector.All)
        val onThisDay = Bucket(PileSelector.OnThisDay)
        val oneYearAgo = Bucket(PileSelector.OneYearAgo)
        val screenshots = Bucket(PileSelector.Screenshots)
        val videos = Bucket(PileSelector.Videos)
        val months = linkedMapOf<PileSelector.Month, Bucket>()
        val albums = linkedMapOf<String, Bucket>()

        for (row in rows) {
            all.add(row)
            if (row.isVideo) videos.add(row)
            if (row.isScreenshot) screenshots.add(row)
            val date = row.dateMs.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            if (date != null) {
                val monthSelector = PileSelector.Month(date.year, date.monthValue)
                months.getOrPut(monthSelector) { Bucket(monthSelector) }.add(row)
                if (date.year < today.year && date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth) {
                    onThisDay.add(row)
                }
                if (date.year == yearAgo.year && date.monthValue == yearAgo.monthValue) oneYearAgo.add(row)
            }
            val bucketId = row.bucketId
            if (bucketId != null) {
                albums.getOrPut(bucketId) {
                    Bucket(PileSelector.Album(bucketId), row.bucketName ?: context.getString(R.string.album_unknown))
                }.add(row)
            }
        }

        val currentMonth = PileSelector.Month(today.year, today.monthValue)
        val sessions = buildList {
            add(months[currentMonth]?.toPile() ?: Pile(currentMonth, null, 0, 0, null))
            add(onThisDay.toPile())
            add(oneYearAgo.toPile())
            add(screenshots.toPile())
            if (includeVideos) add(videos.toPile())
            add(all.toPile())
        }.filter { it.photoCount > 0 || it.selector == PileSelector.All }

        val monthPiles = months.values
            .map { it.toPile() }
            .filter { it.selector != currentMonth }
            .sortedByDescending { (it.selector as PileSelector.Month).let { m -> m.year * 100 + m.month } }
            .take(MONTH_PILE_LIMIT)

        return sessions + monthPiles + albums.values.map { it.toPile() }
    }

    override suspend fun loadPhotos(selector: PileSelector, includeVideos: Boolean): List<PhotoAsset> =
        withContext(Dispatchers.IO) {
            val typeClause = mediaTypeSelection(if (selector == PileSelector.Videos) true else includeVideos)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val rows = when (selector) {
                PileSelector.All, PileSelector.OnThisDay, PileSelector.OneYearAgo, PileSelector.Screenshots ->
                    queryRows(typeClause, null, DEFAULT_SORT)
                is PileSelector.Album ->
                    queryRows("$typeClause AND ${MediaStore.Images.ImageColumns.BUCKET_ID} = ?", arrayOf(selector.albumId), DEFAULT_SORT)
                is PileSelector.Month -> queryRows(typeClause, null, DEFAULT_SORT)
                PileSelector.Videos -> queryRows(videoOnlySelection(), null, DEFAULT_SORT)
            }
            val filtered = when (selector) {
                PileSelector.OnThisDay -> rows.filter { row ->
                    val date = row.localDate(zone) ?: return@filter false
                    date.year < today.year && date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth
                }
                PileSelector.OneYearAgo -> {
                    val target = today.minusMonths(12)
                    rows.filter { row ->
                        val date = row.localDate(zone) ?: return@filter false
                        date.year == target.year && date.monthValue == target.monthValue
                    }
                }
                is PileSelector.Month -> rows.filter { row ->
                    val date = row.localDate(zone) ?: return@filter false
                    date.year == selector.year && date.monthValue == selector.month
                }
                PileSelector.Screenshots -> rows.filter { it.isScreenshot }
                else -> rows
            }
            val ids = filtered.map { it.id }
            val sizes = querySizesAndDurations(ids)
            filtered.map { it.toAsset(sizes[it.id]) }
        }

    override suspend fun loadPhotosByIds(ids: List<Long>): List<PhotoAsset> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        ids.chunked(ID_QUERY_CHUNK).flatMap { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val rows = queryRows(
                "${MediaStore.Files.FileColumns._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            )
            val sizes = querySizesAndDurations(rows.map { it.id })
            rows.map { it.toAsset(sizes[it.id]) }
        }
    }

    override suspend fun loadSimilarityHashes(ids: List<Long>): Map<Long, Long> = withContext(Dispatchers.IO) {
        val out = HashMap<Long, Long>(ids.size)
        for (id in ids) {
            val uri = ContentUris.withAppendedId(imagesCollection(), id)
            val bitmap = runCatching { loadThumbnail(uri, id) }.getOrNull() ?: continue
            val small = bitmap.scale(SimilarityGrouper.HASH_WIDTH, SimilarityGrouper.HASH_HEIGHT, filter = true)
            val gray = IntArray(SimilarityGrouper.HASH_WIDTH * SimilarityGrouper.HASH_HEIGHT)
            for (y in 0 until SimilarityGrouper.HASH_HEIGHT) {
                for (x in 0 until SimilarityGrouper.HASH_WIDTH) {
                    val pixel = small[x, y]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    gray[y * SimilarityGrouper.HASH_WIDTH + x] = (r * 299 + g * 587 + b * 114) / 1000
                }
            }
            out[id] = SimilarityGrouper.dHash(gray)
            if (small !== bitmap) small.recycle()
            bitmap.recycle()
        }
        out
    }

    private fun loadThumbnail(uri: Uri, id: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_EDGE, THUMBNAIL_EDGE), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, id, MediaStore.Images.Thumbnails.MICRO_KIND, null
            )
        }

    override suspend fun loadLocation(photoId: Long): GeoPoint? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(imagesCollection(), photoId)
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
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val lat = cursor.getDouble(0)
                val lng = cursor.getDouble(1)
                if (lat == 0.0 && lng == 0.0) null else GeoPoint(latitude = lat, longitude = lng)
            }
        }
    }

    private fun queryRows(selection: String?, selectionArgs: Array<String>?, sort: String?): List<MediaRow> {
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Files.FileColumns.DATA)
            }
        }.toTypedArray()
        val out = mutableListOf<MediaRow>()
        context.contentResolver.query(filesCollection(), projection, selection, selectionArgs, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndex(projection.last())
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val takenMs = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else 0L
                val addedSec = cursor.getLong(addedCol)
                val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val collection = if (isVideo) videosCollection() else imagesCollection()
                out += MediaRow(
                    id = id,
                    dateMs = if (takenMs > 0) takenMs else addedSec * 1000L,
                    isVideo = isVideo,
                    bucketId = if (!cursor.isNull(bucketIdCol)) cursor.getLong(bucketIdCol).toString() else null,
                    bucketName = if (!cursor.isNull(bucketNameCol)) cursor.getString(bucketNameCol) else null,
                    path = if (pathCol >= 0 && !cursor.isNull(pathCol)) cursor.getString(pathCol) else null,
                    uri = ContentUris.withAppendedId(collection, id).toString()
                )
            }
        }
        return out
    }

    private fun querySizesAndDurations(ids: List<Long>): Map<Long, Pair<Long, Long?>> {
        if (ids.isEmpty()) return emptyMap()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Video.VideoColumns.DURATION
        )
        val out = HashMap<Long, Pair<Long, Long?>>(ids.size)
        ids.chunked(ID_QUERY_CHUNK).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                filesCollection(),
                projection,
                "${MediaStore.Files.FileColumns._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val size = if (!cursor.isNull(1)) cursor.getLong(1) else 0L
                    val duration = if (!cursor.isNull(2)) cursor.getLong(2).takeIf { it > 0 } else null
                    out[id] = size to duration
                }
            }
        }
        return out
    }

    private fun MediaRow.toAsset(extra: Pair<Long, Long?>?): PhotoAsset = PhotoAsset(
        id = id,
        uri = uri,
        creationDate = if (dateMs > 0) Date(dateMs) else null,
        sizeBytes = extra?.first ?: 0L,
        isVideo = isVideo,
        durationMs = if (isVideo) extra?.second else null
    )

    private fun MediaRow.localDate(zone: ZoneId): LocalDate? =
        dateMs.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    private fun mediaTypeSelection(includeVideos: Boolean): String {
        val image = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        return if (includeVideos) {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN ($image, $video)"
        } else {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $image"
        }
    }

    private fun videoOnlySelection(): String =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"

    private fun filesCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

    private fun imagesCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun videosCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private companion object {
        const val ID_QUERY_CHUNK = 500
        const val MONTH_PILE_LIMIT = 24
        const val THUMBNAIL_EDGE = 32
        val DEFAULT_SORT = "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC, " +
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    }
}
