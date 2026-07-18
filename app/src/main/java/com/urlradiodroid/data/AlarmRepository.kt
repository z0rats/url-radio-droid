package com.urlradiodroid.data

import android.content.Context
import com.urlradiodroid.ui.playback.AlarmStateStore

class AlarmRepository(
    private val dao: WakeAlarmDao,
) {
    suspend fun getAllAlarms(): List<WakeAlarm> = dao.getAllAlarms()

    suspend fun getAlarmById(id: Long): WakeAlarm? = dao.getAlarmById(id)

    suspend fun insertAlarm(alarm: WakeAlarm): Long = dao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: WakeAlarm) = dao.updateAlarm(alarm)

    suspend fun deleteAlarm(id: Long) = dao.deleteAlarm(id)

    /**
     * One-time import of the pre-multi-alarm, SharedPreferences-backed single alarm ([legacyStore])
     * into this table, so upgrading from that version doesn't silently drop an existing user's
     * wake-up alarm. [AlarmStateStore.restore] returns null once [AlarmStateStore.clear] has run
     * here, so this is naturally idempotent — safe to call on every alarm-list load, boot, and
     * alarm firing. Returns the migrated row, or null if there was nothing to migrate.
     */
    suspend fun migrateLegacyAlarmIfNeeded(legacyStore: AlarmStateStore): WakeAlarm? {
        val legacy = legacyStore.restore() ?: return null
        val id =
            dao.insertAlarm(
                WakeAlarm(
                    enabled = legacy.enabled,
                    hour = legacy.hour,
                    minute = legacy.minute,
                    stationName = legacy.stationName,
                    streamUrl = legacy.streamUrl,
                ),
            )
        legacyStore.clear()
        return dao.getAlarmById(id)
    }

    companion object {
        fun create(context: Context): AlarmRepository = AlarmRepository(AppDatabase.getDatabase(context).wakeAlarmDao())
    }
}
