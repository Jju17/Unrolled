package dev.rahier.unrolled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface StatsRepository {
    fun load(): ActivityStats
    fun save(stats: ActivityStats)
}

class SharedPrefsStatsRepository(context: Context) : StatsRepository {
    private val prefs: SharedPreferences = context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ActivityStats = ActivityStats(
        bytesFreed = prefs.getLong(KEY_BYTES_FREED, 0L),
        photosDeleted = prefs.getInt(KEY_PHOTOS_DELETED, 0),
        photosReviewed = prefs.getInt(KEY_PHOTOS_REVIEWED, 0),
        todayCount = prefs.getInt(KEY_TODAY_COUNT, 0),
        todayKey = prefs.getString(KEY_TODAY_KEY, "").orEmpty(),
        currentStreak = prefs.getInt(KEY_CURRENT_STREAK, 0),
        longestStreak = prefs.getInt(KEY_LONGEST_STREAK, 0),
        lastGoalDay = prefs.getString(KEY_LAST_GOAL_DAY, null),
        lastFreezeWeek = prefs.getString(KEY_LAST_FREEZE_WEEK, null),
        reachedMilestones = prefs.getString(KEY_MILESTONES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    )

    override fun save(stats: ActivityStats) {
        prefs.edit {
            putLong(KEY_BYTES_FREED, stats.bytesFreed)
            putInt(KEY_PHOTOS_DELETED, stats.photosDeleted)
            putInt(KEY_PHOTOS_REVIEWED, stats.photosReviewed)
            putInt(KEY_TODAY_COUNT, stats.todayCount)
            putString(KEY_TODAY_KEY, stats.todayKey)
            putInt(KEY_CURRENT_STREAK, stats.currentStreak)
            putInt(KEY_LONGEST_STREAK, stats.longestStreak)
            putString(KEY_LAST_GOAL_DAY, stats.lastGoalDay)
            putString(KEY_LAST_FREEZE_WEEK, stats.lastFreezeWeek)
            putString(KEY_MILESTONES, stats.reachedMilestones.joinToString(","))
        }
    }

    private companion object {
        const val PREFS_NAME = "activity_stats"
        const val KEY_BYTES_FREED = "bytes_freed"
        const val KEY_PHOTOS_DELETED = "photos_deleted"
        const val KEY_PHOTOS_REVIEWED = "photos_reviewed"
        const val KEY_TODAY_COUNT = "today_count"
        const val KEY_TODAY_KEY = "today_key"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_LONGEST_STREAK = "longest_streak"
        const val KEY_LAST_GOAL_DAY = "last_goal_day"
        const val KEY_LAST_FREEZE_WEEK = "last_freeze_week"
        const val KEY_MILESTONES = "milestones"
    }
}
