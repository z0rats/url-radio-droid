package com.urlradiodroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface WakeAlarmDao {
    @Query("SELECT * FROM wake_alarms ORDER BY hour ASC, minute ASC, id ASC")
    suspend fun getAllAlarms(): List<WakeAlarm>

    @Query("SELECT * FROM wake_alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): WakeAlarm?

    @Insert
    suspend fun insertAlarm(alarm: WakeAlarm): Long

    @Update
    suspend fun updateAlarm(alarm: WakeAlarm)

    @Query("DELETE FROM wake_alarms WHERE id = :id")
    suspend fun deleteAlarm(id: Long)
}
