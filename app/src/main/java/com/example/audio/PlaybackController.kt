package com.example.audio

import android.content.Context
import android.util.Log
import com.example.data.NaatEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the Media3 session-owned entry shown by the global mini-player. */
data class NowPlaying(
    val naatId: Int,
    val title: String,
    val poet: String?,
    val audioPath: String
)

/** Process-wide ownership boundary around the shared Media3 player. */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: Media3PlaybackEngine
) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    private val _previewPath = MutableStateFlow<String?>(null)
    val previewPath: StateFlow<String?> = _previewPath.asStateFlow()

    val isPlaying = engine.isPlaying
    val currentPosition = engine.currentPosition
    val duration = engine.duration
    val isPreparing = engine.isPreparing
    val hasActiveSession = engine.hasActiveSession

    init {
        engine.onSessionStopped = {
            val wasEntrySession = _nowPlaying.value != null
            _nowPlaying.value = null
            _previewPath.value = null
            if (wasEntrySession) MediaPlaybackService.stop(context)
        }
    }

    fun playEntry(naat: NaatEntity) {
        val path = naat.audioPath ?: return
        playEntry(naat, path)
    }

    fun playEntry(naat: NaatEntity, path: String) {
        // The service creates MediaSession before replacing the current item. Keeping an
        // existing service alive avoids a stop/start race between consecutive entry requests.
        _previewPath.value = null
        _nowPlaying.value = NowPlaying(naat.id, naat.title, naat.poet, path)
        try {
            MediaPlaybackService.playEntry(context, path, naat.id, naat.title, naat.poet)
        } catch (error: Exception) {
            _nowPlaying.value = null
            engine.stop()
            MediaPlaybackService.stop(context)
            Log.e("PlaybackController", "Unable to start background playback", error)
        }
    }

    fun playPreview(path: String) {
        engine.play(
            audioPath = path,
            mediaId = "preview:${path.hashCode()}",
            title = "Audio preview",
            artist = null
        )
        if (!engine.hasActiveSession()) return
        _nowPlaying.value = null
        _previewPath.value = path
    }

    fun ownsEntry(naatId: Int): Boolean =
        _nowPlaying.value?.naatId == naatId && engine.hasActiveSession()

    fun ownsPreview(path: String): Boolean =
        _previewPath.value == path && engine.hasActiveSession()

    fun hasActiveSession(): Boolean = engine.hasActiveSession()
    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun togglePlayPause() = engine.togglePlayPause()
    fun seekTo(positionMs: Int) = engine.seekTo(positionMs)
    fun stop() = engine.stop()

    /** Closing/backgrounding the editor cannot stop a service-owned entry. */
    fun stopPreview() {
        if (_previewPath.value != null) engine.stop()
    }
}
