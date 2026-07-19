package com.freqcast.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single daily wake-up alarm. Multiple rows are supported — each is independently
 * enabled/scheduled via [com.freqcast.ui.AlarmScheduler], keyed by this row's [id].
 */
@Entity(tableName = "wake_alarms")
data class WakeAlarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val stationName: String?,
    val streamUrl: String?,
)
