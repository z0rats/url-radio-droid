package com.urlradiodroid.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.net.Network
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.core.app.NotificationCompat
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

    /** True when playback failed due to network; we retry when network is back (e.g. VPN reconnect). */
    private var pendingRetry = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.app_name)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createConnectingNotification(): android.app.Notification {
        val openIntent = Intent(this, PlaybackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PlaybackActivity.EXTRA_STATION_NAME, stationName)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(stationName ?: getString(R.string.unknown_station))
            .setContentText(getString(R.string.starting))
            .setSmallIcon(R.drawable.ic_play_circle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun startForegroundWithNotification(notification: android.app.Notification) {
        startForegroundWithNotification(NOTIFICATION_ID, notification)
    }

    private fun startForegroundWithNotification(notificationId: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.d(TAG, "onPlayerError: code=${error.errorCode}, message=${error.message}")
                    handlePlayerError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val p = this@RadioPlaybackService.player
                    Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying, state=${p?.playbackState}, mediaId=${p?.currentMediaItem?.mediaId?.take(50)}")
                    notificationManager?.invalidate()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateStr = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "?($playbackState)"
                    }
                    Log.d(TAG, "onPlaybackStateChanged: $stateStr")
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        notificationManager?.invalidate()
                    }
                }
            })
        }

        // MediaSession is created in startPlayback() when we have station info for lock screen session activity
    }

    private fun buildMediaSession(streamUrl: String) {
        val p = player ?: return
        mediaSession?.release()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlaybackActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PlaybackActivity.EXTRA_STATION_NAME, stationName)
                putExtra(PlaybackActivity.EXTRA_STREAM_URL, streamUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, p)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(sessionActivity)
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
                if (ongoing) {
                    startForegroundWithNotification(notificationId, notification)
                }
            }
        }).build().apply {
            setPlayer(exoPlayer)
        }
    }

    private fun startPlayback(streamUrl: String) {
        val exoPlayer = player ?: return
        Log.d(TAG, "startPlayback: isHls=${isHlsUrl(streamUrl)}, url=${streamUrl.take(60)}")
        pendingRetry = false
        stopTimeshiftRecorder()
        registerNetworkCallback()

        // Start foreground immediately so notification and lock screen controls appear right away
        startForegroundWithNotification(createConnectingNotification())

        buildMediaSession(streamUrl)

        val isHls = isHlsUrl(streamUrl)
        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(streamUrl)
            .setUri(streamUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(stationName ?: getString(R.string.unknown_station))
                    .setArtist(getString(R.string.app_name))
                    .build()
            )
        // Explicit HLS type when URL doesn't end with .m3u8 (per ExoPlayer HLS guide)
        if (isHls && !streamUrl.lowercase().endsWith(".m3u8")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val mediaItem = mediaItemBuilder.build()

        if (isHls) {
            // HLS: use HlsMediaSource + DefaultHttpDataSource per Android HLS guide (live segments, timeouts).
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
            val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(hlsMediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            // Single URL stream: record to buffer file and play with timeshift.
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
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory as DataSource.Factory)
                .createMediaSource(mediaItem.buildUpon().setUri(Uri.fromFile(bufferFile)).build())
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        }

        notificationManager?.invalidate()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { tryResumePlaybackAfterNetworkRestored() }
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) { /* already unregistered */ }
            networkCallback = null
        }
    }

    private fun tryResumePlaybackAfterNetworkRestored() {
        val p = player ?: return
        if (p.currentMediaItem == null) return
        // Retry when we had set pendingRetry (network error) or player is in IDLE (e.g. connection lost without our error code).
        val shouldRetry = pendingRetry ||
            p.playbackState == Player.STATE_IDLE
        if (!shouldRetry) return
        pendingRetry = false
        Log.d(TAG, "tryResumePlaybackAfterNetworkRestored: state=${p.playbackState}, preparing and playing")
        p.prepare()
        p.play()
        notificationManager?.invalidate()
    }

    private fun isHlsUrl(url: String): Boolean =
        url.contains("m3u8", ignoreCase = true)

    private fun stopTimeshiftRecorder() {
        streamRecorder?.stop()
        streamRecorder = null
        timeshiftBufferFile?.takeIf { it.exists() }?.delete()
        timeshiftBufferFile = null
    }

    fun stopPlayback() {
        pendingRetry = false
        unregisterNetworkCallback()
        stopTimeshiftRecorder()
        player?.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun isBuffering(): Boolean = player?.playbackState == Player.STATE_BUFFERING

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
                player?.play()
            }
            else -> {
                // Transient network errors (e.g. VPN toggle): retry when network is back instead of stopping.
                if (isRetryableNetworkError(error)) {
                    Log.d(TAG, "handlePlayerError: retryable network error, will retry when network available")
                    pendingRetry = true
                    notificationManager?.invalidate()
                } else {
                    markConnectionError()
                    stopPlayback()
                }
            }
        }
    }

    private fun isRetryableNetworkError(error: PlaybackException): Boolean {
        // All IO/network error codes in media3 (2000–2010): timeout, connection failed, reset, unspecified, etc.
        val code = error.errorCode
        return code in 2000..2010
    }

    private fun releasePlayer() {
        pendingRetry = false
        unregisterNetworkCallback()
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
        private const val TAG = "RadioPlayback"
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
