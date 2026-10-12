package dev.rahier.unrolled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent record of which photos the user has already classified.
 * Survives the process so a relaunch doesn't re-present photos the
 * user has already kept or staged for deletion. Emptying the trash
 * clears [deletedIDs] (those assets are gone from the system library);
 * only the explicit `Reset progress` button in the trash sheet wipes
 * [keptIDs] as well.
 */
data class SwipeProgress(
    val deletedIDs: List<Long> = emptyList(),
    val keptIDs: List<Long> = emptyList()
)

interface SwipeProgressRepository {
    fun load(): SwipeProgress
    fun save(progress: SwipeProgress)
    fun clear()
}

class SharedPrefsSwipeProgressRepository(context: Context) : SwipeProgressRepository {
    private val prefs: SharedPreferences = context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SwipeProgress = SwipeProgress(
        deletedIDs = prefs.getString(KEY_DELETED, null).decodeIDs(),
        keptIDs = prefs.getString(KEY_KEPT, null).decodeIDs()
    )

    override fun save(progress: SwipeProgress) {
        prefs.edit {
            putString(KEY_DELETED, progress.deletedIDs.encodeIDs())
            putString(KEY_KEPT, progress.keptIDs.encodeIDs())
        }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS_NAME = "swipe_progress"
        private const val KEY_DELETED = "deleted_ids"
        private const val KEY_KEPT = "kept_ids"

        // Plain comma-separated Long list. Avoids pulling in a JSON
        // dependency just to persist two flat lists. Empty / null
        // strings round-trip to an empty list.
        private fun List<Long>.encodeIDs(): String =
            joinToString(separator = ",")

        private fun String?.decodeIDs(): List<Long> {
            if (this.isNullOrBlank()) return emptyList()
            return split(',').mapNotNull { it.toLongOrNull() }
        }
    }
}
