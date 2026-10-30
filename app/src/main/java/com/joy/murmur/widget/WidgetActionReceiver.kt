package com.joy.murmur.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.joy.murmur.playback.PlaybackService

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val mainHandler = Handler(Looper.getMainLooper())
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()

        val timeoutRunnable = Runnable {
            MediaController.releaseFuture(future)
            runCatching { pendingResult.finish() }
        }
        mainHandler.postDelayed(timeoutRunnable, 6_000L)

        future.addListener(
            {
                mainHandler.removeCallbacks(timeoutRunnable)
                try {
                    val controller = runCatching { future.get() }.getOrNull()
                    if (controller != null) {
                        dispatchTransportAction(controller, action)
                        WidgetManager.updateAllWidgets(appContext)
                    }
                } finally {
                    MediaController.releaseFuture(future)
                    runCatching { pendingResult.finish() }
                }
            },
            { command -> mainHandler.post(command) },
        )
    }

    companion object {
        const val EXTRA_ACTION = "action"
        internal val SUPPORTED_ACTIONS = setOf("play", "pause", "next", "previous")

        internal fun dispatchTransportAction(player: Player, action: String) {
            when (action) {
                "play" -> {
                    if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                        player.prepare()
                    }
                    if (!player.isPlaying) player.play()
                }
                "pause" -> if (player.isPlaying) player.pause()
                "next" -> player.seekToNextMediaItem()
                "previous" -> player.seekToPreviousMediaItem()
            }
        }
    }
}