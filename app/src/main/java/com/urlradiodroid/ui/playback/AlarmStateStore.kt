package com.urlradiodroid.ui.playback

import android.content.Context

/**
 * Persists the single wake-up alarm (enabled flag, time-of-day, target station) so
 * [com.urlradiodroid.ui.AlarmReceiver] and [com.urlradiodroid.ui.BootReceiver] can read it and
 * reschedule the next occurrence without a Room round-trip, mirroring [PlaybackStateStore]'s
 * SharedPreferences-backed shape.
 */
class AlarmStateStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(alarm: Alarm) {
        prefs
            .edit()
            .putBoolean(KEY_ENABLED, alarm.enabled)
            .putInt(KEY_HOUR, alarm.hour)
            .putInt(KEY_MINUTE, alarm.minute)
            .putString(KEY_STATION_NAME, alarm.stationName)
            .putString(KEY_STREAM_URL, alarm.streamUrl)
            .apply()
    }

    /** Wipes the stored alarm — called once [restore]'s data has been imported into Room, see [com.urlradiodroid.data.AlarmRepository.migrateLegacyAlarmIfNeeded]. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Null only if an alarm has never been saved; a saved-but-disabled alarm still restores with `enabled = false`. */
    fun restore(): Alarm? {
        if (!prefs.contains(KEY_HOUR)) return null
        return Alarm(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
            minute = prefs.getInt(KEY_MINUTE, 0),
            stationName = prefs.getString(KEY_STATION_NAME, null),
            streamUrl = prefs.getString(KEY_STREAM_URL, null),
        )
    }

    data class Alarm(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val stationName: String?,
        val streamUrl: String?,
    )

    companion object {
        private const val PREFS_NAME = "alarm_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_STATION_NAME = "station_name"
        private const val KEY_STREAM_URL = "stream_url"
        const val DEFAULT_HOUR = 7
    }
}
