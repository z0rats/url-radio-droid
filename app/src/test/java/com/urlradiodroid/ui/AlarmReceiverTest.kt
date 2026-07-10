package com.urlradiodroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.urlradiodroid.ui.playback.AlarmStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val receiver = AlarmReceiver()

    @Test
    fun `onReceive starts playback of the saved station when the alarm is enabled`() {
        AlarmStateStore(context).save(
            AlarmStateStore.Alarm(
                enabled = true,
                hour = 7,
                minute = 0,
                stationName = "Morning FM",
                streamUrl = "https://stream.example.com/morning",
            ),
        )

        receiver.onReceive(context, Intent())

        val startedService = shadowOf(context as Application).nextStartedService
        assertEquals("Morning FM", startedService?.getStringExtra(RadioPlaybackService.EXTRA_STATION_NAME))
        assertEquals(
            "https://stream.example.com/morning",
            startedService?.getStringExtra(RadioPlaybackService.EXTRA_STREAM_URL),
        )
    }

    @Test
    fun `onReceive does nothing when no alarm was ever saved`() {
        receiver.onReceive(context, Intent())

        assertNull(shadowOf(context as Application).nextStartedService)
    }

    @Test
    fun `onReceive does nothing when the saved alarm is disabled`() {
        AlarmStateStore(context).save(
            AlarmStateStore.Alarm(
                enabled = false,
                hour = 7,
                minute = 0,
                stationName = "Morning FM",
                streamUrl = "https://stream.example.com/morning",
            ),
        )

        receiver.onReceive(context, Intent())

        assertNull(shadowOf(context as Application).nextStartedService)
    }
}
