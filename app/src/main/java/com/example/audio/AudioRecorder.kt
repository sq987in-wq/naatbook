package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false
    private var isPaused = false

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
            isRecording = true
            isPaused = false
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
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
