package com.urlradiodroid.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

import com.urlradiodroid.R
import com.urlradiodroid.ui.playback.LiveFileDataSource
import com.urlradiodroid.ui.playback.StreamRecorder
import com.urlradiodroid.util.EmojiGenerator
import java.io.File

@UnstableApi
class RadioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var notificationManager: PlayerNotificationManager? = null
    private var stationName: String? = null

    private var timeshiftBufferFile: File? = null
    private var streamRecorder: StreamRecorder? = null
    private var atLiveEdge: Boolean = true

    /** Estimated playback position (ms) for timeshift seek when player reports 0 for live-style source. */
    private var lastSeekPositionMs: Long = 0L
    private var lastSeekTimeMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RadioPlaybackService = this@RadioPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        setupNotificationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stationName = intent?.getStringExtra(EXTRA_STATION_NAME)
        val streamUrl = intent?.getStringExtra(EXTRA_STREAM_URL)

        if (streamUrl != null) {
            startPlayback(streamUrl)
        } else {
            // If no URL - stop
            stopSelf()
        }

        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        // Create notification channel with default importance to ensure visibility
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.app_name)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Update notification when playback state changes
                    notificationManager?.invalidate()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Ensure notification is shown when player is ready or playing
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        notificationManager?.invalidate()
                    }
                }
            })
        }

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .build()
    }

    private fun setupNotificationManager() {
        val exoPlayer = player ?: return

        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        ).setMediaDescriptionAdapter(
            object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return stationName ?: getString(R.string.unknown_station)
                }

                override fun getCurrentContentText(player: Player): CharSequence {
                    return getString(R.string.app_name)
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): android.graphics.Bitmap? {
                    val emoji = EmojiGenerator.getEmojiForStation(
                        stationName ?: getString(R.string.unknown_station),
                        player.currentMediaItem?.mediaId ?: ""
                    )
                    return EmojiGenerator.getEmojiBitmap(emoji, 128)
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val openIntent = Intent(this@RadioPlaybackService, PlaybackActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(PlaybackActivity.EXTRA_STATION_NAME, stationName)
                        putExtra(PlaybackActivity.EXTRA_STREAM_URL, player.currentMediaItem?.mediaId)
                    }

                    return PendingIntent.getActivity(
                        this@RadioPlaybackService,
                        0,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            }
        ).setNotificationListener(object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopSelf()
            }

            override fun onNotificationPosted(
                notificationId: Int,
                notification: android.app.Notification,
                ongoing: Boolean
            ) {
                // Start foreground service with notification to keep playback running
                if (ongoing) {
                    startForeground(notificationId, notification)
                }
            }
        }).build().apply {
            setPlayer(exoPlayer)
        }
    }

    private fun startPlayback(streamUrl: String) {
        val exoPlayer = player ?: return

        // Stop previous recorder and delete its file (handles station switch: new URL = new file)
        stopTimeshiftRecorder()
        val bufferFile = File(cacheDir, "timeshift_${streamUrl.hashCode().toString(36)}.tmp")
        bufferFile.createNewFile()
        timeshiftBufferFile = bufferFile
        val recorder = StreamRecorder(streamUrl, bufferFile)
        streamRecorder = recorder
        atLiveEdge = true
        lastSeekPositionMs = 0L
        lastSeekTimeMs = System.currentTimeMillis()
        recorder.start {
            mainHandler.post {
                markConnectionError()
                stopPlayback()
            }
        }

        val dataSourceFactory = LiveFileDataSource.Factory(
            file = bufferFile,
            currentLengthSupplier = { recorder.getCurrentLength() }
        )
        val mediaItem = MediaItem.Builder()
            .setMediaId(streamUrl)
            .setUri(Uri.fromFile(bufferFile))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(stationName ?: getString(R.string.unknown_station))
                    .setArtist(getString(R.string.app_name))
                    .build()
            )
            .build()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory as DataSource.Factory)
            .createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()

        notificationManager?.invalidate()
    }

    private fun stopTimeshiftRecorder() {
        streamRecorder?.stop()
        streamRecorder = null
        timeshiftBufferFile?.takeIf { it.exists() }?.delete()
        timeshiftBufferFile = null
    }

    fun stopPlayback() {
        stopTimeshiftRecorder()
        player?.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun getPlayer(): ExoPlayer? = player

    fun getCurrentStationName(): String? = stationName

    fun seekBackward(ms: Long) {
        val p = player ?: return
        val rec = streamRecorder ?: return
        val bufferFile = timeshiftBufferFile ?: return
        val now = System.currentTimeMillis()
        val currentPosMs = lastSeekPositionMs + (now - lastSeekTimeMs)
        val targetMs = (currentPosMs - ms).coerceAtLeast(0L)

        // ExoPlayer often ignores seekTo(ms) for live-style progressive source (C.LENGTH_UNSET).
        // Seek by reopening source at byte offset corresponding to targetMs.
        val bytesTotal = rec.getCurrentLength()
        val startMs = rec.getStartTimeMs()
        val elapsedMs = (now - startMs).coerceAtLeast(1L)
        val bytesPerMs = if (elapsedMs > 0 && bytesTotal > 0) bytesTotal / elapsedMs else 0L
        val targetBytes = if (bytesPerMs > 0) (targetMs * bytesPerMs).coerceIn(0L, bytesTotal) else 0L

        val dataSourceFactory = LiveFileDataSource.Factory(
            file = bufferFile,
            currentLengthSupplier = { rec.getCurrentLength() },
            startPositionOverride = { targetBytes }
        )
        val mediaItem = p.currentMediaItem ?: return
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory as DataSource.Factory)
            .createMediaSource(mediaItem)
        p.setMediaSource(mediaSource)
        p.prepare()
        p.play()

        lastSeekPositionMs = targetMs
        lastSeekTimeMs = now
        atLiveEdge = false
    }

    fun seekToLive() {
        val rec = streamRecorder ?: return
        val p = player ?: return
        val bufferFile = timeshiftBufferFile ?: return
        val dataSourceFactory = LiveFileDataSource.Factory(
            file = bufferFile,
            currentLengthSupplier = { rec.getCurrentLength() },
            startPositionOverride = { rec.getCurrentLength() }
        )
        val mediaItem = p.currentMediaItem ?: return
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory as DataSource.Factory)
            .createMediaSource(mediaItem)
        p.setMediaSource(mediaSource)
        p.prepare()
        p.play()
        atLiveEdge = true
        lastSeekPositionMs = 0L
        lastSeekTimeMs = System.currentTimeMillis()
    }

    fun isAtLive(): Boolean = atLiveEdge

    fun hasTimeshift(): Boolean = streamRecorder?.isRecording() == true

    private fun handlePlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player?.seekToDefaultPosition()
                player?.prepare()
            }
            else -> {
                markConnectionError()
                stopPlayback()
            }
        }
    }

    private fun releasePlayer() {
        stopTimeshiftRecorder()
        notificationManager?.setPlayer(null)
        mediaSession?.release()
        player?.let {
            it.stop()
            it.release()
            player = null
        }
        mediaSession = null
        notificationManager = null
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                item.buildUpon()
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(stationName ?: getString(R.string.unknown_station))
                            .setArtist(getString(R.string.app_name))
                            .build()
                    )
                    .build()
            }
            return Futures.immediateFuture(updatedItems.toMutableList())
        }
    }

    companion object {
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STREAM_URL = "stream_url"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "radio_playback_channel"

        @Volatile
        private var connectionErrorFlag = false

        fun markConnectionError() {
            connectionErrorFlag = true
        }

        fun getAndClearConnectionError(): Boolean =
            connectionErrorFlag.also { connectionErrorFlag = false }
    }
}
