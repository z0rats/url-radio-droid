package com.urlradiodroid.util

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.ui.playback.PlaybackStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `refresh publishes no shortcuts when there is no last-played station and no stations`() {
        AppShortcuts.refresh(context, stations = emptyList())

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun `refresh publishes only a last-played shortcut when the station list is empty`() {
        PlaybackStateStore(context).save("Radio One", "https://stream.example.com/one")

        AppShortcuts.refresh(
            context,
            stations = listOf(station(name = "Radio One", url = "https://stream.example.com/one")),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(1, shortcuts.size)
        assertEquals("last_played", shortcuts[0].id)
        assertEquals("Radio One", shortcuts[0].shortLabel)
    }

    @Test
    fun `refresh publishes only a first-station shortcut when nothing was ever played`() {
        AppShortcuts.refresh(
            context,
            stations = listOf(station(name = "Jazz FM", url = "https://stream.example.com/jazz")),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(1, shortcuts.size)
        assertEquals("first_station", shortcuts[0].id)
        assertEquals("Jazz FM", shortcuts[0].shortLabel)
    }

    @Test
    fun `refresh publishes both shortcuts, last-played ranked first, when they're different stations`() {
        PlaybackStateStore(context).save("Radio One", "https://stream.example.com/one")

        AppShortcuts.refresh(
            context,
            stations =
                listOf(
                    station(name = "Radio One", url = "https://stream.example.com/one"),
                    station(name = "Jazz FM", url = "https://stream.example.com/jazz"),
                ),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("last_played", "first_station"), shortcuts.map { it.id })
    }

    @Test
    fun `refresh uses the first station in list order, not just any station`() {
        AppShortcuts.refresh(
            context,
            stations =
                listOf(
                    station(name = "First In List", url = "https://stream.example.com/first"),
                    station(name = "Second In List", url = "https://stream.example.com/second"),
                ),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(1, shortcuts.size)
        assertEquals("First In List", shortcuts[0].shortLabel)
    }

    @Test
    fun `refresh dedupes to a single shortcut when the first station is also the last-played station`() {
        PlaybackStateStore(context).save("Jazz FM", "https://stream.example.com/jazz")

        AppShortcuts.refresh(
            context,
            stations = listOf(station(name = "Jazz FM", url = "https://stream.example.com/jazz")),
        )

        assertEquals(1, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    private fun station(
        name: String,
        url: String,
    ) = RadioStation(name = name, streamUrl = url)
}
