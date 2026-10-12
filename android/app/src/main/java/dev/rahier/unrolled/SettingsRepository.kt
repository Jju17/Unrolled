package dev.rahier.unrolled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class UserSettings(
    val showLocation: Boolean = true,
    val includeVideos: Boolean = true,
    val detectSimilar: Boolean = true,
    val gamificationEnabled: Boolean = true,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 50
        const val DEFAULT_REMINDER_HOUR = 19
        val DAILY_GOAL_CHOICES = listOf(25, 50, 100, 200)
    }
}

interface SettingsRepository {
    fun load(): UserSettings
    fun save(settings: UserSettings)
}

class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): UserSettings = UserSettings(
        showLocation = prefs.getBoolean(KEY_SHOW_LOCATION, true),
        includeVideos = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true),
        detectSimilar = prefs.getBoolean(KEY_DETECT_SIMILAR, true),
        gamificationEnabled = prefs.getBoolean(KEY_GAMIFICATION, true),
        dailyGoal = prefs.getInt(KEY_DAILY_GOAL, UserSettings.DEFAULT_DAILY_GOAL),
        reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false),
        reminderHour = prefs.getInt(KEY_REMINDER_HOUR, UserSettings.DEFAULT_REMINDER_HOUR)
    )

    override fun save(settings: UserSettings) {
        prefs.edit {
            putBoolean(KEY_SHOW_LOCATION, settings.showLocation)
            putBoolean(KEY_INCLUDE_VIDEOS, settings.includeVideos)
            putBoolean(KEY_DETECT_SIMILAR, settings.detectSimilar)
            putBoolean(KEY_GAMIFICATION, settings.gamificationEnabled)
            putInt(KEY_DAILY_GOAL, settings.dailyGoal)
            putBoolean(KEY_REMINDER_ENABLED, settings.reminderEnabled)
            putInt(KEY_REMINDER_HOUR, settings.reminderHour)
        }
    }

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_SHOW_LOCATION = "show_location"
        private const val KEY_INCLUDE_VIDEOS = "include_videos"
        private const val KEY_DETECT_SIMILAR = "detect_similar"
        private const val KEY_GAMIFICATION = "gamification_enabled"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
    }
}
