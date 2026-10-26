package com.joy.murmur.playback

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SleepTimer(
    private val context: Context,
    private val playbackController: PlaybackController?,
) : LifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var alarmManager: AlarmManager? = null
    private var pendingIntent: PendingIntent? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("murmur_sleep_timer", Context.MODE_PRIVATE)

    private val ALARM_ACTION = "com.joy.murmur.action.SLEEP_TIMER"
    private val PREFS_STATE = "timer_state"
    private val PREFS_REMAINING = "timer_remaining_ms"
    private val PREFS_END_TIME = "timer_end_time_ms"
    private val PREFS_MODE = "timer_mode"

    enum class TimerMode {
        TIMER,           // Fixed duration
        END_OF_CHAPTER,  // Stop at end of current chapter
        END_OF_SECTION,  // Stop at end of current section
        CUSTOM,          // Custom duration
    }

    enum class TimerState {
        IDLE,
        RUNNING,
        PAUSED,
    }

    private var _mode: TimerMode = prefs.getString(PREFS_MODE, "TIMER")?.let { TimerMode.valueOf(it) } ?: TimerMode.TIMER
    val mode: TimerMode
        get() = _mode

    private var _state: TimerState = prefs.getString(PREFS_STATE, "IDLE")?.let { TimerState.valueOf(it) } ?: TimerState.IDLE
    val state: TimerState
        get() = _state

    private var _remainingMs: Long = prefs.getLong(PREFS_REMAINING, 0)
    val remainingMs: Long
        get() = if (_state == TimerState.RUNNING && _mode == TimerMode.TIMER) {
            (_endTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
        } else {
            _remainingMs
        }

    private var _endTimeMs: Long = prefs.getLong(PREFS_END_TIME, 0)
    val endTimeMs: Long
        get() = _endTimeMs

    private var _currentChapterEndMs: Long? = null
    private var _currentSectionEndMs: Long? = null

    init {
        if (_state == TimerState.RUNNING && _mode == TimerMode.TIMER && _endTimeMs > 0) {
            val now = System.currentTimeMillis()
            if (_endTimeMs > now) {
                resume()
            } else {
                _state = TimerState.IDLE
                _remainingMs = 0
                _endTimeMs = 0
                saveState()
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        cancel()
        cancelSystemAlarm()
    }

    fun start(minutes: Int) {
        cancel()
        _mode = TimerMode.TIMER
        val durationMs = minutes.toLong() * 60_000L
        _remainingMs = durationMs
        _endTimeMs = System.currentTimeMillis() + durationMs
        _state = TimerState.RUNNING
        saveState()

        timerJob = scope.launch {
            try {
                delay(durationMs)
                if (_state == TimerState.RUNNING && _mode == TimerMode.TIMER) {
                    onTimerComplete()
                }
            } catch (e: Exception) {
                _state = TimerState.IDLE
                saveState()
            }
        }

        scheduleSystemAlarm(durationMs)
    }

    fun startEndOfChapter(chapterEndMs: Long) {
        cancel()
        _mode = TimerMode.END_OF_CHAPTER
        _currentChapterEndMs = chapterEndMs
        _state = TimerState.RUNNING
        val currentPos = playbackController?.snapshot?.value?.positionMs ?: 0L
        _remainingMs = (chapterEndMs - currentPos).coerceAtLeast(0)
        saveState()
    }

    fun startEndOfSection(sectionEndMs: Long) {
        cancel()
        _mode = TimerMode.END_OF_SECTION
        _currentSectionEndMs = sectionEndMs
        _state = TimerState.RUNNING
        val currentPos = playbackController?.snapshot?.value?.positionMs ?: 0L
        _remainingMs = (sectionEndMs - currentPos).coerceAtLeast(0)
        saveState()
    }

    fun startCustom(milliseconds: Long) {
        cancel()
        _mode = TimerMode.CUSTOM
        _remainingMs = milliseconds
        _endTimeMs = System.currentTimeMillis() + milliseconds
        _state = TimerState.RUNNING
        saveState()

        timerJob = scope.launch {
            try {
                delay(milliseconds)
                if (_state == TimerState.RUNNING && _mode == TimerMode.CUSTOM) {
                    onTimerComplete()
                }
            } catch (e: Exception) {
                _state = TimerState.IDLE
                saveState()
            }
        }

        scheduleSystemAlarm(milliseconds)
    }

    fun pause() {
        if (_state == TimerState.RUNNING) {
            _state = TimerState.PAUSED
            _remainingMs = when (_mode) {
                TimerMode.TIMER, TimerMode.CUSTOM -> _endTimeMs - System.currentTimeMillis()
                TimerMode.END_OF_CHAPTER -> _currentChapterEndMs?.let { it - (playbackController?.snapshot?.value?.positionMs ?: 0) } ?: _remainingMs
                TimerMode.END_OF_SECTION -> _currentSectionEndMs?.let { it - (playbackController?.snapshot?.value?.positionMs ?: 0) } ?: _remainingMs
            }.coerceAtLeast(0)
            timerJob?.cancel()
            timerJob = null
            cancelSystemAlarm()
            saveState()
        }
    }

    fun resume() {
        if (_state == TimerState.PAUSED && _remainingMs > 0) {
            _state = TimerState.RUNNING
            when (_mode) {
                TimerMode.TIMER, TimerMode.CUSTOM -> {
                    _endTimeMs = System.currentTimeMillis() + _remainingMs
                }
                TimerMode.END_OF_CHAPTER -> {
                    _currentChapterEndMs?.let { _remainingMs = it - (playbackController?.snapshot?.value?.positionMs ?: 0) }
                }
                TimerMode.END_OF_SECTION -> {
                    _currentSectionEndMs?.let { _remainingMs = it - (playbackController?.snapshot?.value?.positionMs ?: 0) }
                }
            }
            saveState()
            when (_mode) {
                TimerMode.TIMER, TimerMode.CUSTOM -> {
                    timerJob = scope.launch {
                        try {
                            delay(_remainingMs)
                            if (_state == TimerState.RUNNING) {
                                onTimerComplete()
                            }
                        } catch (e: Exception) {
                            _state = TimerState.IDLE
                            saveState()
                        }
                    }
                    scheduleSystemAlarm(_remainingMs)
                }
                TimerMode.END_OF_CHAPTER, TimerMode.END_OF_SECTION -> {
                    // Chapter/section end is monitored via playback position
                }
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state = TimerState.IDLE
        _mode = TimerMode.TIMER
        _remainingMs = 0
        _endTimeMs = 0
        _currentChapterEndMs = null
        _currentSectionEndMs = null
        cancelSystemAlarm()
        saveState()
    }

    suspend fun showPicker() {
        // Implemented by UI layer
    }

    fun extendMinutes(minutes: Int) {
        if (_state == TimerState.RUNNING || _state == TimerState.PAUSED) {
            val additionalMs = minutes.toLong() * 60_000L
            _remainingMs += additionalMs
            when (_mode) {
                TimerMode.TIMER, TimerMode.CUSTOM -> _endTimeMs += additionalMs
                TimerMode.END_OF_CHAPTER -> _currentChapterEndMs = (_currentChapterEndMs ?: 0) + additionalMs
                TimerMode.END_OF_SECTION -> _currentSectionEndMs = (_currentSectionEndMs ?: 0) + additionalMs
            }
            saveState()
            if (_state == TimerState.RUNNING && _mode != TimerMode.END_OF_CHAPTER && _mode != TimerMode.END_OF_SECTION) {
                timerJob?.cancel()
                timerJob = scope.launch {
                    try {
                        delay(_remainingMs)
                        if (_state == TimerState.RUNNING) {
                            onTimerComplete()
                        }
                    } catch (e: Exception) {
                        _state = TimerState.IDLE
                        saveState()
                    }
                }
                scheduleSystemAlarm(_remainingMs)
            }
        }
    }

    private fun onTimerComplete() {
        _state = TimerState.IDLE
        _mode = TimerMode.TIMER
        _remainingMs = 0
        _endTimeMs = 0
        _currentChapterEndMs = null
        _currentSectionEndMs = null
        saveState()
        playbackController?.togglePlayPause()
    }

    private fun saveState() {
        prefs.edit()
            .putString(PREFS_STATE, _state.name)
            .putString(PREFS_MODE, _mode.name)
            .putLong(PREFS_REMAINING, _remainingMs)
            .putLong(PREFS_END_TIME, _endTimeMs)
            .apply()
    }

    private fun scheduleSystemAlarm(delayMs: Long) {
        try {
            alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ALARM_ACTION).apply {
                putExtra("remaining_ms", _remainingMs)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            pendingIntent = pi
            // Check if we can schedule exact alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager?.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + delayMs,
                        pi,
                    )
                } else {
                    // Fallback to setExact if exact alarm permission not granted
                    alarmManager?.setExact(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + delayMs,
                        pi,
                    )
                }
            } else {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs,
                    pi,
                )
            }
        } catch (e: Exception) {
            // Alarm scheduling may not be available; in-process timer is fallback
        }
    }

    private fun cancelSystemAlarm() {
        try {
            pendingIntent?.let { pi ->
                alarmManager?.cancel(pi)
            }
        } catch (e: Exception) {
            // Ignore
        }
        pendingIntent = null
    }

    fun release() {
        cancel()
        cancelSystemAlarm()
    }

    companion object {
        val PRESET_MINUTES = listOf(15, 30, 45, 60)
        val QUICK_EXTEND_MINUTES = listOf(10, 30)
    }
}

class SleepTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.joy.murmur.action.SLEEP_TIMER") return
        val serviceIntent = Intent(context, PlaybackService::class.java)
            .setAction("com.joy.murmur.action.SLEEP_TIMER_PAUSE")
        context.startForegroundService(serviceIntent)
    }
}