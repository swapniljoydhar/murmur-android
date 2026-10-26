package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerPolicyTest {
    @Test
    fun remainingTimeNeverBecomesNegative() {
        assertEquals(5_000L, remainingSleepTimerMs(endTimeMs = 15_000L, nowMs = 10_000L))
        assertEquals(0L, remainingSleepTimerMs(endTimeMs = 10_000L, nowMs = 10_000L))
        assertEquals(0L, remainingSleepTimerMs(endTimeMs = 9_000L, nowMs = 10_000L))
    }
}
