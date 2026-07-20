package com.freqcast.data

import com.freqcast.util.StreamValidator
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

/**
 * [MockWebServer] doesn't care what Host header a request arrives with, only the socket it's
 * bound to - so tests that need a real (non-"localhost") multi-label hostname, to exercise
 * [StationUrlResolver]'s domain-label logic, route every hostname to the loopback address via a
 * custom [Dns] rather than depending on the test machine's own DNS/resolver quirks (e.g.
 * `*.localhost` auto-resolution isn't guaranteed portable across CI).
 */
private val LOOPBACK_DNS =
    Dns { listOf(InetAddress.getByName("127.0.0.1")) }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StationUrlResolverTest {
    private lateinit var server: MockWebServer
    private val loopbackClient = OkHttpClient.Builder().dns(LOOPBACK_DNS).build()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun resolver(radioBrowserApi: RadioBrowserApi = RadioBrowserApi(baseUrl = server.url("/"))) =
        StationUrlResolver(
            radioBrowserApi = radioBrowserApi,
            streamValidator = StreamValidator(client = loopbackClient),
            client = loopbackClient,
        )

    @Test
    fun `resolve returns the directory match when its homepage matches the target host`() =
        runTest {
            val body =
                """
                [{"name":"Silver Rain","url":"${server.url("/stream")}","homepage":"https://www.myradio.test/",
                  "stationuuid":"abc-123","hls":0}]
                """.trimIndent()
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setResponseCode(200))

            val result = resolver().resolve("https://myradio.test/")

            assertEquals(server.url("/stream").toString(), result?.streamUrl)
            assertEquals("abc-123", result?.radioBrowserUuid)
        }

    @Test
    fun `resolve skips a directory match with a different homepage and scrapes the page instead`() =
        runTest {
            val homepageUrl = "http://myradio.test:${server.port}/"
            val searchBody =
                """[{"name":"Other Station","url":"http://elsewhere.example/stream","homepage":"https://not-a-match.example/"}]"""
            server.enqueue(MockResponse().setBody(searchBody))
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><audio src="/stream.mp3"></audio></body></html>""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200))

            val result = resolver().resolve(homepageUrl)

            assertEquals("http://myradio.test:${server.port}/stream.mp3", result?.streamUrl)
        }

    @Test
    fun `resolve extracts a stream url from an audio tag when the directory has no keyword to search`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><audio src="${server.url("/stream.mp3")}"></audio></body></html>""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200))

            val result = resolver().resolve(server.url("/").toString())

            assertEquals(server.url("/stream.mp3").toString(), result?.streamUrl)
            assertTrue(result?.isHls == false)
        }

    @Test
    fun `resolve follows a linked pls playlist to the stream url inside it`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><a href="/listen.pls">Listen</a></body></html>""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    [playlist]
                    File1=${server.url("/stream.mp3")}
                    Title1=My Radio
                    NumberOfEntries=1
                    """.trimIndent(),
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200))

            val result = resolver().resolve(server.url("/").toString())

            assertEquals(server.url("/stream.mp3").toString(), result?.streamUrl)
        }

    @Test
    fun `resolve follows a linked m3u playlist to its first url line`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><a href="/listen.m3u">Listen</a></body></html>""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    #EXTM3U
                    ${server.url("/stream.mp3")}
                    """.trimIndent(),
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200))

            val result = resolver().resolve(server.url("/").toString())

            assertEquals(server.url("/stream.mp3").toString(), result?.streamUrl)
        }

    @Test
    fun `resolve returns null when the homepage has no stream-like url anywhere`() =
        runTest {
            server.enqueue(MockResponse().setBody("<html><body><p>Just a website.</p></body></html>"))

            val result = resolver().resolve(server.url("/").toString())

            assertNull(result)
        }

    @Test
    fun `resolve returns null when the only candidate on the page is unreachable`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """<html><body><audio src="http://127.0.0.1:1/stream.mp3"></audio></body></html>""",
                ),
            )

            val result = resolver().resolve(server.url("/").toString())

            assertNull(result)
        }

    @Test
    fun `extractCandidates finds a stream_url json value and captures its context`() {
        val text = """window.streams = {"jazz":{"title":"Jazz FM","stream_url":"https://example.com/jazz.mp3"}}"""

        val candidates = resolver().extractCandidates(text, "https://example.com/")

        val match = candidates.firstOrNull { it.url == "https://example.com/jazz.mp3" }
        assertTrue(match != null)
        assertTrue(match!!.context.contains("Jazz FM"))
    }

    @Test
    fun `extractCandidates finds an m3u8 url embedded directly in script text`() {
        val text = """const player = {hls: "https://stream-test.example.com/hls/main.m3u8"};"""

        val candidates = resolver().extractCandidates(text, "https://example.com/")

        assertTrue(candidates.any { it.url == "https://stream-test.example.com/hls/main.m3u8" })
    }

    @Test
    fun `rank prefers the candidate whose nearby text overlaps the domain label`() {
        val candidates =
            listOf(
                StationUrlResolver.Candidate(
                    "https://nashe1.hostingradio.ru/nashe-128.mp3",
                    context = """"title":"NASHE Radio",""",
                ),
                StationUrlResolver.Candidate(
                    "https://jfm1.hostingradio.ru/jazz.mp3",
                    context = """"title":"Jazz FM",""",
                ),
            )

        val ranked = resolver().rank(candidates, "radiojazzfm.ru")

        assertEquals("https://jfm1.hostingradio.ru/jazz.mp3", ranked.first())
    }

    @Test
    fun `rank prefers a same-subdomain host over an unrelated third-party host`() {
        val candidates =
            listOf(
                StationUrlResolver.Candidate("https://partneraudio.wavefarm.org/reveil.mp3", context = ""),
                StationUrlResolver.Candidate("https://stream-test.hkcr.live/hls/main.m3u8", context = ""),
            )

        val ranked = resolver().rank(candidates, "hkcr.live")

        assertEquals("https://stream-test.hkcr.live/hls/main.m3u8", ranked.first())
    }

    @Test
    fun `searchKeyword extracts the second-level label`() {
        assertEquals("silver", resolver().searchKeyword("silver.ru"))
        assertEquals("kursradio", resolver().searchKeyword("kursradio.live"))
    }

    @Test
    fun `searchKeyword returns null for a single-label host`() {
        assertNull(resolver().searchKeyword("localhost"))
    }

    @Test
    fun `searchKeyword returns null when the label is too short to be meaningful`() {
        assertNull(resolver().searchKeyword("a.ru"))
    }

    @Test
    fun `hostOf strips scheme, www and trailing slash`() {
        assertEquals("silver.ru", resolver().hostOf("https://www.silver.ru/"))
        assertEquals("kursradio.live", resolver().hostOf("kursradio.live"))
    }

    @Test
    fun `hostOf returns null for an unparseable url`() {
        assertNull(resolver().hostOf(""))
    }

    @Test
    fun `panelStreamUrl parses an AzuraCast nowplaying response`() {
        val body =
            """[{"listen_url":"${server.url("/listen/station/radio.mp3")}"}]"""
        server.enqueue(MockResponse().setBody(body))

        val result = resolver().panelStreamUrl(server.url("/").toString().trimEnd('/'))

        assertEquals(server.url("/listen/station/radio.mp3").toString(), result)
    }

    @Test
    fun `panelStreamUrl parses an Icecast status-json response with an object source`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val body = """{"icestats":{"source":{"listenurl":"${server.url("/stream.mp3")}"}}}"""
        server.enqueue(MockResponse().setBody(body))

        val result = resolver().panelStreamUrl(server.url("/").toString().trimEnd('/'))

        assertEquals(server.url("/stream.mp3").toString(), result)
    }

    @Test
    fun `panelStreamUrl parses an Icecast status-json response with an array of sources`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val body = """{"icestats":{"source":[{"listenurl":"${server.url("/stream.mp3")}"}]}}"""
        server.enqueue(MockResponse().setBody(body))

        val result = resolver().panelStreamUrl(server.url("/").toString().trimEnd('/'))

        assertEquals(server.url("/stream.mp3").toString(), result)
    }

    @Test
    fun `panelStreamUrl returns null when neither AzuraCast nor Icecast endpoints respond`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val result = resolver().panelStreamUrl(server.url("/").toString().trimEnd('/'))

        assertNull(result)
    }
}
