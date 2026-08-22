package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private var progressJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + Job())

    fun play(audioPath: String) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                if (audioPath.startsWith("content://")) {
                    setDataSource(context, Uri.parse(audioPath))
                } else {
                    val file = File(audioPath)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                    } else {
                        Log.e("AudioPlayer", "File does not exist: $audioPath")
                        setDataSource(audioPath)
                    }
                }
                prepare()
                start()
                _duration.value = duration
                _isPlaying.value = true
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    stopProgressTracking()
                }
            }
            startProgressTracking()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error starting playback", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    stopProgressTracking()
                } else {
                    it.start()
                    _isPlaying.value = true
                    startProgressTracking()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error toggling playback", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPosition.value = positionMs
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error seeking", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error in stop", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPosition.value = 0
            _duration.value = 0
            stopProgressTracking()
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = playerScope.launch {
            while (true) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
