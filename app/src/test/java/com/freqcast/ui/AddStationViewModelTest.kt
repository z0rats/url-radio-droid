package com.freqcast.ui

import androidx.room.Room
import com.freqcast.R
import com.freqcast.data.AppDatabase
import com.freqcast.data.RadioStation
import com.freqcast.data.RadioStationRepository
import com.freqcast.data.StationUrlResolver
import com.freqcast.util.StreamValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * [StreamValidator] hops onto the real `Dispatchers.IO` for its MockWebServer round-trip, off the
 * virtual test scheduler — so, like [MainViewModelTest], assertions after [AddStationViewModel.save]
 * use [awaitTrue] (a real-time poll) rather than a single [advanceUntilIdle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AddStationViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RadioStationRepository
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                // See DiscoverStationsViewModelTest: keeps Room's suspend DAO calls off its own
                // real thread pool so they can't race the virtual test dispatcher.
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        repository = RadioStationRepository(database.radioStationDao())
        server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        scheduler: TestCoroutineScheduler,
        editingStationId: Long? = null,
        stationUrlResolver: StationUrlResolver? = null,
    ): AddStationViewModel {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val streamValidator = StreamValidator(client = OkHttpClient())
        return AddStationViewModel(
            repository,
            editingStationId,
            streamValidator,
            stationUrlResolver ?: StationUrlResolver(streamValidator = streamValidator),
        )
    }

    private suspend fun TestScope.awaitTrue(
        timeoutMs: Long = 5000L,
        poll: () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!poll()) {
                advanceUntilIdle()
            }
        }
    }

    @Test
    fun `selecting an emoji sets it as the custom icon`() =
        runTest {
            val viewModel = createViewModel(testScheduler)

            viewModel.onEmojiIconSelected("🎸")

            assertEquals("🎸", viewModel.uiState.value.customIcon)
        }

    @Test
    fun `selecting an image path sets it as the custom icon`() =
        runTest {
            val viewModel = createViewModel(testScheduler)

            viewModel.onImageIconSelected("/data/user/0/com.freqcast/files/station_icons/a.jpg")

            assertEquals("/data/user/0/com.freqcast/files/station_icons/a.jpg", viewModel.uiState.value.customIcon)
        }

    @Test
    fun `removing the icon clears it`() =
        runTest {
            val viewModel = createViewModel(testScheduler)
            viewModel.onEmojiIconSelected("🎸")

            viewModel.onRemoveIcon()

            assertNull(viewModel.uiState.value.customIcon)
        }

    @Test
    fun `editing a station loads its existing custom icon`() =
        runTest {
            val id =
                repository.insertStation(
                    RadioStation(name = "Jazz FM", streamUrl = "http://example.com", customIcon = "🎷"),
                )
            val viewModel = createViewModel(testScheduler, editingStationId = id)

            awaitTrue { viewModel.uiState.value.customIcon == "🎷" }
        }

    @Test
    fun `editing a station loads its existing genre`() =
        runTest {
            val id =
                repository.insertStation(
                    RadioStation(name = "Jazz FM", streamUrl = "http://example.com", genre = "jazz,smooth"),
                )
            val viewModel = createViewModel(testScheduler, editingStationId = id)

            awaitTrue { viewModel.uiState.value.genre == "jazz,smooth" }
        }

    @Test
    fun `saving a new station persists the entered genre`() =
        runTest {
            val viewModel = createViewModel(testScheduler)
            viewModel.onNameChange("New FM")
            viewModel.onUrlChange(server.url("/stream").toString())
            viewModel.onGenreChange("rock")

            viewModel.save()

            // isSaving starts false, so polling !isSaving alone would race ahead of the save
            // (see the class doc); waiting for the true->false transition avoids that. Can't poll
            // the DB row directly either: a suspend call inside the poll condition would itself
            // suspend on Room's real executor thread, and since nothing else drives the virtual
            // scheduler forward while we're suspended, save()'s own coroutine never progresses -
            // a deadlock only `withTimeout` breaks out of.
            awaitTrue { viewModel.uiState.value.isSaving }
            awaitTrue { !viewModel.uiState.value.isSaving }
            assertEquals("rock", database.radioStationDao().getAllStations()[0].genre)
        }

    @Test
    fun `pasting a station homepage resolves and saves the stream url found on the page`() =
        runTest {
            // A dedicated server, rather than the class's shared `server` (which setup() pre-seeds
            // with one always-200 response for the simple direct-stream-url tests), so this test
            // controls its exact request/response sequence: HEAD on the homepage (content-type
            // check), GET on the homepage (the actual scrape), then HEAD on the stream it finds.
            val homepageServer = MockWebServer()
            homepageServer.start()
            try {
                homepageServer.enqueue(MockResponse().setHeader("Content-Type", "text/html"))
                homepageServer.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody(
                            """<html><body><audio src="${homepageServer.url("/stream.mp3")}"></audio></body></html>""",
                        ),
                )
                homepageServer.enqueue(MockResponse().setResponseCode(200))

                val viewModel = createViewModel(testScheduler)
                viewModel.onNameChange("New FM")
                viewModel.onUrlChange(homepageServer.url("/").toString())

                viewModel.save()

                awaitTrue { viewModel.uiState.value.isSaving }
                awaitTrue { !viewModel.uiState.value.isSaving }
                assertEquals(homepageServer.url("/stream.mp3").toString(), viewModel.uiState.value.url)
                assertEquals(
                    homepageServer.url("/stream.mp3").toString(),
                    database.radioStationDao().getAllStations()[0].streamUrl,
                )
            } finally {
                homepageServer.shutdown()
            }
        }

    @Test
    fun `pasting a website with no discoverable stream shows the unreachable error`() =
        runTest {
            val homepageServer = MockWebServer()
            homepageServer.start()
            try {
                homepageServer.enqueue(MockResponse().setHeader("Content-Type", "text/html"))
                homepageServer.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><body><p>Just a website, no player here.</p></body></html>"),
                )

                val viewModel = createViewModel(testScheduler)
                viewModel.onNameChange("New FM")
                viewModel.onUrlChange(homepageServer.url("/").toString())

                viewModel.save()

                awaitTrue { viewModel.uiState.value.isSaving }
                awaitTrue { !viewModel.uiState.value.isSaving }
                assertEquals(R.string.error_stream_unreachable, viewModel.uiState.value.urlErrorRes)
            } finally {
                homepageServer.shutdown()
            }
        }

    @Test
    fun `editing a station keeps its manual list position after save`() =
        runTest {
            // insertStation() auto-assigns the next sortOrder; add two others first so this one
            // doesn't land at 0 by coincidence and mask a regression.
            repository.insertStation(RadioStation(name = "First", streamUrl = "http://example.com/first"))
            repository.insertStation(RadioStation(name = "Second", streamUrl = "http://example.com/second"))
            val id =
                repository.insertStation(
                    RadioStation(name = "Jazz FM", streamUrl = server.url("/stream").toString()),
                )
            val originalSortOrder = repository.getStationById(id)!!.sortOrder
            val viewModel = createViewModel(testScheduler, editingStationId = id)
            // Wait for init's Room load to populate sortOrder before saving, same reasoning as the
            // icon-replacement test above (Room's suspend calls hop off the virtual test scheduler).
            awaitTrue { viewModel.uiState.value.sortOrder == originalSortOrder }

            viewModel.onGenreChange("smooth jazz")
            viewModel.save()

            // See the true->false isSaving reasoning in the test above.
            awaitTrue { viewModel.uiState.value.isSaving }
            awaitTrue { !viewModel.uiState.value.isSaving }
            assertEquals(originalSortOrder, repository.getStationById(id)?.sortOrder)
        }

    @Test
    fun `saving with a replaced image icon deletes the old icon file`() =
        runTest {
            val iconsDir = File(RuntimeEnvironment.getApplication().filesDir, "station_icons").apply { mkdirs() }
            val oldIconFile = File(iconsDir, "old.jpg").apply { writeText("fake-jpeg-bytes") }
            val id =
                repository.insertStation(
                    RadioStation(
                        name = "Jazz FM",
                        streamUrl = server.url("/stream").toString(),
                        customIcon = oldIconFile.absolutePath,
                    ),
                )
            val viewModel = createViewModel(testScheduler, editingStationId = id)
            // Wait for the ViewModel's init block to finish loading the station (including
            // originalCustomIcon, used by save()'s cleanup check) before triggering a save —
            // a single advanceUntilIdle() isn't enough since Room's suspend DAO calls hop onto
            // their own executor thread, off the virtual test scheduler.
            awaitTrue { viewModel.uiState.value.customIcon == oldIconFile.absolutePath }
            assertTrue(oldIconFile.exists())

            viewModel.onEmojiIconSelected("🎷")
            viewModel.save()

            awaitTrue { !oldIconFile.exists() }
        }
}
