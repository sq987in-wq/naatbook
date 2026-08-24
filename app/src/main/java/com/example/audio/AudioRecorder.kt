package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.File

class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false

    // Pause-aware elapsed-time accounting
    private var startTimestampMs = 0L
    private var pausedTimestampMs = 0L
    private var accumulatedPauseMs = 0L

    /** Starts capture and returns false after releasing native state on any setup failure. */
    fun start(outputFile: File): Boolean {
        stop()
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            // Publish before configuration: prepare/start can throw and catch must release it.
            mediaRecorder = recorder
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            startTimestampMs = SystemClock.elapsedRealtime()
            accumulatedPauseMs = 0L
            pausedTimestampMs = 0L
            isRecording = true
            isPaused = false
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            // Never leak the native recorder. File cleanup is owned by the shared
            // lifecycle coordinator so it never runs on the UI thread or races a save.
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            isRecording = false
            isPaused = false
            return false
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
                pausedTimestampMs = SystemClock.elapsedRealtime()
                isPaused = true
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to pause recording", e)
            }
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            try {
                mediaRecorder?.resume()
                accumulatedPauseMs += SystemClock.elapsedRealtime() - pausedTimestampMs
                isPaused = false
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to resume recording", e)
            }
        }
    }

    /**
     * Stops and releases native capture state. The caller owns output validation and
     * deletion through the shared audio-file lifecycle coordinator.
     */
    fun stop(): Boolean {
        // Detach first so no callback/query can observe a recorder being torn down.
        val recorder = mediaRecorder
        mediaRecorder = null
        var stoppedCleanly = true
        try {
            if (recorder != null) {
                NativeResourceSafety.stopAndRelease(
                    stop = { recorder.stop() },
                    release = {
                        try { recorder.release() } catch (e: Exception) {
                            Log.e("AudioRecorder", "Error releasing recorder", e)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recorder", e)
            stoppedCleanly = false
        } finally {
            isRecording = false
            isPaused = false
            startTimestampMs = 0L
            accumulatedPauseMs = 0L
            pausedTimestampMs = 0L
        }
        return stoppedCleanly
    }

    /** Elapsed recording time in ms, excluding paused intervals. */
    fun getElapsedMs(): Long {
        if (!isRecording || startTimestampMs == 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val pausedTotal = accumulatedPauseMs + if (isPaused) now - pausedTimestampMs else 0L
        return (now - startTimestampMs - pausedTotal).coerceAtLeast(0L)
    }

    /** Live peak amplitude (0–32767) for VU metering; 0 when idle or paused. */
    fun getMaxAmplitude(): Int {
        if (!isRecording || isPaused) return 0
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getRecordingState(): RecordingState {
        return when {
            !isRecording -> RecordingState.IDLE
            isPaused -> RecordingState.PAUSED
            else -> RecordingState.RECORDING
        }
    }
}

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED
}
