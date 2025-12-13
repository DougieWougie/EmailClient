package com.emailclient.data.local

import android.content.Context
import android.content.SharedPreferences
import com.emailclient.domain.model.SwipeAction
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
        private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
        private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"
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

    /**
     * Get swipe left action
     */
    fun getSwipeLeftAction(): SwipeAction {
        val ordinal = prefs.getInt(KEY_SWIPE_LEFT_ACTION, SwipeAction.MARK_READ.ordinal)
        return SwipeAction.fromOrdinal(ordinal)
    }

    /**
     * Set swipe left action
     */
    fun setSwipeLeftAction(action: SwipeAction) {
        prefs.edit().putInt(KEY_SWIPE_LEFT_ACTION, action.ordinal).apply()
        android.util.Log.d("AppPreferences", "Swipe left action set to ${action.displayName}")
    }

    /**
     * Get swipe right action
     */
    fun getSwipeRightAction(): SwipeAction {
        val ordinal = prefs.getInt(KEY_SWIPE_RIGHT_ACTION, SwipeAction.ARCHIVE.ordinal)
        return SwipeAction.fromOrdinal(ordinal)
    }

    /**
     * Set swipe right action
     */
    fun setSwipeRightAction(action: SwipeAction) {
        prefs.edit().putInt(KEY_SWIPE_RIGHT_ACTION, action.ordinal).apply()
        android.util.Log.d("AppPreferences", "Swipe right action set to ${action.displayName}")
    }
}

/**
 * Data class for sync interval options
 */
data class SyncIntervalOption(
    val minutes: Int,
    val label: String
)
