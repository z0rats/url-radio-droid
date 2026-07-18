package com.urlradiodroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaylistImportTest {
    @Test
    fun `parse reads OPML outlines with xmlUrl and text attributes`() {
        val opml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline text="Rock FM" xmlUrl="http://example.com/rock"/>
                <outline text="Jazz Radio" xmlUrl="http://example.com/jazz"/>
              </body>
            </opml>
            """.trimIndent()

        val result = PlaylistImport.parse(opml)

        assertEquals(
            listOf(
                ParsedPlaylistStation("Rock FM", "http://example.com/rock"),
                ParsedPlaylistStation("Jazz Radio", "http://example.com/jazz"),
            ),
            result,
        )
    }

    @Test
    fun `parse reads nested OPML outlines and skips folder outlines with no url`() {
        val opml =
            """
            <opml version="1.0">
              <body>
                <outline text="Favorites">
                  <outline text="Rock FM" url="http://example.com/rock"/>
                </outline>
              </body>
            </opml>
            """.trimIndent()

        val result = PlaylistImport.parse(opml)

        assertEquals(listOf(ParsedPlaylistStation("Rock FM", "http://example.com/rock")), result)
    }

    @Test
    fun `parse falls back to the url as name when an OPML outline has no text`() {
        val opml = """<opml><body><outline xmlUrl="http://example.com/rock"/></body></opml>"""

        val result = PlaylistImport.parse(opml)

        assertEquals(listOf(ParsedPlaylistStation("http://example.com/rock", "http://example.com/rock")), result)
    }

    @Test
    fun `parse reads M3U entries with EXTINF titles`() {
        val m3u =
            """
            #EXTM3U
            #EXTINF:-1,Rock FM
            http://example.com/rock
            #EXTINF:-1,Jazz Radio
            http://example.com/jazz
            """.trimIndent()

        val result = PlaylistImport.parse(m3u)

        assertEquals(
            listOf(
                ParsedPlaylistStation("Rock FM", "http://example.com/rock"),
                ParsedPlaylistStation("Jazz Radio", "http://example.com/jazz"),
            ),
            result,
        )
    }

    @Test
    fun `parse falls back to the url as name for a bare M3U entry with no EXTINF`() {
        val m3u = "#EXTM3U\nhttp://example.com/rock"

        val result = PlaylistImport.parse(m3u)

        assertEquals(listOf(ParsedPlaylistStation("http://example.com/rock", "http://example.com/rock")), result)
    }

    @Test
    fun `parse reads PLS entries regardless of File Title line order`() {
        val pls =
            """
            [playlist]
            NumberOfEntries=2
            Title1=Rock FM
            File1=http://example.com/rock
            File2=http://example.com/jazz
            Title2=Jazz Radio
            Version=2
            """.trimIndent()

        val result = PlaylistImport.parse(pls)

        assertEquals(
            listOf(
                ParsedPlaylistStation("Rock FM", "http://example.com/rock"),
                ParsedPlaylistStation("Jazz Radio", "http://example.com/jazz"),
            ),
            result,
        )
    }

    @Test
    fun `parse falls back to the url as name for a PLS entry with no Title`() {
        val pls = "[playlist]\nFile1=http://example.com/rock\nNumberOfEntries=1"

        val result = PlaylistImport.parse(pls)

        assertEquals(listOf(ParsedPlaylistStation("http://example.com/rock", "http://example.com/rock")), result)
    }

    @Test
    fun `parse throws IllegalArgumentException for unrecognized content`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaylistImport.parse("just some plain text, not a playlist")
        }
    }
}
