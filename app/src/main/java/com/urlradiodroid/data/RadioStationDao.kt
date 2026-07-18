package com.urlradiodroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface RadioStationDao {
    // Secondary `id ASC` tie-breaks stations sharing a sortOrder (SQLite's order for ties is
    // otherwise unspecified) — normally only possible via a direct DAO insert bypassing
    // RadioStationRepository.insertStation()'s auto-assigned sortOrder, but deterministic either way.
    @Query("SELECT * FROM radio_stations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllStations(): List<RadioStation>

    @Query("SELECT * FROM radio_stations WHERE id = :id")
    suspend fun getStationById(id: Long): RadioStation?

    @Insert
    suspend fun insertStation(station: RadioStation): Long

    @Update
    suspend fun updateStation(station: RadioStation)

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun deleteStation(id: Long)

    /** Highest existing `sortOrder`, or -1 if the table is empty — next new station appends at +1. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM radio_stations")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE radio_stations SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(
        id: Long,
        sortOrder: Int,
    )

    /** Reassigns `sortOrder` for every id in [orderedIds] to its index — the new drag-drop order. */
    @Transaction
    suspend fun updateSortOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("SELECT * FROM radio_stations WHERE name = :name AND id != :excludeId LIMIT 1")
    suspend fun findStationByName(
        name: String,
        excludeId: Long = 0,
    ): RadioStation?

    @Query("SELECT * FROM radio_stations WHERE streamUrl = :url AND id != :excludeId LIMIT 1")
    suspend fun findStationByUrl(
        url: String,
        excludeId: Long = 0,
    ): RadioStation?
}
