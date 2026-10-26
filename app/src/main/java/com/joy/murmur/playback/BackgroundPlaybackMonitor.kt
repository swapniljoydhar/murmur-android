package com.joy.murmur.playback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackgroundPlaybackMonitor(
    private val context: Context,
    private val controller: MediaController,
    private val oemCompatibility: OemCompatibility,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var lastPlayerState = Player.STATE_IDLE
    private var lastPosition = 0L
    private var interruptionCount = 0
    private var lastInterruptionTime = 0L

    private val _healthState = MutableStateFlow<PlaybackHealthState>(PlaybackHealthState.HEALTHY)
    val healthState: StateFlow<PlaybackHealthState> = _healthState.asStateFlow()

    fun start() {
        monitorJob = scope.launch {
            while (true) {
                delay(5_000L)
                checkHealth()
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun checkHealth() {
        val playerState = controller.playbackState
        val position = controller.currentPosition
        val isPlaying = controller.playWhenReady && playerState == Player.STATE_READY
        val positionStalled = isPlaying && position == lastPosition && lastPlayerState == Player.STATE_READY
        val unexpectedPause = lastPlayerState == Player.STATE_READY && !controller.playWhenReady && playerState == Player.STATE_READY

        if (positionStalled || unexpectedPause) {
            val now = System.currentTimeMillis()
            if (now - lastInterruptionTime > 30_000L) {
                interruptionCount++
                lastInterruptionTime = now
                oemCompatibility.recordInterruption()

                if (interruptionCount >= 2) {
                    _healthState.value = PlaybackHealthState.DEGRADED
                }
                if (interruptionCount >= 5) {
                    _healthState.value = PlaybackHealthState.CRITICAL
                }
            }
        } else if (isPlaying) {
            if (interruptionCount > 0 && position != lastPosition) {
                interruptionCount = max(0, interruptionCount - 1)
            }
            if (interruptionCount == 0) {
                _healthState.value = PlaybackHealthState.HEALTHY
            }
        }

        lastPlayerState = playerState
        lastPosition = position

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationActive = notificationManager.activeNotifications.any { it.packageName == context.packageName }

        oemCompatibility.updateDiagnostics(
            isForegroundServiceActive = true,
            mediaSessionState = if (controller.playWhenReady && controller.playbackState == Player.STATE_READY) Player.STATE_READY else Player.STATE_IDLE,
            playerState = playerState,
            notificationState = notificationActive,
        )
    }
}

sealed interface PlaybackHealthState {
    object HEALTHY : PlaybackHealthState
    object DEGRADED : PlaybackHealthState
    object CRITICAL : PlaybackHealthState
}


