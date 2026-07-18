package com.urlradiodroid.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.urlradiodroid.ui.playback.AlarmStateStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AlarmRepository

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = AlarmRepository(database.wakeAlarmDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert then getAllAlarms returns alarms ordered by time`() =
        runTest {
            repository.insertAlarm(WakeAlarm(enabled = true, hour = 8, minute = 0, stationName = "B", streamUrl = "b"))
            repository.insertAlarm(WakeAlarm(enabled = true, hour = 7, minute = 30, stationName = "A", streamUrl = "a"))

            val alarms = repository.getAllAlarms()

            assertEquals(listOf("A", "B"), alarms.map { it.stationName })
        }

    @Test
    fun `updateAlarm and deleteAlarm mutate the stored row`() =
        runTest {
            val id =
                repository.insertAlarm(
                    WakeAlarm(enabled = false, hour = 7, minute = 0, stationName = "A", streamUrl = "a"),
                )

            repository.updateAlarm(repository.getAlarmById(id)!!.copy(enabled = true))
            assertTrue(repository.getAlarmById(id)!!.enabled)

            repository.deleteAlarm(id)
            assertNull(repository.getAlarmById(id))
        }

    @Test
    fun `migrateLegacyAlarmIfNeeded imports the legacy alarm once and clears it`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val legacyStore = AlarmStateStore(context)
            legacyStore.save(
                AlarmStateStore.Alarm(
                    enabled = true,
                    hour = 7,
                    minute = 45,
                    stationName = "Morning FM",
                    streamUrl = "https://stream.example.com/morning",
                ),
            )

            val migrated = repository.migrateLegacyAlarmIfNeeded(legacyStore)

            assertEquals("Morning FM", migrated?.stationName)
            assertEquals(1, repository.getAllAlarms().size)
            assertNull(legacyStore.restore())

            // Idempotent: a second call with nothing left to migrate is a no-op, not a duplicate row.
            val secondCall = repository.migrateLegacyAlarmIfNeeded(legacyStore)
            assertNull(secondCall)
            assertEquals(1, repository.getAllAlarms().size)
        }

    @Test
    fun `migrateLegacyAlarmIfNeeded returns null when nothing was ever saved`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()

            val migrated = repository.migrateLegacyAlarmIfNeeded(AlarmStateStore(context))

            assertNull(migrated)
            assertEquals(0, repository.getAllAlarms().size)
        }
}
