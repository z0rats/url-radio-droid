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
    fun `refresh publishes no shortcuts when there is no last-played station and no favorite`() {
        AppShortcuts.refresh(context, stations = emptyList())

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun `refresh publishes only a last-played shortcut when nothing is favorited`() {
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
    fun `refresh publishes only a favorite shortcut when nothing was ever played`() {
        AppShortcuts.refresh(
            context,
            stations = listOf(station(name = "Jazz FM", url = "https://stream.example.com/jazz", isFavorite = true)),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(1, shortcuts.size)
        assertEquals("favorite", shortcuts[0].id)
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
                    station(name = "Jazz FM", url = "https://stream.example.com/jazz", isFavorite = true),
                ),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("last_played", "favorite"), shortcuts.map { it.id })
    }

    @Test
    fun `refresh dedupes to a single shortcut when the favorite is also the last-played station`() {
        PlaybackStateStore(context).save("Jazz FM", "https://stream.example.com/jazz")

        AppShortcuts.refresh(
            context,
            stations = listOf(station(name = "Jazz FM", url = "https://stream.example.com/jazz", isFavorite = true)),
        )

        assertEquals(1, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    private fun station(
        name: String,
        url: String,
        isFavorite: Boolean = false,
    ) = RadioStation(name = name, streamUrl = url, isFavorite = isFavorite)
}
