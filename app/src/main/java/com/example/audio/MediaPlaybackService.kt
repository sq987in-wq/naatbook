package com.example.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Media3 session/notification owner for service-owned entry playback. */
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {
    @Inject lateinit var engine: Media3PlaybackEngine

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val session = MediaSession.Builder(this, engine.player)
            .setSessionActivity(launchIntent())
            .build()
        mediaSession = session

        // This app starts playback with an explicit service command rather than binding an
        // in-process MediaController. onGetSession() is therefore not called automatically.
        // Explicit registration is required for MediaSessionService to observe the player,
        // publish its notification, and promote itself to a foreground service.
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .build()
            .apply { setSmallIcon(R.drawable.ic_stat_naatbook) }
        setMediaNotificationProvider(notificationProvider)
        addSession(session)
        setListener(object : MediaSessionService.Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Log.e(TAG, "System rejected media foreground-service promotion")
                engine.stop()
                stopSelf()
            }
        })
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
                    triggerNotificationUpdate()
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
        val player = mediaSession?.player
        if (player != null && isPlaybackOngoing() && player.playWhenReady &&
            player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
        ) {
            // Deliberately do not call super: its fallback stops non-registered/non-foreground
            // sessions. A registered ongoing session must survive removal from recents.
            return
        }
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        clearListener()
        mediaSession?.let { session ->
            if (isSessionAdded(session)) removeSession(session)
            session.release()
        }
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
        private const val CHANNEL_ID = "naatbook_playback"
        private const val NOTIFICATION_ID = 1001
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
            // Let MediaSessionService consume the player's empty-timeline events and remove the
            // foreground notification before releasing its session on service destruction.
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.stopService(Intent(context, MediaPlaybackService::class.java))
                } catch (error: Exception) {
                    Log.w(TAG, "MediaSessionService stop failed", error)
                }
            }, 250L)
        }
    }
}
