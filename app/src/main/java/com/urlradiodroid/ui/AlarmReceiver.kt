package com.urlradiodroid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.urlradiodroid.ui.playback.AlarmStateStore

/** Fired by [AlarmScheduler] at the scheduled wake-up time: starts playback, then reschedules itself for tomorrow. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val alarm = AlarmStateStore(context).restore() ?: return
        val streamUrl = alarm.streamUrl ?: return
        if (!alarm.enabled) return

        val serviceIntent =
            Intent(context, RadioPlaybackService::class.java).apply {
                putExtra(RadioPlaybackService.EXTRA_STATION_NAME, alarm.stationName)
                putExtra(RadioPlaybackService.EXTRA_STREAM_URL, streamUrl)
            }
        ContextCompat.startForegroundService(context, serviceIntent)

        AlarmScheduler.schedule(context, alarm.hour, alarm.minute)
    }
}
