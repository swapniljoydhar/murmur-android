package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookPositionSliderTest {
    @Test
    fun normalizedSliderRoundTripsDistantPositionsInLongAudiobooks() {
        val durationMs = 237L * 60L * 60L * 1_000L
        val positions = listOf(0L, 1L, durationMs / 2L, durationMs - 1_000L, durationMs)

        positions.forEach { position ->
            val restored = playbackPositionAtFraction(playbackPositionFraction(position, durationMs), durationMs)
            assertTrue("position=$position restored=$restored", kotlin.math.abs(restored - position) <= 100L)
        }
    }

    @Test
    fun sliderClampsPositionsAndFractions() {
        assertEquals(0f, playbackPositionFraction(-1L, 10L))
        assertEquals(1f, playbackPositionFraction(11L, 10L))
        assertEquals(0L, playbackPositionAtFraction(-1f, 10L))
        assertEquals(10L, playbackPositionAtFraction(2f, 10L))
    }
}
