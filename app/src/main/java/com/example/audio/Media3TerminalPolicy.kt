package com.example.audio

import androidx.media3.common.Player

/** Pure terminal-state policy kept independent from ExoPlayer callbacks for regression tests. */
internal object Media3TerminalPolicy {
    fun shouldStop(playbackState: Int, mediaItemCount: Int, clearingInternally: Boolean): Boolean =
        playbackState == Player.STATE_ENDED ||
            (playbackState == Player.STATE_IDLE && mediaItemCount > 0 && !clearingInternally)
}
