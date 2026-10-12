package dev.rahier.unrolled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persisted user preferences. Backed by [SharedPreferences] in
 * production; tests can supply a fake [SettingsRepository].
 *
 * On Android, the iOS-only metadata features (people tags, Live Photos)
 * have no equivalent — those settings simply don't exist here, per
 * product decision.
 */
data class UserSettings(
    val showLocation: Boolean = true
)

interface SettingsRepository {
    fun load(): UserSettings
    fun save(settings: UserSettings)
}

class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): UserSettings = UserSettings(
        showLocation = prefs.getBoolean(KEY_SHOW_LOCATION, true)
    )

    override fun save(settings: UserSettings) {
        prefs.edit { putBoolean(KEY_SHOW_LOCATION, settings.showLocation) }
    }

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_SHOW_LOCATION = "show_location"
    }
}
