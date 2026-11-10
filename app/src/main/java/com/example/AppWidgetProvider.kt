package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

/**
 * Home-screen widget for GlassPlayer.
 * Shows: album art, title/artist, and play/pause + prev/next buttons.
 * Sends broadcast intents to PlaybackService on button taps.
 */
class GlassPlayerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val engine = PlayerEngine.getOrNull()
                val action = if (engine?.isPlaying?.value == true) {
                    PlaybackService.ACTION_PAUSE
                } else {
                    PlaybackService.ACTION_PLAY
                }
                sendToService(context, action)
            }
            ACTION_WIDGET_NEXT -> sendToService(context, PlaybackService.ACTION_NEXT)
            ACTION_WIDGET_PREV -> sendToService(context, PlaybackService.ACTION_PREV)
            ACTION_WIDGET_FAVORITE -> {
                val engine = PlayerEngine.getOrNull()
                val current = engine?.currentTrack?.value
                if (current != null && current.id > 0) {
                    Thread {
                        try {
                            val db = AudioDatabase.getDatabase(context.applicationContext)
                            runBlocking {
                                db.audioDao().toggleFavorite(current.id, !current.isFavorite)
                            }
                            engine.patchCurrentTrack { it.copy(isFavorite = !it.isFavorite) }
                            notifyUpdate(context)
                        } catch (_: Exception) {
                        }
                    }.start()
                }
            }
        }
    }

    private fun sendToService(context: Context, action: String) {
        val svcIntent = Intent(context, PlaybackService::class.java).apply {
            this.action = action
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (_: Exception) { }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.WIDGET_PREV"
        const val ACTION_WIDGET_FAVORITE = "com.example.WIDGET_FAVORITE"

        private fun pendingBroadcast(context: Context, action: String, req: Int): PendingIntent {
            val intent = Intent(context, GlassPlayerWidget::class.java).apply { this.action = action }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, req, intent, flags)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val engine = PlayerEngine.getOrNull()
            val track = engine?.currentTrack?.value
            val isPlaying = engine?.isPlaying?.value == true

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Title / artist
            views.setTextViewText(R.id.widget_title, track?.title ?: "GlassPlayer")
            views.setTextViewText(R.id.widget_artist, track?.artist ?: "Tap to open")
            val mood = track?.mood?.takeIf { it.isNotBlank() }?.let { "${moodEmoji(it)} $it" } ?: ""
            val bpm = track?.bpm?.takeIf { it > 0f }?.let { "${it.toInt()} BPM" } ?: ""
            views.setTextViewText(R.id.widget_meta, listOf(mood, bpm).filter { it.isNotBlank() }.joinToString("  •  "))

            // Album art
            val artBitmap = track?.albumArtUri?.let { uriStr ->
                try {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                } catch (_: Exception) { null }
            }
            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_art, blurBitmap(artBitmap))
            } else {
                views.setImageViewResource(R.id.widget_art, R.drawable.img_app_icon_1784343634612)
            }

            // Play/pause icon
            val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause
                         else android.R.drawable.ic_media_play
            views.setImageViewResource(R.id.widget_play_pause, ppIcon)

            // Button intents
            views.setOnClickPendingIntent(R.id.widget_play_pause, pendingBroadcast(context, ACTION_WIDGET_PLAY_PAUSE, 10))
            views.setOnClickPendingIntent(R.id.widget_next, pendingBroadcast(context, ACTION_WIDGET_NEXT, 11))
            views.setOnClickPendingIntent(R.id.widget_prev, pendingBroadcast(context, ACTION_WIDGET_PREV, 12))
            views.setOnClickPendingIntent(R.id.widget_favorite, pendingBroadcast(context, ACTION_WIDGET_FAVORITE, 13))

            val favoriteIcon = if (track?.isFavorite == true) {
                android.R.drawable.btn_star_big_on
            } else {
                android.R.drawable.btn_star_big_off
            }
            views.setImageViewResource(R.id.widget_favorite, favoriteIcon)

            // Tap on art/title opens the app
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val launchPending = PendingIntent.getActivity(context, 0, launchIntent, flags)
                views.setOnClickPendingIntent(R.id.widget_art, launchPending)
                views.setOnClickPendingIntent(R.id.widget_title, launchPending)
            }

            manager.updateAppWidget(widgetId, views)
        }

        /** Called by PlaybackService whenever track or play state changes. */
        fun notifyUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GlassPlayerWidget::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun blurBitmap(src: Bitmap): Bitmap {
            val scaledW = (src.width / 8).coerceAtLeast(1)
            val scaledH = (src.height / 8).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val blurred = Bitmap.createScaledBitmap(small, src.width, src.height, true)
            val out = blurred.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            val overlay = Paint().apply {
                color = 0x55000000
            }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), overlay)
            return out
        }

        private fun moodEmoji(mood: String): String = when (mood) {
            "Chill" -> "😌"
            "Hype" -> "🔥"
            "Focus" -> "🎯"
            "Sad" -> "🌧️"
            "Party" -> "🎉"
            "Workout" -> "💪"
            else -> "🎵"
        }
    }
}
