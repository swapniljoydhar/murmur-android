package com.joy.murmur.playback

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A fixed-duration timer owned by PlaybackService, so it stays active with background playback. */
class SleepTimer(
    context: Context,
    private val onTimerComplete: () -> Unit,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var endTimeMs = preferences.getLong(PREFERENCE_END_TIME_MS, 0L)

    val remainingMs: Long
        get() = remainingSleepTimerMs(endTimeMs, System.currentTimeMillis())

    val isRunning: Boolean
        get() = endTimeMs > System.currentTimeMillis()

    init {
        if (isRunning) {
            schedule(endTimeMs)
        } else if (endTimeMs != 0L) {
            clearPersistedTimer()
        }
    }

    fun start(minutes: Int) {
        require(minutes in 1..MAX_DURATION_MINUTES) { "Sleep timer duration must be between 1 minute and 24 hours." }
        val endAt = System.currentTimeMillis() + minutes.toLong() * MILLIS_PER_MINUTE
        endTimeMs = endAt
        preferences.edit().putLong(PREFERENCE_END_TIME_MS, endAt).apply()
        schedule(endAt)
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        endTimeMs = 0L
        clearPersistedTimer()
    }

    /** Stops this service instance's timer job but keeps its deadline for service recreation. */
    fun shutdown() {
        timerJob?.cancel()
        timerJob = null
        scope.cancel()
    }

    private fun schedule(deadlineMs: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(remainingSleepTimerMs(deadlineMs, System.currentTimeMillis()))
            if (endTimeMs == deadlineMs) {
                endTimeMs = 0L
                clearPersistedTimer()
                onTimerComplete()
            }
        }
    }

    private fun clearPersistedTimer() {
        preferences.edit().remove(PREFERENCE_END_TIME_MS).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "murmur_sleep_timer"
        const val PREFERENCE_END_TIME_MS = "timer_end_time_ms"
        const val MAX_DURATION_MINUTES = 24 * 60
        const val MILLIS_PER_MINUTE = 60_000L
        val PRESET_MINUTES = listOf(15, 30, 45, 60)
    }
}

internal fun remainingSleepTimerMs(endTimeMs: Long, nowMs: Long): Long =
    (endTimeMs - nowMs).coerceAtLeast(0L)
