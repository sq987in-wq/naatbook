package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service exposing the active [PlaybackController] through a
 * MediaSessionCompat + media-style notification. This is what powers
 * lock-screen playback controls (play/pause/stop/seek), headset and
 * Bluetooth media buttons, and the system media output switcher.
 *
 * Lifecycle: started (foreground) whenever the player starts playback via
 * [start]; mirrors the player's state flows; shuts itself down as soon as
 * the player session is fully stopped/released.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    @Inject lateinit var playbackController: PlaybackController

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var syncJob: Job? = null
    private var foregroundStarted = false

    // Throttling state (the player polls position every 250 ms)
    private var lastPushedPlaying: Boolean? = null
    private var lastPushedPositionSec = -1
    private var lastNotifiedPlaying: Boolean? = null
    private var lastMetadataDuration = -1

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (playbackController.hasActiveSession()) playbackController.resume()
        }

        override fun onPause() {
            playbackController.pause()
        }

        override fun onSeekTo(pos: Long) {
            playbackController.seekTo(pos.toInt())
        }

        override fun onStop() {
            playbackController.stop()
            shutdown()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(sessionCallback)
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(launchIntent())
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Intent is null on START_STICKY restarts — handleIntent would NPE on it
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
        if (!playbackController.hasActiveSession() || playbackController.nowPlaying.value == null) {
            // Process resurrected without a live entry session — nothing to control.
            shutdown()
        } else {
            setForegroundNotification(playbackController.isPlaying.value)
            lastMetadataDuration = -1
            pushMetadata(playbackController.duration.value)
            startSync()
        }
        return START_STICKY
    }

    /** Mirrors player state flows into the MediaSession + notification. */
    private fun startSync() {
        if (syncJob != null) return
        syncJob = serviceScope.launch {
            combine(
                playbackController.isPlaying,
                playbackController.currentPosition,
                playbackController.duration,
                playbackController.hasActiveSession
            ) { playing, pos, dur, hasSession ->
                PlaybackSnapshot(playing, pos, dur, hasSession)
            }.collect { snapshot ->
                pushPlaybackState(snapshot.isPlaying, snapshot.positionMs)
                pushMetadata(snapshot.durationMs)
                maybeUpdateNotification(snapshot.isPlaying)
                if (
                    (!snapshot.hasSession && !playbackController.hasActiveSession()) ||
                    playbackController.nowPlaying.value == null
                ) {
                    // Player fully stopped or switched to a UI-owned preview.
                    shutdown()
                }
            }
        }
    }

    /** Pushes on ~1 s cadence or on any play/pause transition (not every 250 ms poll). */
    private fun pushPlaybackState(playing: Boolean, positionMs: Int) {
        val positionSec = positionMs / 1000
        if (playing == lastPushedPlaying && positionSec == lastPushedPositionSec) return
        lastPushedPlaying = playing
        lastPushedPositionSec = positionSec

        val hasSession = playbackController.hasActiveSession()
        val state = when {
            playing -> PlaybackStateCompat.STATE_PLAYING
            hasSession -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, positionMs.toLong(), 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )
    }

    private fun pushMetadata(durationMs: Int) {
        if (durationMs == lastMetadataDuration) return
        lastMetadataDuration = durationMs
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    playbackController.nowPlaying.value?.title ?: getString(R.string.app_name)
                )
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playbackController.nowPlaying.value?.poet)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.toLong())
                .build()
        )
    }

    private fun setForegroundNotification(isPlaying: Boolean) {
        val notification = buildNotification(isPlaying)
        if (!foregroundStarted) {
            foregroundStarted = true
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                else 0
            )
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
        lastNotifiedPlaying = isPlaying
    }

    /** Only re-post the notification when the play/pause affordance flips. */
    private fun maybeUpdateNotification(isPlaying: Boolean) {
        if (!foregroundStarted || isPlaying == lastNotifiedPlaying) return
        lastNotifiedPlaying = isPlaying
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        ).build()
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_STOP
            )
        ).build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_naatbook)
            .setContentTitle(playbackController.nowPlaying.value?.title ?: getString(R.string.app_name))
            .setContentText(playbackController.nowPlaying.value?.poet)
            .setContentIntent(launchIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // fully visible on the lock screen
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying) // swipeable while paused
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun launchIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun shutdown() {
        syncJob?.cancel()
        syncJob = null
        mediaSession.isActive = false
        if (foregroundStarted) {
            foregroundStarted = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents — retire playback alongside the task
        playbackController.stop()
        shutdown()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        syncJob?.cancel()
        syncJob = null
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class PlaybackSnapshot(
        val isPlaying: Boolean,
        val positionMs: Int,
        val durationMs: Int,
        val hasSession: Boolean
    )

    companion object {
        private const val TAG = "NaatPlayback"
        private const val CHANNEL_ID = "naatbook_playback"
        private const val NOTIFICATION_ID = 1001

        /** Idempotently raise the foreground playback service (called from play()). */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MediaPlaybackService::class.java)
                )
            } catch (e: Exception) {
                // Never let lock-screen plumbing break in-app playback.
                Log.w(TAG, "Playback service start was rejected", e)
            }
        }
    }
}
