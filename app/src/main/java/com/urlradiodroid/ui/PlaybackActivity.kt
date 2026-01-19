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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import com.urlradiodroid.R
import com.urlradiodroid.databinding.ActivityPlaybackBinding
import com.urlradiodroid.util.EmojiGenerator

class PlaybackActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaybackBinding
    private var playbackService: RadioPlaybackService? = null
    private var isBound = false
    private var stationName: String? = null
    private var streamUrl: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updatePlayButton()
            handler.postDelayed(this, 500)
        }
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STREAM_URL = "stream_url"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioPlaybackService.LocalBinder
            playbackService = binder.getService()
            isBound = true

            // Update UI with current playing station from service if we don't have it from intent
            if (stationName == null || streamUrl == null) {
                val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
                if (currentMediaId != null) {
                    streamUrl = currentMediaId
                    // Try to get station name from service
                    val serviceStationName = playbackService?.getCurrentStationName()
                    if (serviceStationName != null) {
                        stationName = serviceStationName
                    }
                    updateUI()
                }
            }

            updatePlayButton()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is required for background playback",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        handleIntent(intent)

        binding.toolbarPlayback.setNavigationOnClickListener { finish() }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.buttonPlayStop.setOnClickListener {
            togglePlayback()
        }
    }

    private fun setupWindowInsets() {
        // Root layout doesn't need padding as toolbar and ScrollView handle it
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            insets
        }

        // Apply insets to toolbar - add top padding for status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarPlayback) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Apply insets to ScrollView - add bottom padding for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollViewContent) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navigationBars.bottom
            )
            insets
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent ?: return)
    }

    private fun handleIntent(intent: Intent?) {
        val intentStationName = intent?.getStringExtra(EXTRA_STATION_NAME)
        val intentStreamUrl = intent?.getStringExtra(EXTRA_STREAM_URL)

        // Priority: use data from intent (from notification click)
        // If intent has data, always use it (this handles notification clicks)
        if (intentStationName != null && intentStreamUrl != null) {
            stationName = intentStationName
            streamUrl = intentStreamUrl
            updateUI()
            return
        }

        // Fallback: if no intent data but we have service, get from service
        if (playbackService != null) {
            val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
            if (currentMediaId != null) {
                streamUrl = currentMediaId
                val serviceStationName = playbackService?.getCurrentStationName()
                if (serviceStationName != null) {
                    stationName = serviceStationName
                }
                updateUI()
                return
            }
        }

        // If we have partial data, use what we have
        if (intentStationName != null) {
            stationName = intentStationName
        }
        if (intentStreamUrl != null) {
            streamUrl = intentStreamUrl
        }

        updateUI()
    }

    private fun updateUI() {
        val displayName = stationName ?: getString(R.string.unknown_station)
        binding.toolbarPlayback.title = displayName
        binding.textViewStationName.text = displayName
        binding.textViewStationEmoji.text = EmojiGenerator.getEmojiForStation(displayName, streamUrl ?: "")
    }

    override fun onStart() {
        super.onStart()
        Intent(this, RadioPlaybackService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update UI in case service has different station playing
        if (playbackService != null) {
            val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
            if (currentMediaId != null && currentMediaId != streamUrl) {
                streamUrl = currentMediaId
                val serviceStationName = playbackService?.getCurrentStationName()
                if (serviceStationName != null) {
                    stationName = serviceStationName
                }
                updateUI()
            }
        }
        updatePlayButton()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
        // Don't unbind from service to keep playback running in background
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun togglePlayback() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            return
        }

        val url = streamUrl ?: return

        if (playbackService == null) {
            startService(url)
            return
        }

        val service = playbackService ?: return

        if (service.isPlaying()) {
            service.stopPlayback()
        } else {
            startService(url)
        }

        updatePlayButton()
    }

    private fun startService(url: String) {
        Intent(this, RadioPlaybackService::class.java).apply {
            putExtra(RadioPlaybackService.EXTRA_STATION_NAME, stationName)
            putExtra(RadioPlaybackService.EXTRA_STREAM_URL, url)
            startForegroundService(this)
        }
    }

    private fun updatePlayButton() {
        val isPlaying = playbackService?.isPlaying() ?: false
        binding.buttonPlayStop.text = if (isPlaying) {
            getString(R.string.stop)
        } else {
            getString(R.string.play)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
