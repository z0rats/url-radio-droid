package com.urlradiodroid.ui.playback

import android.content.Context

/**
 * Persists app-wide user preferences (currently just the metered-connection playback warning),
 * mirroring [PlaybackStateStore]'s SharedPreferences-backed shape.
 */
class SettingsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var warnOnMeteredConnection: Boolean
        get() = prefs.getBoolean(KEY_WARN_ON_METERED, true)
        set(value) = prefs.edit().putBoolean(KEY_WARN_ON_METERED, value).apply()

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_WARN_ON_METERED = "warn_on_metered_connection"
    }
}
