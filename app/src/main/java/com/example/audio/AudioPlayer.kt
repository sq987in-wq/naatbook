package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    init {
        // Register for the MediaPlaybackService (lock-screen controls)
        PlaybackRegistry.attach(this)
    }
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    /** True while an async prepare (buffering) is in flight. */
    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    // Observable variant of hasActiveSession() for UI (e.g. the global mini-player).
    private val _hasActiveSessionFlow = MutableStateFlow(false)
    val hasActiveSessionFlow: StateFlow<Boolean> = _hasActiveSessionFlow.asStateFlow()

    private var progressJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + Job())

    // Invalidates stale async-prepare callbacks after stop()/re-play()
    private var playGeneration = 0

    // --- Audio focus ---
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (another app took media audio) — stop cleanly.
                resumeOnFocusGain = false
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (call, alarm) — pause now, resume on regain.
                resumeOnFocusGain = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try { mediaPlayer?.setVolume(0.25f, 0.25f) } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    /** True when a media session exists (prepared at least once) and can be resumed. */
    fun hasActiveSession(): Boolean = mediaPlayer != null

    fun play(audioPath: String, title: String? = null, artist: String? = null) {
        stop()
        // Publish now-playing metadata and raise the MediaSession service so
        // the lock screen / notification / Bluetooth buttons can control us.
        PlaybackRegistry.publish(title, artist, audioPath)
        MediaPlaybackService.start(context)
        val generation = ++playGeneration
        if (!requestFocus()) {
            Log.w(TAG, "Audio focus not granted — playing anyway")
        }
        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            _isPreparing.value = true
            mp.apply {
                if (audioPath.startsWith("content://")) {
                    setDataSource(context, Uri.parse(audioPath))
                } else {
                    val file = File(audioPath)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                    } else {
                        Log.e(TAG, "File does not exist: $audioPath")
                        setDataSource(audioPath)
                    }
                }
                setOnPreparedListener { prepared ->
                    if (generation != playGeneration || mediaPlayer !== prepared) {
                        // Superseded by a newer play()/stop() — release quietly.
                        try { prepared.release() } catch (_: Exception) {}
                        return@setOnPreparedListener
                    }
                    _isPreparing.value = false
                    try {
                        prepared.start()
                        _duration.value = prepared.duration
                        _isPlaying.value = true
                        _hasActiveSessionFlow.value = true
                        startProgressTracking()
                    } catch (e: Exception) {
                        Log.e(TAG, "start() after prepare failed", e)
                    }
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    stopProgressTracking()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _isPreparing.value = false
                    _isPlaying.value = false
                    _hasActiveSessionFlow.value = false
                    stopProgressTracking()
                    true
                }
                prepareAsync() // never block the UI thread
            }
        } catch (e: Exception) {
            _isPreparing.value = false
            Log.e(TAG, "Error starting playback", e)
        }
    }

    /** Strict pause — does NOT toggle. */
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    stopProgressTracking()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }

    /** Resume a paused session (or replay a completed one from the start). */
    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _isPlaying.value = true
                    startProgressTracking()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPosition.value = positionMs
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun stop() {
        playGeneration++ // invalidate any pending prepare callback
        _isPreparing.value = false
        resumeOnFocusGain = false
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stop", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPosition.value = 0
            _duration.value = 0
            _hasActiveSessionFlow.value = false
            stopProgressTracking()
            abandonFocus()
        }
    }

    /** Full teardown — cancels the coroutine scope. Called from ViewModel.onCleared(). */
    fun release() {
        stop()
        PlaybackRegistry.clearPlayer(this)
        playerScope.cancel()
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = playerScope.launch {
            while (isActive) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPosition.value = player.currentPosition
                        }
                    }
                } catch (e: Exception) {
                    // Player released mid-poll — exit quietly
                    return@launch
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}
