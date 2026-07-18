package com.urlradiodroid.ui.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AlarmStateStore(context)

    @Test
    fun `restore returns null when nothing was ever saved`() {
        assertNull(store.restore())
    }

    @Test
    fun `save then restore round-trips all fields`() {
        store.save(
            AlarmStateStore.Alarm(
                enabled = true,
                hour = 7,
                minute = 45,
                stationName = "Morning FM",
                streamUrl = "https://stream.example.com/morning",
            ),
        )

        val restored = store.restore()

        assertEquals(true, restored?.enabled)
        assertEquals(7, restored?.hour)
        assertEquals(45, restored?.minute)
        assertEquals("Morning FM", restored?.stationName)
        assertEquals("https://stream.example.com/morning", restored?.streamUrl)
    }

    @Test
    fun `a saved but disabled alarm still restores with enabled false`() {
        store.save(
            AlarmStateStore.Alarm(
                enabled = false,
                hour = 8,
                minute = 0,
                stationName = "Morning FM",
                streamUrl = "https://stream.example.com/morning",
            ),
        )

        val restored = store.restore()

        assertTrue(restored != null && !restored.enabled)
    }

    @Test
    fun `clear wipes a saved alarm so restore returns null afterward`() {
        store.save(
            AlarmStateStore.Alarm(
                enabled = true,
                hour = 7,
                minute = 45,
                stationName = "Morning FM",
                streamUrl = "https://stream.example.com/morning",
            ),
        )

        store.clear()

        assertNull(store.restore())
    }
}
