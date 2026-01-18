package com.urlradiodroid.ui

import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.urlradiodroid.R
import com.urlradiodroid.databinding.ActivityPlaybackBinding

class PlaybackActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaybackBinding
    private var player: ExoPlayer? = null
    private var isPlaying = false
    private var stationName: String? = null
    private var streamUrl: String? = null
    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STREAM_URL = "stream_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        stationName = intent.getStringExtra(EXTRA_STATION_NAME)
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)

        binding.textViewStationName.text = stationName ?: "Неизвестная станция"

        initializePlayer()
        setupVolumeControl()

        binding.buttonPlayStop.setOnClickListener {
            togglePlayback()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        releasePlayer()
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    super.onPlayerError(error)
                    Toast.makeText(
                        this@PlaybackActivity,
                        getString(R.string.error_playback),
                        Toast.LENGTH_SHORT
                    ).show()
                    isPlaying = false
                    updatePlayButton()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    this@PlaybackActivity.isPlaying = isPlaying
                    updatePlayButton()
                }
            })
        }
    }

    private fun setupVolumeControl() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = (currentVolume * 100) / maxVolume

        binding.seekBarVolume.max = 100
        binding.seekBarVolume.progress = volumePercent

        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = (progress * maxVolume) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayback() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            return
        }

        val url = streamUrl ?: return
        val exoPlayer = player ?: return

        if (isPlaying) {
            exoPlayer.stop()
            isPlaying = false
        } else {
            try {
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                isPlaying = true
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.error_playback), Toast.LENGTH_SHORT).show()
            }
        }
        updatePlayButton()
    }

    private fun updatePlayButton() {
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

    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
            player = null
        }
        isPlaying = false
    }
}
