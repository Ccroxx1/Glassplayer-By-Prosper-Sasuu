package com.example

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground media service that keeps playback alive in the background and
 * powers lock-screen / notification mini player controls.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = PlayerEngine.get(this)

        when (intent?.action) {
            ACTION_PLAY -> engine.togglePlayPause(forcePlay = true)
            ACTION_PAUSE -> engine.togglePlayPause(forcePause = true)
            ACTION_NEXT -> engine.nextTrack()
            ACTION_PREV -> engine.previousTrack()
            ACTION_STOP -> {
                persistEngineSession(paused = true)
                engine.togglePlayPause(forcePause = true)
                tearDownForeground()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ENSURE_FOREGROUND, null -> {
                // Keep FGS alive / ignore non-media intents
            }
            else -> {
                // Avoid treating random actions as media-button events
            }
        }

        // Only forward real media-button intents to the session
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            try {
                MediaButtonReceiver.handleIntent(engine.getMediaSession(), intent)
            } catch (e: Exception) {
                Log.w(TAG, "Media button handle failed", e)
            }
        }

        // Sticky restart / ensure with nothing to play — do not linger as an empty FGS
        if (engine.currentTrack.value == null) {
            tearDownForeground()
            // Avoid leaving an idle engine (visualizer loop) after sticky restart
            if (intent?.action == null || intent.action == ACTION_ENSURE_FOREGROUND) {
                try {
                    PlayerEngine.getOrNull()?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Idle engine release failed", e)
                }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        ensureObserving(engine)
        promoteToForeground(engine)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from Recents — persist session, then stop playback and tear down
        Log.i(TAG, "Task removed — saving session, stopping playback and releasing media resources")
        observeJob?.cancel()
        observeJob = null
        try {
            persistEngineSession(paused = true)
            PlayerEngine.getOrNull()?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release PlayerEngine on task removed", e)
        }
        tearDownForeground()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        scope.cancel()
        super.onDestroy()
    }

    /** Writes the current engine snapshot before teardown so restore survives app exit / reboot. */
    private fun persistEngineSession(paused: Boolean) {
        val engine = PlayerEngine.getOrNull() ?: return
        val snapshot = engine.captureSession() ?: return
        val toSave = if (paused) snapshot.copy(wasPlaying = false) else snapshot
        try {
            runBlocking {
                PlaybackSessionStore(applicationContext).saveSession(toSave)
            }
            Log.i(TAG, "Persisted playback session for ${toSave.trackUri}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist playback session", e)
        }
    }

    private fun ensureObserving(engine: PlayerEngine) {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            launch { engine.currentTrack.collectLatest { refresh() } }
            launch { engine.isPlaying.collectLatest { refresh() } }
        }
    }

    private fun tearDownForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
        try {
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Notification cancel failed", e)
        }
    }

    private fun promoteToForeground(engine: PlayerEngine) {
        try {
            startAsForeground(buildNotification(this, engine))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start/update foreground", e)
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(this, engine))
            } catch (inner: Exception) {
                Log.e(TAG, "Notification fallback failed", inner)
            }
        }
    }

    private fun refresh() {
        val engine = PlayerEngine.getOrNull() ?: return
        if (engine.currentTrack.value == null) return
        try {
            if (engine.isPlaying.value) {
                promoteToForeground(engine)
            } else {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(this, engine))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification refresh failed", e)
        }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Now Playing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps music playing in the background with lock-screen controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CHANNEL_ID = "glassplayer_playback_v3"
        const val NOTIFICATION_ID = 42
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_ENSURE_FOREGROUND = "com.example.action.ENSURE_FOREGROUND"

        fun updateNotification(context: Context) {
            val engine = PlayerEngine.getOrNull() ?: return
            if (engine.currentTrack.value == null) return
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(context, engine))
            } catch (e: Exception) {
                Log.w(TAG, "updateNotification failed", e)
            }
        }

        private fun buildNotification(context: Context, engine: PlayerEngine): Notification {
            val track = engine.currentTrack.value
            val isPlaying = engine.isPlaying.value
            val title = track?.title ?: "GlassPlayer"
            val artist = track?.artist ?: "Ready"
            val session = engine.getMediaSession()

            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseAction = if (isPlaying) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    pendingService(context, ACTION_PAUSE, 1)
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Play",
                    pendingService(context, ACTION_PLAY, 2)
                )
            }

            val style = MediaNotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(pendingService(context, ACTION_STOP, 5))

            session?.sessionToken?.let { style.setMediaSession(it) }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText(track?.album)
                .setContentIntent(contentIntent)
                .setDeleteIntent(pendingService(context, ACTION_STOP, 5))
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_previous,
                        "Previous",
                        pendingService(context, ACTION_PREV, 3)
                    )
                )
                .addAction(playPauseAction)
                .addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_next,
                        "Next",
                        pendingService(context, ACTION_NEXT, 4)
                    )
                )
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setShowWhen(false)
                .build()
        }

        private fun pendingService(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
