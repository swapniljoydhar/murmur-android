package com.joy.murmur.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.joy.murmur.MainActivity
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
    val artworkBitmap: Bitmap?,
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
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, NowPlayingWidget::class.java)
        val appWidgetIds = manager.getAppWidgetIds(component)
        if (appWidgetIds.isNotEmpty()) {
            val updateIntent = Intent(appContext, NowPlayingWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            appContext.sendBroadcast(updateIntent)
        }
    }
}

class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
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

        // Open Murmur Now Playing / Continue when tapping the artwork or title
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(
                if (snapshot.subtitle.isNotBlank() || snapshot.isPlaying) "murmur://player" else "murmur://continue"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_artwork, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)

        // Set play/pause icon and accessibility contentDescription based on playback state
        val playPauseRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        val playPauseDesc = context.getString(
            if (snapshot.isPlaying) R.string.widget_pause_desc else R.string.widget_play_desc
        )
        views.setImageViewResource(R.id.widget_play_pause, playPauseRes)
        views.setContentDescription(R.id.widget_play_pause, playPauseDesc)

        // Set up transport click actions
        val playPauseIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra(WidgetActionReceiver.EXTRA_ACTION, if (snapshot.isPlaying) "pause" else "play")
        }
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            PendingIntent.getBroadcast(
                context,
                0,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        val previousIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra(WidgetActionReceiver.EXTRA_ACTION, "previous")
        }
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            PendingIntent.getBroadcast(
                context,
                1,
                previousIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        val nextIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            putExtra(WidgetActionReceiver.EXTRA_ACTION, "next")
        }
        views.setOnClickPendingIntent(
            R.id.widget_next,
            PendingIntent.getBroadcast(
                context,
                2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}