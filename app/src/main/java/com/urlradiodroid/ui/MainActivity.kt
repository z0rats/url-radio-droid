package com.urlradiodroid.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urlradiodroid.R
import com.urlradiodroid.data.AppDatabase
import com.urlradiodroid.data.RadioStation
import com.urlradiodroid.databinding.ActivityMainBinding
import com.urlradiodroid.databinding.BottomNowPlayingBinding
import com.urlradiodroid.util.EmojiGenerator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StationAdapter
    private lateinit var database: AppDatabase
    private var allStations: List<RadioStation> = emptyList()
    private var filteredStations: List<RadioStation> = emptyList()

    private var playbackService: RadioPlaybackService? = null
    private var isBound = false
    private var currentPlayingStation: RadioStation? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNowPlayingPopup()
            handler.postDelayed(this, 500) // Update every 500ms
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RadioPlaybackService.LocalBinder
            playbackService = binder?.getService()
            isBound = true

            // If we have a current playing station, update popup
            if (currentPlayingStation != null) {
                // Small delay to ensure service is ready
                handler.postDelayed({
                    updateNowPlayingPopup()
                }, 200)
            } else {
                // Try to restore from service
                val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
                if (currentMediaId != null && currentPlayingStation == null) {
                    currentPlayingStation = allStations.find { it.streamUrl == currentMediaId }
                    if (currentPlayingStation != null) {
                        handler.postDelayed({
                            updateNowPlayingPopup()
                        }, 200)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
            updateNowPlayingPopup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        database = AppDatabase.getDatabase(this)

        adapter = StationAdapter(
            onPlayClick = { station ->
                playStation(station)
            },
            onStationLongClick = { station ->
                // Long press opens edit (settings) - handled in adapter now
            },
            onStationEdit = { station ->
                val intent = Intent(this, AddStationActivity::class.java).apply {
                    putExtra(AddStationActivity.EXTRA_STATION_ID, station.id)
                }
                startActivity(intent)
            },
            onStationDelete = { station ->
                showDeleteConfirmation(station)
            }
        )

        setupNowPlayingPopup()

        binding.recyclerViewStations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewStations.adapter = adapter

        // Add smooth animations for list items
        binding.recyclerViewStations.itemAnimator = jp.wasabeef.recyclerview.animators.SlideInUpAnimator(
            OvershootInterpolator(1.0f)
        ).apply {
            addDuration = 300
            removeDuration = 250
            moveDuration = 250
            changeDuration = 200
        }

        // Optimize RecyclerView for large lists
        binding.recyclerViewStations.setHasFixedSize(true)
        binding.recyclerViewStations.setItemViewCacheSize(20)
        binding.recyclerViewStations.recycledViewPool.setMaxRecycledViews(0, 20)

        setupSearch()
        setupScrollListener()

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddStationActivity::class.java))
        }

        loadStations()
    }

    private fun setupSearch() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStations(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupScrollListener() {
        binding.recyclerViewStations.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // If scrolling up (dy < 0) and search has focus, clear focus
                if (dy < 0 && binding.editTextSearch.hasFocus()) {
                    binding.editTextSearch.clearFocus()
                    // Hide keyboard
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
                }
            }
        })
    }

    private fun filterStations(query: String) {
        val queryLower = query.lowercase().trim()
        filteredStations = if (queryLower.isEmpty()) {
            allStations
        } else {
            allStations.filter { station ->
                // Search by name (partial match) OR full URL (exact match)
                station.name.lowercase().contains(queryLower) ||
                station.streamUrl.lowercase() == queryLower
            }
        }
        adapter.submitList(filteredStations)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = filteredStations.isEmpty()
        val hasSearchQuery = binding.editTextSearch.text?.toString()?.isNotBlank() == true
        val shouldShow = isEmpty && !hasSearchQuery

        if (shouldShow && binding.layoutEmpty.visibility != View.VISIBLE) {
            // Animate in with scale and fade
            binding.layoutEmpty.alpha = 0f
            binding.layoutEmpty.scaleX = 0.8f
            binding.layoutEmpty.scaleY = 0.8f
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.layoutEmpty.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        } else if (!shouldShow && binding.layoutEmpty.visibility == View.VISIBLE) {
            // Animate out
            binding.layoutEmpty.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .withEndAction {
                    binding.layoutEmpty.visibility = View.GONE
                }
                .start()
        }
    }

    private fun setupWindowInsets() {
        // Apply insets to AppBarLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Apply insets to search input if visible
        ViewCompat.setOnApplyWindowInsetsListener(binding.textInputLayoutSearch) { v, insets ->
            // Search bar already has proper margins, no additional insets needed
            insets
        }

        // Apply insets to RecyclerView - add bottom padding for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewStations) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navigationBars.bottom + v.paddingBottom
            )
            insets
        }

        // Apply insets to FAB - position above navigation bar and popup
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val layoutParams = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            // Add extra margin if popup is visible
            val popupHeight = if (binding.includeNowPlaying.root.visibility == View.VISIBLE) {
                binding.includeNowPlaying.root.height
            } else {
                0
            }
            layoutParams?.bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.fab_margin) + popupHeight
            v.requestLayout()
            insets
        }

        // Apply insets to now playing popup - position above navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.includeNowPlaying.root) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val layoutParams = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            // Position above navigation bar with spacing (marginEnd already set in XML to leave space for FAB)
            layoutParams?.bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.spacing_2)
            v.requestLayout()
            insets
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
        loadStations()
        // Small delay to ensure stations are loaded before checking playback state
        handler.postDelayed({
            updateNowPlayingPopup()
            handler.post(updateRunnable)
        }, 100)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun loadStations() {
        lifecycleScope.launch {
            allStations = database.radioStationDao().getAllStations()

            // Show search bar only if there are more than 4 stations with animation
            val shouldShowSearch = allStations.size > 4
            if (shouldShowSearch && binding.textInputLayoutSearch.visibility != View.VISIBLE) {
                binding.textInputLayoutSearch.alpha = 0f
                binding.textInputLayoutSearch.visibility = View.VISIBLE
                binding.textInputLayoutSearch.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            } else if (!shouldShowSearch && binding.textInputLayoutSearch.visibility == View.VISIBLE) {
                binding.textInputLayoutSearch.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        binding.textInputLayoutSearch.visibility = View.GONE
                    }
                    .start()
            }

            // Apply current search filter or show all stations
            val currentQuery = binding.editTextSearch.text?.toString() ?: ""
            if (currentQuery.isBlank()) {
                filteredStations = allStations
                adapter.submitList(allStations)
            } else {
                filterStations(currentQuery)
            }

            updateEmptyState()
        }
    }


    private fun showDeleteConfirmation(station: com.urlradiodroid.data.RadioStation) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_station))
            .setMessage(getString(R.string.delete_station_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteStation(station)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteStation(station: com.urlradiodroid.data.RadioStation) {
        lifecycleScope.launch {
            try {
                database.radioStationDao().deleteStation(station.id)
                Toast.makeText(this@MainActivity, getString(R.string.station_deleted), Toast.LENGTH_SHORT).show()
                // Clear search when deleting to show updated list
                binding.editTextSearch.setText("")
                loadStations()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNowPlayingPopup() {
        val nowPlayingBinding = BottomNowPlayingBinding.bind(binding.includeNowPlaying.root)

        // Click on popup opens PlaybackActivity
        nowPlayingBinding.cardNowPlaying.setOnClickListener {
            currentPlayingStation?.let { station ->
                val intent = Intent(this, PlaybackActivity::class.java).apply {
                    putExtra(PlaybackActivity.EXTRA_STATION_ID, station.id)
                    putExtra(PlaybackActivity.EXTRA_STATION_NAME, station.name)
                    putExtra(PlaybackActivity.EXTRA_STREAM_URL, station.streamUrl)
                }
                startActivity(intent)
            }
        }

        // Play/Pause button in popup
        nowPlayingBinding.imageViewNowPlayingControl.setOnClickListener {
            togglePlayback()
        }
    }

    private fun playStation(station: RadioStation) {
        // If clicking on the same station that's playing, stop it
        if (currentPlayingStation?.id == station.id && playbackService?.isPlaying() == true) {
            playbackService?.stopPlayback()
            currentPlayingStation = null
            adapter.updateCurrentPlayingStation(null)
            updateNowPlayingPopup()
            return
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            return
        }

        currentPlayingStation = station

        Intent(this, RadioPlaybackService::class.java).apply {
            putExtra(RadioPlaybackService.EXTRA_STATION_NAME, station.name)
            putExtra(RadioPlaybackService.EXTRA_STREAM_URL, station.streamUrl)
            startForegroundService(this)
        }

        // Bind to service to get updates
        if (!isBound) {
            Intent(this, RadioPlaybackService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }

        // Update popup immediately to show it, then update again after service starts
        updateNowPlayingPopup()
        handler.postDelayed({
            updateNowPlayingPopup()
        }, 500)
    }

    private fun togglePlayback() {
        val service = playbackService ?: return

        if (service.isPlaying()) {
            service.stopPlayback()
            currentPlayingStation = null
            adapter.updateCurrentPlayingStation(null)
        } else {
            currentPlayingStation?.let { station ->
                playStation(station)
            }
        }

        updateNowPlayingPopup()
    }

    private fun updateNowPlayingPopup() {
        val nowPlayingBinding = BottomNowPlayingBinding.bind(binding.includeNowPlaying.root)
        val isPlaying = playbackService?.isPlaying() ?: false

        // If service stopped but we still have station, try to restore from service
        if (!isPlaying && currentPlayingStation == null && playbackService != null) {
            val currentMediaId = playbackService?.getPlayer()?.currentMediaItem?.mediaId
            if (currentMediaId != null) {
                // Try to find station by URL
                currentPlayingStation = allStations.find { it.streamUrl == currentMediaId }
            }
        }

        // Show popup if we have a current playing station (even if service not yet connected)
        // or if service is actually playing
        val shouldShowPopup = (currentPlayingStation != null) || isPlaying

        // Update adapter with current playing station ID
        val playingStationId = if (shouldShowPopup && currentPlayingStation != null) {
            currentPlayingStation?.id
        } else {
            null
        }
        adapter.updateCurrentPlayingStation(playingStationId)

        if (shouldShowPopup && currentPlayingStation != null) {
            val station = currentPlayingStation!!

            // Update popup content
            nowPlayingBinding.textViewNowPlayingName.text = station.name
            nowPlayingBinding.textViewNowPlayingEmoji.text = station.customIcon
                ?: EmojiGenerator.getEmojiForStation(station.name, station.streamUrl)

            // Update play/stop icon - show stop if actually playing, otherwise play
            val actuallyPlaying = playbackService?.isPlaying() ?: false
            if (actuallyPlaying) {
                nowPlayingBinding.imageViewNowPlayingControl.setImageResource(R.drawable.ic_stop_circle)
                nowPlayingBinding.imageViewNowPlayingControl.contentDescription = getString(R.string.stop)
            } else {
                nowPlayingBinding.imageViewNowPlayingControl.setImageResource(R.drawable.ic_play_circle)
                nowPlayingBinding.imageViewNowPlayingControl.contentDescription = getString(R.string.play)
            }

            // Show popup with animation
            if (binding.includeNowPlaying.root.visibility != View.VISIBLE) {
                binding.includeNowPlaying.root.alpha = 0f
                binding.includeNowPlaying.root.translationY = 100f
                binding.includeNowPlaying.root.visibility = View.VISIBLE
                binding.includeNowPlaying.root.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .withEndAction {
                        updateFabPosition()
                    }
                    .start()
            } else {
                updateFabPosition()
            }
        } else {
            // Hide popup with animation
            if (binding.includeNowPlaying.root.visibility == View.VISIBLE) {
                binding.includeNowPlaying.root.animate()
                    .alpha(0f)
                    .translationY(100f)
                    .setDuration(200)
                    .withEndAction {
                        binding.includeNowPlaying.root.visibility = View.GONE
                        updateFabPosition()
                    }
                    .start()
            } else {
                updateFabPosition()
            }
            currentPlayingStation = null
        }
    }

    private fun updateFabPosition() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { v, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val layoutParams = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            // Position FAB at same level as popup (not above it) to avoid overlap
            // Both should be above navigation bar
            layoutParams?.bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.fab_margin)
            v.requestLayout()
            insets
        }
        binding.fabAdd.requestLayout()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
