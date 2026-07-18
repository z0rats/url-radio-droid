package com.urlradiodroid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.urlradiodroid.data.AlarmRepository
import com.urlradiodroid.ui.playback.AlarmStateStore
import kotlinx.coroutines.runBlocking

/**
 * AlarmManager alarms are cleared on reboot; reschedules every enabled wake-up alarm. Uses
 * [runBlocking] for the same reason as [AlarmReceiver] — a small, bounded Room read.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runBlocking {
            val repository = AlarmRepository.create(context)
            // Covers the case where the device rebooted before the app was ever reopened
            // post-update, so the one-time SharedPreferences->Room import hadn't run yet.
            repository.migrateLegacyAlarmIfNeeded(AlarmStateStore(context))
            repository
                .getAllAlarms()
                .filter { it.enabled && it.streamUrl != null }
                .forEach { alarm -> AlarmScheduler.schedule(context, alarm.id, alarm.hour, alarm.minute) }
        }
    }
}
