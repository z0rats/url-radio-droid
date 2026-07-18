package com.urlradiodroid.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules/cancels wake-up alarms via [AlarmManager.setAlarmClock]. Unlike
 * `setExactAndAllowWhileIdle`, apps that show a user-visible alarm (this one shows the status bar
 * alarm-clock icon, via [AlarmManager.AlarmClockInfo]) are *normally* exempt from the
 * `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` permission dance — but that exemption isn't guaranteed
 * on every OS build/device (observed in practice: `setAlarmClock()` itself throwing
 * `SecurityException` demanding one of those permissions). [schedule] therefore checks
 * [AlarmManager.canScheduleExactAlarms] first and also catches the exception as a last-resort
 * safety net, since [BootReceiver]/[AlarmReceiver] call this with no UI to recover from a crash —
 * it must never throw.
 *
 * Each [com.urlradiodroid.data.WakeAlarm] row gets its own [PendingIntent], keyed by a request
 * code derived from [alarmId] ([requestCodeFor]), so multiple alarms can be independently
 * scheduled/cancelled/fired without clobbering each other's [AlarmManager] registration.
 */
object AlarmScheduler {
    /** Shared by every alarm's [AlarmManager.AlarmClockInfo] "show" intent — always just opens the app. */
    private const val SHOW_REQUEST_CODE = 999

    /**
     * The request code the single pre-multi-alarm alarm used. Kept only so [cancelLegacy] can find
     * and cancel that PendingIntent if it's still registered with [AlarmManager] (it survives an
     * app update, just not a reboot) — see [com.urlradiodroid.data.AlarmRepository.migrateLegacyAlarmIfNeeded].
     */
    private const val LEGACY_REQUEST_CODE = 1001

    /** Offset so a per-alarm request code ([requestCodeFor]) can never collide with [SHOW_REQUEST_CODE]/[LEGACY_REQUEST_CODE]. */
    private const val REQUEST_CODE_OFFSET = 2000

    /** Returns true if the alarm was actually scheduled, false if exact-alarm permission is missing. */
    fun schedule(
        context: Context,
        alarmId: Long,
        hour: Int,
        minute: Int,
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val triggerAtMillis = nextTriggerMillis(hour, minute, System.currentTimeMillis())
        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent(context)),
                operationIntent(context, requestCodeFor(alarmId), alarmId),
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(
        context: Context,
        alarmId: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(operationIntent(context, requestCodeFor(alarmId), alarmId))
    }

    /** Cancels the pre-multi-alarm single alarm's [PendingIntent], if it's still registered — see [LEGACY_REQUEST_CODE]. */
    fun cancelLegacy(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                LEGACY_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun operationIntent(
        context: Context,
        requestCode: Int,
        alarmId: Long,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCodeFor(alarmId: Long): Int = (REQUEST_CODE_OFFSET + alarmId).toInt()

    /** Next occurrence of [hour]:[minute] at/after [nowMillis] — today if that time hasn't passed yet, else tomorrow. */
    internal fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long,
    ): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
