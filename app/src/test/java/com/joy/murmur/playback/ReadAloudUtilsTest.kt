package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the spoken/rendered index maps used for highlighting and tap-to-read. Markdown cleanup itself is
 * covered by [MarkdownSpeechMappingTest].
 */
class ReadAloudUtilsTest {

    @Test
    fun spokenIndexMapIsBoundedAndMonotonic() {
        val source = "one *two* three [four](https://example.com)"
        val map = ReadAloudUtils.buildSpokenIndexMap(source)
        assertEquals(source.length, map.size)
        var previous = -1
        map.forEach { spokenIndex ->
            if (spokenIndex < 0) {
                assertTrue("unmapped entries must be syntax or whitespace", spokenIndex == -1)
            } else {
                assertTrue("spoken indexes must increase", spokenIndex > previous)
                previous = spokenIndex
            }
        }
        assertTrue("some text must remain spoken", previous >= 0)
    }

    @Test
    fun inverseSpokenIndexMapIsMonotonicAndInBounds() {
        val source = "one *two* three"
        val inverse = ReadAloudUtils.buildInverseSpokenIndexMap(ReadAloudUtils.buildSpokenIndexMap(source))
        assertTrue(inverse.isNotEmpty())
        inverse.indices.forEach { spokenIndex ->
            assertTrue(inverse[spokenIndex] in source.indices)
            if (spokenIndex > 0) {
                assertTrue(inverse[spokenIndex] >= inverse[spokenIndex - 1])
            }
        }
    }

    @Test
    fun emptyTextProducesEmptyMaps() {
        assertEquals(0, ReadAloudUtils.buildSpokenIndexMap("").size)
        assertEquals(0, ReadAloudUtils.buildInverseSpokenIndexMap(IntArray(0)).size)
    }
}
