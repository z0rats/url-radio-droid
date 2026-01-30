package com.urlradiodroid.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Records a stream URL to a file in the background. Used for timeshift (rewind) support.
 * Stops when maxSizeBytes is reached (~30 MB = ~25–30 min at 128 kbps).
 */
class StreamRecorder(
    private val streamUrl: String,
    private val outputFile: File,
    private val maxSizeBytes: Long = 30L * 1024 * 1024
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val bytesWritten = AtomicLong(0L)
    private val startTimeMs = AtomicLong(0L)
    private val recording = AtomicBoolean(false)
    private var job: Job? = null

    fun getCurrentLength(): Long = bytesWritten.get()

    fun getStartTimeMs(): Long = startTimeMs.get()

    fun isRecording(): Boolean = recording.get()

    fun start(onError: (Throwable) -> Unit = {}) {
        if (recording.getAndSet(true)) return
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    recordStream(onError)
                }
            } finally {
                recording.set(false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recording.set(false)
    }

    private fun recordStream(onError: (Throwable) -> Unit) {
        try {
            val request = Request.Builder().url(streamUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onError(RuntimeException("HTTP ${response.code}"))
                    return
                }
                val body = response.body ?: run {
                    onError(RuntimeException("Empty response body"))
                    return
                }
                body.byteStream().use { input ->
                    FileOutputStream(outputFile, false).use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        if (startTimeMs.get() == 0L) {
                            startTimeMs.set(System.currentTimeMillis())
                        }
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1 && total < maxSizeBytes) {
                            val toWrite = minOf(read.toLong(), maxSizeBytes - total).toInt()
                            output.write(buffer, 0, toWrite)
                            total += toWrite
                            bytesWritten.set(total)
                            if (total >= maxSizeBytes) break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}
