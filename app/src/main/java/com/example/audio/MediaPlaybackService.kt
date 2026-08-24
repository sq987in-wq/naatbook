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

/** Media3 session/notification owner for service-owned entry playback. */
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY_ENTRY) {
            val path = intent.getStringExtra(EXTRA_PATH)
            val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
            val title = intent.getStringExtra(EXTRA_TITLE)
            if (path != null && mediaId != null && title != null) {
                try {
                    // onCreate has already attached MediaSession listeners, so this state change
                    // always raises the foreground notification and lock-screen controls.
                    engine.play(
                        audioPath = path,
                        mediaId = mediaId,
                        title = title,
                        artist = intent.getStringExtra(EXTRA_ARTIST),
                        notifyPreviousOwner = false
                    )
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start service-owned playback", error)
                    engine.stop()
                    stopSelfResult(startId)
                }
            } else {
                Log.e(TAG, "Rejected incomplete playback request")
                stopSelfResult(startId)
            }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Active or paused entry sessions remain available through the media notification.
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
        private const val ACTION_PLAY_ENTRY = "com.example.audio.PLAY_ENTRY"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_MEDIA_ID = "mediaId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"

        fun playEntry(
            context: Context,
            path: String,
            naatId: Int,
            title: String,
            artist: String?
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_PLAY_ENTRY
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_MEDIA_ID, "naat:$naatId")
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                Log.e(TAG, "MediaSessionService start was rejected", error)
                throw error
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
