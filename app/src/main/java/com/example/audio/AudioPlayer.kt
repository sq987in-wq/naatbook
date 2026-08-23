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
import javax.inject.Singleton
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

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    /** Notifies the process-scoped controller when the current owner is gone. */
    internal var onSessionStopped: (() -> Unit)? = null

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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to abandon audio focus", e)
        } finally {
            focusRequest = null
        }
    }

    /** True when a media session exists (prepared at least once) and can be resumed. */
    fun hasActiveSession(): Boolean = mediaPlayer != null

    fun play(audioPath: String) {
        stop()
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
                        stop()
                    }
                }
                setOnCompletionListener { completed ->
                    // Completion is terminal: release native resources/focus and clear ownership.
                    if (generation == playGeneration && mediaPlayer === completed) stop()
                    else try { completed.release() } catch (_: Exception) {}
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true
                }
                prepareAsync() // never block the UI thread
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            stop()
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
            stop()
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
            stop()
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
        resumeOnFocusGain = false
        // Detach first. A late async callback can no longer reclaim ownership.
        val player = mediaPlayer
        mediaPlayer = null
        try {
            if (player != null) {
                NativeResourceSafety.stopAndRelease(
                    stop = {
                        // isPlaying itself can throw for a broken native state.
                        if (player.isPlaying) player.stop()
                    },
                    release = {
                        try { player.release() } catch (e: Exception) {
                            Log.e(TAG, "Error releasing player", e)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player", e)
        } finally {
            _isPreparing.value = false
            _isPlaying.value = false
            _currentPosition.value = 0
            _duration.value = 0
            _hasActiveSessionFlow.value = false
            stopProgressTracking()
            abandonFocus()
            try { onSessionStopped?.invoke() } catch (e: Exception) {
                Log.e(TAG, "Session-stop callback failed", e)
            }
        }
    }

    /** Full process teardown; normal ViewModel destruction must not call this. */
    fun release() {
        stop()
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
