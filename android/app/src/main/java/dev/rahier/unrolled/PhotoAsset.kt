package dev.rahier.unrolled

import java.util.Date

/**
 * Lightweight reference to a photo or video in the user's MediaStore.
 * [uri] is the string form of the content URI; Coil accepts it directly
 * and keeping it as a String keeps the model free of `android.net.Uri`.
 */
data class PhotoAsset(
    val id: Long,
    val uri: String,
    val creationDate: Date?,
    val sizeBytes: Long = 0L,
    val isVideo: Boolean = false,
    val durationMs: Long? = null
)

/** Which slice of the library a deck is built from. */
sealed interface PileSelector {
    val id: String

    data object All : PileSelector {
        override val id: String get() = "all"
    }

    data class Album(val albumId: String) : PileSelector {
        override val id: String get() = "album:$albumId"
    }

    data class Month(val year: Int, val month: Int) : PileSelector {
        override val id: String get() = "month:%04d-%02d".format(year, month)
    }

    data object OnThisDay : PileSelector {
        override val id: String get() = "onThisDay"
    }

    data object OneYearAgo : PileSelector {
        override val id: String get() = "oneYearAgo"
    }

    data object Screenshots : PileSelector {
        override val id: String get() = "screenshots"
    }

    data object Videos : PileSelector {
        override val id: String get() = "videos"
    }
}

/**
 * A finite unit of work: a month, a special selection or a MediaStore
 * bucket, with how much of it is still undecided.
 */
data class Pile(
    val selector: PileSelector,
    val name: String? = null,
    val photoCount: Int,
    val remainingCount: Int,
    val keyUri: String? = null
) {
    val id: String get() = selector.id

    val progress: Double
        get() = if (photoCount == 0) 0.0 else 1.0 - remainingCount.toDouble() / photoCount
}
