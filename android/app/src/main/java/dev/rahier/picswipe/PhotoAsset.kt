package dev.rahier.picswipe

import java.util.Date

/**
 * Lightweight reference to a photo in the user's MediaStore.
 * [uri] is the string form of the content URI (`content://media/...`);
 * Coil accepts it directly, and keeping it as a String lets the model
 * stay a pure JVM value type (no `android.net.Uri` dependency in the
 * ViewModel, which matters for local unit tests).
 */
data class PhotoAsset(
    val id: Long,
    val uri: String,
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
