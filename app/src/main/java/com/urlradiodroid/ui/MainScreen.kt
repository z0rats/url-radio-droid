package com.urlradiodroid.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.ui.components.NowPlayingBottomBar
import com.urlradiodroid.ui.components.StationItem
import com.urlradiodroid.ui.theme.URLRadioDroidTheme
import com.urlradiodroid.ui.theme.background_gradient_end
import com.urlradiodroid.ui.theme.background_gradient_mid
import com.urlradiodroid.ui.theme.background_gradient_start
import com.urlradiodroid.ui.theme.text_secondary

class MainActivity : ComponentActivity() {
    private var playbackService: RadioPlaybackService? = null
    private var isBound = false
    private var currentPlayingStation: RadioStation? = null
    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RadioPlaybackService.LocalBinder
            playbackService = binder?.getService()
            isBound = true

            // Try to restore current playing station from service
            val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
            if (currentMediaId != null && currentPlayingStation == null) {
                // Will be handled in MainScreen LaunchedEffect
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    private var viewModelRef: MainViewModel? = null

    private val addStationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Reload stations when returning from AddStationActivity
            viewModelRef?.loadStations()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val viewModelFactory = MainViewModel.provideFactory(database)

        setContent {
            URLRadioDroidTheme {
                val viewModel: MainViewModel = viewModel(factory = viewModelFactory)
                viewModelRef = viewModel

                // Load stations on start
                LaunchedEffect(Unit) {
                    viewModel.loadStations()
                }

                MainScreen(
                    viewModel = viewModel,
                    onAddStationClick = {
                        addStationLauncher.launch(Intent(this, AddStationActivity::class.java))
                    },
                    onStationEdit = { station ->
                        val intent = Intent(this, AddStationActivity::class.java).apply {
                            putExtra(AddStationActivity.EXTRA_STATION_ID, station.id)
                        }
                        addStationLauncher.launch(intent)
                    },
                    onResume = {
                        viewModel.loadStations()
                    },
                    onStationDelete = { station ->
                        showDeleteConfirmation(station) { stationToDelete ->
                            viewModel.deleteStation(stationToDelete.id)
                        }
                    },
                    onPlayStation = { station ->
                        playStation(station, viewModel)
                    },
                    onNowPlayingClick = { station ->
                        val intent = Intent(this, PlaybackActivity::class.java).apply {
                            putExtra(PlaybackActivity.EXTRA_STATION_ID, station.id)
                            putExtra(PlaybackActivity.EXTRA_STATION_NAME, station.name)
                            putExtra(PlaybackActivity.EXTRA_STREAM_URL, station.streamUrl)
                        }
                        startActivity(intent)
                    },
                    playbackService = playbackService,
                    onCurrentPlayingStationChanged = { station ->
                        currentPlayingStation = station
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, RadioPlaybackService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload stations when returning from AddStationActivity
        viewModelRef?.loadStations()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun playStation(station: RadioStation, viewModel: MainViewModel) {
        // If clicking on the same station that's playing, stop it
        if (currentPlayingStation?.id == station.id && playbackService?.isPlaying() == true) {
            playbackService?.stopPlayback()
            currentPlayingStation = null
            viewModel.updateCurrentPlayingStation(null)
            return
        }

        if (!isNetworkAvailable()) {
            android.widget.Toast.makeText(this, getString(R.string.error_network), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        currentPlayingStation = station
        viewModel.updateCurrentPlayingStation(station.id)

        Intent(this, RadioPlaybackService::class.java).apply {
            putExtra(RadioPlaybackService.EXTRA_STATION_NAME, station.name)
            putExtra(RadioPlaybackService.EXTRA_STREAM_URL, station.streamUrl)
            startForegroundService(this)
        }

        if (!isBound) {
            Intent(this, RadioPlaybackService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    private fun showDeleteConfirmation(station: RadioStation, onConfirm: (RadioStation) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_station))
            .setMessage(getString(R.string.delete_station_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                onConfirm(station)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onAddStationClick: () -> Unit,
    onStationEdit: (RadioStation) -> Unit,
    onStationDelete: (RadioStation) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onNowPlayingClick: (RadioStation) -> Unit,
    playbackService: RadioPlaybackService?,
    onCurrentPlayingStationChanged: (RadioStation?) -> Unit,
    onResume: () -> Unit
) {
    val stations by viewModel.filteredStations.collectAsState(initial = emptyList())
    val allStations by viewModel.stations.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlayingStationId by viewModel.currentPlayingStationId.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }

    // Update playing state periodically
    LaunchedEffect(playbackService) {
        while (true) {
            isPlaying = playbackService?.isPlaying() ?: false
            kotlinx.coroutines.delay(200)
        }
    }

    // Find current playing station by ID
    val currentPlayingStation = remember(currentPlayingStationId, allStations) {
        currentPlayingStationId?.let { id ->
            allStations.find { it.id == id }
        }
    }

    // Reload stations when screen is resumed
    LaunchedEffect(Unit) {
        onResume()
    }

    // Update current playing station from service
    LaunchedEffect(playbackService, isPlaying, allStations) {
        val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
        if (currentMediaId != null) {
            // Try to find station by URL
            val foundStation = allStations.find { it.streamUrl == currentMediaId }
            if (foundStation != null) {
                if (currentPlayingStationId != foundStation.id) {
                    onCurrentPlayingStationChanged(foundStation)
                    viewModel.updateCurrentPlayingStation(foundStation.id)
                }
            }
        } else if (!isPlaying && currentPlayingStationId != null) {
            // Service stopped - keep station visible for a moment
            // Don't clear immediately to allow user to restart
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        background_gradient_start,
                        background_gradient_mid,
                        background_gradient_end
                    )
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddStationClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_station)
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = currentPlayingStation != null) {
                    currentPlayingStation?.let { station ->
                        NowPlayingBottomBar(
                            station = station,
                            isPlaying = isPlaying,
                            onPlayPauseClick = {
                                if (isPlaying) {
                                    playbackService?.stopPlayback()
                                    onCurrentPlayingStationChanged(null)
                                    viewModel.updateCurrentPlayingStation(null)
                                } else {
                                    onPlayStation(station)
                                }
                            },
                            onCardClick = {
                                currentPlayingStation?.let { station ->
                                    onNowPlayingClick(station)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedVisibility(visible = viewModel.stations.value.size > 4) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_stations)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            focusedTextColor = text_secondary,
                            unfocusedTextColor = text_secondary
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                }

                if (stations.isEmpty() && searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "📻",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                text = stringResource(R.string.no_stations),
                                style = MaterialTheme.typography.bodyLarge,
                                color = text_secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            items = stations,
                            key = { it.id }
                        ) { station ->
                            StationItem(
                                station = station,
                                isPlaying = currentPlayingStationId == station.id,
                                onPlayClick = { onPlayStation(station) },
                                onEditClick = { onStationEdit(station) },
                                onDeleteClick = { onStationDelete(station) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
