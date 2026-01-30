package com.urlradiodroid.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        viewModel = MainViewModel(database)
    }

    @After
    fun tearDown() {
        database.close()
        kotlinx.coroutines.test.resetMain()
    }

    @Test
    fun `getCurrentPlayingStationId returns null initially`() {
        assertNull(viewModel.getCurrentPlayingStationId())
    }

    @Test
    fun `updateCurrentPlayingStation sets and getCurrentPlayingStationId returns id`() = runTest {
        viewModel.updateCurrentPlayingStation(1L)
        assertEquals(1L, viewModel.getCurrentPlayingStationId())
    }

    @Test
    fun `updateCurrentPlayingStation null clears current station`() = runTest {
        viewModel.updateCurrentPlayingStation(5L)
        viewModel.updateCurrentPlayingStation(null)
        assertNull(viewModel.getCurrentPlayingStationId())
    }

    @Test
    fun `updateSearchQuery updates searchQuery flow`() = runTest {
        viewModel.updateSearchQuery("test query")
        assertEquals("test query", viewModel.searchQuery.first())
    }

    @Test
    fun `filteredStations returns all stations when search is blank`() = runTest {
        testScheduler.advanceUntilIdle()
        val stations = listOf(
            RadioStation(name = "Station A", streamUrl = "http://example.com/a"),
            RadioStation(name = "Station B", streamUrl = "http://example.com/b")
        )
        stations.forEach { database.radioStationDao().insertStation(it) }
        viewModel.loadStations()
        testScheduler.advanceUntilIdle()
        viewModel.updateSearchQuery("")
        val result = viewModel.filteredStations.first()
        assertEquals(2, result.size)
    }

    @Test
    fun `filteredStations filters by name`() = runTest {
        testScheduler.advanceUntilIdle()
        database.radioStationDao().insertStation(RadioStation(name = "Rock FM", streamUrl = "http://example.com/rock"))
        database.radioStationDao().insertStation(RadioStation(name = "Jazz Radio", streamUrl = "http://example.com/jazz"))
        viewModel.loadStations()
        testScheduler.advanceUntilIdle()
        viewModel.updateSearchQuery("Rock")
        val result = viewModel.filteredStations.first()
        assertEquals(1, result.size)
        assertEquals("Rock FM", result[0].name)
    }

    @Test
    fun `deleteStation removes station and reloads list`() = runTest {
        testScheduler.advanceUntilIdle()
        val id = database.radioStationDao().insertStation(
            RadioStation(name = "To Delete", streamUrl = "http://example.com/del")
        )
        viewModel.loadStations()
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.stations.value.size)
        viewModel.deleteStation(id)
        testScheduler.advanceUntilIdle()
        assertEquals(0, viewModel.stations.value.size)
    }
}
