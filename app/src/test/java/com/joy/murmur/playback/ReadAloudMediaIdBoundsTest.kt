package com.joy.murmur.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudMediaIdBoundsTest {
    @Test
    fun acceptsValidPageAndSegmentOffsets() {
        assertTrue(isValidReadAloudSourceRange(pageStart = 120L, pageLength = 12_000, sourceOffset = 11_999L))
        assertTrue(isValidReadAloudSourceRange(pageStart = 0L, pageLength = 0, sourceOffset = 0L))
    }

    @Test
    fun rejectsNegativeOrOutOfPageOffsetsAndOverflowingRanges() {
        assertFalse(isValidReadAloudSourceRange(pageStart = -1L, pageLength = 10, sourceOffset = 0L))
        assertFalse(isValidReadAloudSourceRange(pageStart = 1L, pageLength = 10, sourceOffset = -1L))
        assertFalse(isValidReadAloudSourceRange(pageStart = 1L, pageLength = 10, sourceOffset = 11L))
        assertFalse(isValidReadAloudSourceRange(pageStart = Long.MAX_VALUE, pageLength = 0, sourceOffset = 0L))
        assertFalse(isValidReadAloudSourceRange(pageStart = Long.MAX_VALUE - 4, pageLength = 10, sourceOffset = 0L))
    }
}
