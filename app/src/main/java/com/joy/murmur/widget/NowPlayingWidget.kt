package com.joy.murmur.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.joy.murmur.R
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.playback.PlaybackContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

data class WidgetSnapshot(
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val artworkBitmap: android.graphics.Bitmap?,
    val contentType: PlaybackContentType,
) {
    val backgroundColor: Int
        get() = when (contentType) {
            PlaybackContentType.MUSIC -> 0xFF1E1E1E.toInt()
            PlaybackContentType.AUDIOBOOK -> 0xFF2D1B3D.toInt()
            PlaybackContentType.READ_ALOUD -> 0xFF1B2D2D.toInt()
        }
}

object WidgetManager {
    fun updateAllWidgets(context: Context) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, NowPlayingWidget::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(appWidgetIds, 0)
        }
    }
}

class NowPlayingWidget : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val snapshot = runBlocking(Dispatchers.IO) {
            val store = PlaybackStore(PlaybackDatabase.getInstance(context))
            WidgetSnapshot(
                title = store.getCurrentTitle() ?: context.getString(R.string.widget_default_title),
                subtitle = store.getCurrentSubtitle() ?: "",
                isPlaying = store.isPlaying(),
                artworkBitmap = null,
                contentType = store.getCurrentContentType(),
            )
        }

        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

        views.setTextViewText(R.id.widget_title, snapshot.title)
        views.setTextViewText(R.id.widget_subtitle, snapshot.subtitle)

        // Set play/pause icon based on playback state
        val playPauseRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseRes)

        // Set up click actions
        val playPauseIntent = android.content.Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra("action", if (snapshot.isPlaying) "pause" else "play")
        }
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            android.app.PendingIntent.getBroadcast(
                context, 0, playPauseIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ),
        )

        val previousIntent = android.content.Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra("action", "previous")
        }
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            android.app.PendingIntent.getBroadcast(
                context, 1, previousIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ),
        )

        val nextIntent = android.content.Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra("action", "next")
        }
        views.setOnClickPendingIntent(
            R.id.widget_next,
            android.app.PendingIntent.getBroadcast(
                context, 2, nextIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ),
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}