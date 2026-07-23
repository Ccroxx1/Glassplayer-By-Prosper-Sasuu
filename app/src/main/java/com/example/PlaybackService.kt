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
        val engine = PlayerEngine.get(this)
        promoteToForeground(engine)
        observeJob = scope.launch {
            launch { engine.currentTrack.collectLatest { refresh() } }
            launch { engine.isPlaying.collectLatest { refresh() } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = PlayerEngine.get(this)

        when (intent?.action) {
            ACTION_PLAY -> engine.togglePlayPause(forcePlay = true)
            ACTION_PAUSE -> engine.togglePlayPause(forcePause = true)
            ACTION_NEXT -> engine.nextTrack()
            ACTION_PREV -> engine.previousTrack()
            ACTION_STOP -> {
                engine.togglePlayPause(forcePause = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
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

        // Always re-assert foreground status — critical when the app is minimized
        promoteToForeground(engine)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents — keep playing if a track is active
        val engine = PlayerEngine.getOrNull()
        if (engine?.isPlaying?.value == true || engine?.currentTrack?.value != null) {
            promoteToForeground(engine)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
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
