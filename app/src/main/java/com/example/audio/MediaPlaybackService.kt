package com.example.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media3 service owning the system MediaSession and automatic media notification.
 * ExoPlayer handles audio focus and becoming-noisy events; MediaSessionService supplies
 * lock-screen, Bluetooth/headset, notification, and external controller integration.
 */
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {
    @Inject lateinit var engine: Media3PlaybackEngine

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, engine.player)
            .setSessionActivity(launchIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep genuinely active listening alive after the Activity/task disappears. Retire an
        // idle service so a paused/cleared player cannot leave process work behind indefinitely.
        val player = mediaSession?.player
        if (player == null || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.example.MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "Media3Service"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MediaPlaybackService::class.java)
                )
            } catch (error: Exception) {
                // In-app playback remains usable if an OEM rejects foreground startup.
                Log.w(TAG, "MediaSessionService start was rejected", error)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MediaPlaybackService::class.java))
            } catch (error: Exception) {
                Log.w(TAG, "MediaSessionService stop failed", error)
            }
        }
    }
}
