package com.example.audio

/**
 * Process-wide rendezvous between the ViewModel-owned [AudioPlayer] and the
 * [MediaPlaybackService]. The service is a separate Android component and
 * cannot see the ViewModel, while this is a single-process app - so the live
 * player reference and the now-playing metadata cross the gap here.
 */
object PlaybackRegistry {

    @Volatile
    var player: AudioPlayer? = null
        private set

    @Volatile
    var currentTitle: String? = null
        private set

    @Volatile
    var currentArtist: String? = null
        private set

    @Volatile
    var currentPath: String? = null
        private set

    fun attach(player: AudioPlayer) {
        this.player = player
    }

    /** Now-playing metadata for the MediaSession / notification. */
    fun publish(title: String?, artist: String?, path: String) {
        currentTitle = title
        currentArtist = artist
        currentPath = path
    }

    fun clearPlayer(player: AudioPlayer) {
        if (this.player === player) {
            this.player = null
            currentTitle = null
            currentArtist = null
            currentPath = null
        }
    }
}
