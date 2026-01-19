package com.urlradiodroid.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RadioStationDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: RadioStationDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.radioStationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test insert and getAllStations`() = runTest {
        val station1 = RadioStation(
            name = "Station 1",
            streamUrl = "http://example.com/stream1"
        )
        val station2 = RadioStation(
            name = "Station 2",
            streamUrl = "http://example.com/stream2"
        )

        dao.insertStation(station1)
        dao.insertStation(station2)

        val stations = dao.getAllStations()

        assertEquals(2, stations.size)
        assertEquals("Station 1", stations[0].name)
        assertEquals("Station 2", stations[1].name)
    }

    @Test
    fun `test getAllStations returns empty list when no stations`() = runTest {
        val stations = dao.getAllStations()

        assertEquals(0, stations.size)
    }

    @Test
    fun `test getAllStations orders by name ascending`() = runTest {
        val station1 = RadioStation(name = "Z Station", streamUrl = "http://example.com/z")
        val station2 = RadioStation(name = "A Station", streamUrl = "http://example.com/a")
        val station3 = RadioStation(name = "M Station", streamUrl = "http://example.com/m")

        dao.insertStation(station1)
        dao.insertStation(station2)
        dao.insertStation(station3)

        val stations = dao.getAllStations()

        assertEquals(3, stations.size)
        assertEquals("A Station", stations[0].name)
        assertEquals("M Station", stations[1].name)
        assertEquals("Z Station", stations[2].name)
    }

    @Test
    fun `test getStationById returns correct station`() = runTest {
        val station = RadioStation(
            name = "Test Station",
            streamUrl = "http://example.com/test"
        )

        val id = dao.insertStation(station)
        val retrieved = dao.getStationById(id)

        assertEquals(station.name, retrieved?.name)
        assertEquals(station.streamUrl, retrieved?.streamUrl)
        assertEquals(id, retrieved?.id)
    }

    @Test
    fun `test getStationById returns null for non-existent id`() = runTest {
        val station = dao.getStationById(999L)

        assertNull(station)
    }

    @Test
    fun `test insertStation returns generated id`() = runTest {
        val station = RadioStation(
            name = "New Station",
            streamUrl = "http://example.com/new"
        )

        val id = dao.insertStation(station)

        assert(id > 0)
        val retrieved = dao.getStationById(id)
        assertEquals(id, retrieved?.id)
    }
}
