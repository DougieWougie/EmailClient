package com.emailclient.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app preferences using SharedPreferences
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        const val DEFAULT_SYNC_INTERVAL = 30 // 30 minutes default
    }

    /**
     * Get sync interval in minutes
     */
    fun getSyncInterval(): Int {
        return prefs.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
    }

    /**
     * Set sync interval in minutes
     */
    fun setSyncInterval(minutes: Int) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL, minutes).apply()
        android.util.Log.d("AppPreferences", "Sync interval set to $minutes minutes")
    }

    /**
     * Get available sync interval options
     */
    fun getSyncIntervalOptions(): List<SyncIntervalOption> {
        return listOf(
            SyncIntervalOption(15, "15 minutes"),
            SyncIntervalOption(30, "30 minutes"),
            SyncIntervalOption(60, "1 hour"),
            SyncIntervalOption(120, "2 hours"),
            SyncIntervalOption(240, "4 hours"),
            SyncIntervalOption(480, "8 hours"),
            SyncIntervalOption(720, "12 hours"),
            SyncIntervalOption(1440, "24 hours"),
            SyncIntervalOption(0, "Manual only")
        )
    }

    /**
     * Get whether activity animations are enabled
     */
    fun areAnimationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true) // Default to enabled
    }

    /**
     * Set whether activity animations are enabled
     */
    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, enabled).apply()
        android.util.Log.d("AppPreferences", "Animations enabled set to $enabled")
    }
}

/**
 * Data class for sync interval options
 */
data class SyncIntervalOption(
    val minutes: Int,
    val label: String
)
