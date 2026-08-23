package com.example.audio

import android.content.Context
import com.example.data.NaatEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the media-session-owned entry shown by the global mini-player. */
data class NowPlaying(
    val naatId: Int,
    val title: String,
    val poet: String?
)

/**
 * Process-wide owner of every playback session.
 *
 * Entry playback is service-owned and carries [nowPlaying] metadata, so it can
 * survive reader/activity teardown. Editor previews are UI-owned and carry a
 * [previewPath], so backgrounding or closing the editor can stop only the
 * preview without accidentally killing an entry session.
 *
 * This intentionally formalizes the existing platform MediaPlayer model.
 * Migration to Media3 remains deferred to a separate change.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioPlayer: AudioPlayer
) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _previewPath = MutableStateFlow<String?>(null)
    val previewPath: StateFlow<String?> = _previewPath.asStateFlow()

    val isPlaying = audioPlayer.isPlaying
    val currentPosition = audioPlayer.currentPosition
    val duration = audioPlayer.duration
    val isPreparing = audioPlayer.isPreparing
    val hasActiveSession = audioPlayer.hasActiveSessionFlow

    init {
        audioPlayer.onSessionStopped = {
            _nowPlaying.value = null
            _previewPath.value = null
        }
    }

    fun playEntry(naat: NaatEntity) {
        val path = naat.audioPath ?: return
        // AudioPlayer.play() stops and clears the previous owner first.
        audioPlayer.play(path)
        if (!audioPlayer.hasActiveSession()) return
        _previewPath.value = null
        _nowPlaying.value = NowPlaying(naat.id, naat.title, naat.poet)
        MediaPlaybackService.start(context)
    }

    fun playPreview(path: String) {
        // Previews never raise the foreground media service.
        audioPlayer.play(path)
        if (!audioPlayer.hasActiveSession()) return
        _nowPlaying.value = null
        _previewPath.value = path
    }

    fun ownsEntry(naatId: Int): Boolean =
        _nowPlaying.value?.naatId == naatId && audioPlayer.hasActiveSession()

    fun ownsPreview(path: String): Boolean =
        _previewPath.value == path && audioPlayer.hasActiveSession()

    fun hasActiveSession(): Boolean = audioPlayer.hasActiveSession()

    fun pause() = audioPlayer.pause()

    fun resume() = audioPlayer.resume()

    fun togglePlayPause() = audioPlayer.togglePlayPause()

    fun seekTo(positionMs: Int) = audioPlayer.seekTo(positionMs)

    /** Stops whichever owner currently holds the shared player. */
    fun stop() = audioPlayer.stop()

    /** Stops a modal preview without touching service-owned entry playback. */
    fun stopPreview() {
        if (_previewPath.value != null) audioPlayer.stop()
    }
}
