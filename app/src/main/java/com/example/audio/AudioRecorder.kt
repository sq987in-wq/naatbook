package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false
    private var isPaused = false

    // Pause-aware elapsed-time accounting
    private var startTimestampMs = 0L
    private var pausedTimestampMs = 0L
    private var accumulatedPauseMs = 0L

    fun start(outputFile: File) {
        stop()
        try {
            currentOutputFile = outputFile
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
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
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            // Never leak the native recorder when start fails
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            currentOutputFile = null
            isRecording = false
            isPaused = false
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

    fun stop() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            isPaused = false
            startTimestampMs = 0L
            accumulatedPauseMs = 0L
            pausedTimestampMs = 0L
        }
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
