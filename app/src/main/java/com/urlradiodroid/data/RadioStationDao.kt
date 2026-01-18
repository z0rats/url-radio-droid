package com.urlradiodroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations ORDER BY name ASC")
    suspend fun getAllStations(): List<RadioStation>

    @Query("SELECT * FROM radio_stations WHERE id = :id")
    suspend fun getStationById(id: Long): RadioStation?

    @Insert
    suspend fun insertStation(station: RadioStation): Long
}
