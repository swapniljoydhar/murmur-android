package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSpeechMappingTest {
    @Test
    fun markdownCleanupPreservesSourceIndexesAndReadableLabels() {
        val source = "## Welcome\nRead **this** [chapter](https://example.com).\n"

        val spoken = ReadAloudUtils.markdownForSpeech(source)

        assertEquals(source.length, spoken.length)
        assertEquals(source.indexOf("Welcome"), spoken.indexOf("Welcome"))
        assertEquals(source.indexOf("this"), spoken.indexOf("this"))
        assertEquals(source.indexOf("chapter"), spoken.indexOf("chapter"))
        assertTrue(spoken.contains("Welcome"))
        assertTrue(spoken.contains("chapter"))
        assertFalse(spoken.contains("https://example.com"))
        assertFalse(spoken.contains("**"))
    }

    @Test
    fun codeBlocksAreSilencedWithoutShiftingFollowingText() {
        val source = "Before.\n```kotlin\nprintln(1)\n```\nAfter."

        val spoken = ReadAloudUtils.markdownForSpeech(source)

        assertEquals(source.length, spoken.length)
        assertEquals(source.indexOf("After"), spoken.indexOf("After"))
        assertTrue(spoken.contains("Before."))
        assertTrue(spoken.contains("After."))
        assertFalse(spoken.contains("println"))
    }
}
