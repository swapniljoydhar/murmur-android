package com.joy.murmur.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joy.murmur.playback.PlaybackService

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            putExtra("widget_action", action)
        }
        context.startService(serviceIntent)
    }
}