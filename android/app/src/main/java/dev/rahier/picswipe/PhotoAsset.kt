package dev.rahier.picswipe

import android.net.Uri
import java.util.Date

/**
 * Lightweight reference to a photo in the user's MediaStore.
 * The [uri] is a `content://media/external/images/media/<id>` that
 * Coil can load directly.
 */
data class PhotoAsset(
    val id: Long,
    val uri: Uri,
    val creationDate: Date?
)

/**
 * A device photo album. On Android this is a MediaStore "bucket"
 * (directory-based) keyed by [id] = `BUCKET_ID` as a string, with the
 * synthetic [ALL_PHOTOS_ID] used for the unfiltered "All Photos" entry
 * that [PhotoRepository] prepends to the list.
 */
data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val icon: String
) {
    companion object {
        const val ALL_PHOTOS_ID = "__all__"
    }
}
