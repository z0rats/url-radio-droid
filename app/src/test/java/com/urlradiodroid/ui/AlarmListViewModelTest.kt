package com.urlradiodroid.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.urlradiodroid.data.AlarmRepository
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.WakeAlarm
import com.urlradiodroid.ui.playback.AlarmStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmListViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var legacyStore: AlarmStateStore

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = AlarmRepository(database.wakeAlarmDao())
        legacyStore = AlarmStateStore(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(scheduler: TestCoroutineScheduler): AlarmListViewModel {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        return AlarmListViewModel(repository, legacyStore)
    }

    private suspend fun TestScope.waitUntil(condition: () -> Boolean) {
        withTimeout(5000L) {
            while (!condition()) {
                advanceUntilIdle()
            }
        }
    }

    @Test
    fun `loadAlarms populates alarms ordered by time`() =
        runTest {
            repository.insertAlarm(WakeAlarm(enabled = true, hour = 8, minute = 0, stationName = "B", streamUrl = "b"))
            repository.insertAlarm(WakeAlarm(enabled = true, hour = 7, minute = 0, stationName = "A", streamUrl = "a"))
            val viewModel = createViewModel(testScheduler)

            viewModel.loadAlarms()
            waitUntil { viewModel.alarms.value.size == 2 }

            assertEquals(listOf("A", "B"), viewModel.alarms.value.map { it.stationName })
        }

    @Test
    fun `setEnabled persists the new enabled value`() =
        runTest {
            val id =
                repository.insertAlarm(
                    WakeAlarm(enabled = false, hour = 7, minute = 0, stationName = "A", streamUrl = "a"),
                )
            val viewModel = createViewModel(testScheduler)
            viewModel.loadAlarms()
            waitUntil { viewModel.alarms.value.size == 1 }

            viewModel.setEnabled(viewModel.alarms.value[0], true)
            waitUntil {
                viewModel.alarms.value
                    .firstOrNull { it.id == id }
                    ?.enabled == true
            }

            assertTrue(repository.getAlarmById(id)!!.enabled)
        }

    @Test
    fun `deleteAlarm removes it from the list and the database`() =
        runTest {
            val id =
                repository.insertAlarm(
                    WakeAlarm(enabled = true, hour = 7, minute = 0, stationName = "A", streamUrl = "a"),
                )
            val viewModel = createViewModel(testScheduler)
            viewModel.loadAlarms()
            waitUntil { viewModel.alarms.value.size == 1 }

            viewModel.deleteAlarm(viewModel.alarms.value[0])
            waitUntil { viewModel.alarms.value.isEmpty() }

            assertEquals(null, repository.getAlarmById(id))
        }

    @Test
    fun `loadAlarms migrates a legacy alarm and emits LegacyAlarmMigrated`() =
        runTest {
            legacyStore.save(
                AlarmStateStore.Alarm(
                    enabled = true,
                    hour = 7,
                    minute = 45,
                    stationName = "Morning FM",
                    streamUrl = "https://stream.example.com/morning",
                ),
            )
            val viewModel = createViewModel(testScheduler)
            val events = mutableListOf<AlarmListEvent>()
            val job = launch { viewModel.events.collect { events.add(it) } }

            viewModel.loadAlarms()
            waitUntil { events.isNotEmpty() }

            val event = events.single() as AlarmListEvent.LegacyAlarmMigrated
            assertEquals("Morning FM", event.alarm.stationName)
            assertEquals(1, viewModel.alarms.value.size)
            job.cancel()
        }
}
