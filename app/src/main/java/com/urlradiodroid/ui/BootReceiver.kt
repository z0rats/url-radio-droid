package com.urlradiodroid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.urlradiodroid.ui.playback.AlarmStateStore

/** AlarmManager alarms are cleared on reboot; reschedules the wake-up alarm if it was enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val alarm = AlarmStateStore(context).restore() ?: return
        if (alarm.enabled && alarm.streamUrl != null) {
            AlarmScheduler.schedule(context, alarm.hour, alarm.minute)
        }
    }
}
