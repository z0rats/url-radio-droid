package com.urlradiodroid.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.statusBars
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
import com.urlradiodroid.ui.components.PlaybackStatus
import com.urlradiodroid.ui.components.StationItem
import com.urlradiodroid.ui.theme.URLRadioDroidTheme
import com.urlradiodroid.ui.theme.background_gradient_end
import com.urlradiodroid.ui.theme.background_gradient_mid
import com.urlradiodroid.ui.theme.background_gradient_start
import com.urlradiodroid.ui.theme.card_surface
import com.urlradiodroid.ui.theme.text_secondary

class MainActivity : ComponentActivity() {
    private val _playbackService = mutableStateOf<RadioPlaybackService?>(null)
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RadioPlaybackService.LocalBinder
            _playbackService.value = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _playbackService.value = null
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

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* Result handled by system; notification will show if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        val database = AppDatabase.getDatabase(this)
        val viewModelFactory = MainViewModel.provideFactory(database)

        setContent {
            val playbackService by _playbackService
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
                            if (viewModel.getCurrentPlayingStationId() == stationToDelete.id) {
                                _playbackService.value?.stopPlayback()
                                viewModel.updateCurrentPlayingStation(null)
                            }
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
                    onStopPlayback = {
                        _playbackService.value?.stopPlayback()
                    },
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
        val isCurrentlyPlayingThis = viewModelRef?.getCurrentPlayingStationId() == station.id &&
                _playbackService.value?.isPlaying() == true
        if (isCurrentlyPlayingThis) {
            _playbackService.value?.stopPlayback()
            return
        }

        if (!isNetworkAvailable()) {
            android.widget.Toast.makeText(this, getString(R.string.error_network), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

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
    onStopPlayback: () -> Unit,
    playbackService: RadioPlaybackService?,
    onResume: () -> Unit
) {
    val stations by viewModel.filteredStations.collectAsState(initial = emptyList())
    val allStations by viewModel.stations.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlayingStationId by viewModel.currentPlayingStationId.collectAsState()

    // Find current playing station by ID
    val currentPlayingStation = remember(currentPlayingStationId, allStations) {
        currentPlayingStationId?.let { id ->
            allStations.find { it.id == id }
        }
    }

    // Update playing state and timeshift/live state periodically
    var isPlaying by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf(false) }
    var hasTimeshift by remember { mutableStateOf(false) }
    var isAtLive by remember { mutableStateOf(true) }
    var isBufferingCurrentStation by remember { mutableStateOf(false) }
    LaunchedEffect(currentPlayingStationId) {
        isStarting = true
        startError = false
    }
    LaunchedEffect(isStarting) {
        if (!isStarting) return@LaunchedEffect
        kotlinx.coroutines.delay(10_000)
        if (isStarting && !isPlaying) {
            startError = true
            isStarting = false
        }
    }
    LaunchedEffect(playbackService, currentPlayingStation) {
        while (true) {
            val svc = playbackService
            val serviceIsPlaying = svc?.isPlaying() ?: false
            val currentMediaId = svc?.getPlayer()?.currentMediaItem?.mediaId
            val isCurrentStationPlaying = currentPlayingStation != null &&
                    currentMediaId == currentPlayingStation.streamUrl &&
                    serviceIsPlaying
            isPlaying = isCurrentStationPlaying
            hasTimeshift = svc?.hasTimeshift() ?: false
            isAtLive = svc?.isAtLive() ?: true
            val buffering = svc?.isBuffering() ?: false
            isBufferingCurrentStation = buffering && currentMediaId == currentPlayingStation?.streamUrl
            if (isCurrentStationPlaying) {
                isStarting = false
                startError = false
            }
            kotlinx.coroutines.delay(200)
        }
    }

    val playbackStatus = when {
        startError -> PlaybackStatus.ERROR
        isPlaying -> PlaybackStatus.PLAYING
        isStarting -> PlaybackStatus.STARTING
        isBufferingCurrentStation -> PlaybackStatus.STARTING
        else -> PlaybackStatus.PAUSED
    }

    val onPlayStationWithState: (RadioStation) -> Unit = { station ->
        startError = false
        isStarting = true
        onPlayStation(station)
    }

    // Reload stations when screen is resumed
    LaunchedEffect(Unit) {
        onResume()
    }

    // Sync current playing station from service (single source of truth: ViewModel)
    LaunchedEffect(playbackService, allStations) {
        val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
        val serviceIsPlaying = playbackService?.isPlaying() ?: false
        if (currentMediaId != null && serviceIsPlaying) {
            val foundStation = allStations.find { it.streamUrl == currentMediaId }
            if (foundStation != null && currentPlayingStationId != foundStation.id) {
                viewModel.updateCurrentPlayingStation(foundStation.id)
            }
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
                        val isStationPlaying = currentPlayingStationId == station.id && isPlaying
                        NowPlayingBottomBar(
                            station = station,
                            stations = stations,
                            playbackStatus = playbackStatus,
                            hasTimeshift = hasTimeshift,
                            isAtLive = isAtLive,
                            onPlayPauseClick = {
                                if (isStationPlaying) onStopPlayback() else onPlayStationWithState(station)
                            },
                            onCardClick = { onNowPlayingClick(station) },
                            onSwitchStation = onPlayStationWithState,
                            onRewind5s = { playbackService?.seekBackward(5000L) },
                            onReturnToLive = {
                                isStarting = true
                                playbackService?.seekToLive()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
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
                            focusedContainerColor = card_surface,
                            unfocusedContainerColor = card_surface,
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
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = text_secondary
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
                            val isActive = currentPlayingStationId == station.id
                            val isStationPlaying = isActive && isPlaying
                            val isStationStarting = isActive && isStarting
                            val isStationStartError = isActive && startError
                            StationItem(
                                station = station,
                                isActive = isActive,
                                isPlaying = isStationPlaying,
                                isStarting = isStationStarting,
                                isStartError = isStationStartError,
                                onPlayClick = { onPlayStationWithState(station) },
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
