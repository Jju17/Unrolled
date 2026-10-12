package dev.rahier.unrolled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent record of which photos the user has already classified.
 * Emptying the trash clears [deletedIDs]; `Reset progress` wipes both
 * lists; `Start over` wipes the entries of the current album only.
 */
data class SwipeProgress(
    val deletedIDs: List<Long> = emptyList(),
    val keptIDs: List<Long> = emptyList()
)

interface SwipeProgressRepository {
    fun load(): SwipeProgress
    fun save(progress: SwipeProgress)
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

    companion object {
        private const val PREFS_NAME = "swipe_progress"
        private const val KEY_DELETED = "deleted_ids"
        private const val KEY_KEPT = "kept_ids"

        private fun List<Long>.encodeIDs(): String =
            joinToString(separator = ",")

        private fun String?.decodeIDs(): List<Long> {
            if (this.isNullOrBlank()) return emptyList()
            return split(',').mapNotNull { it.toLongOrNull() }
        }
    }
}
