package com.joy.murmur.playback

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReadAloudUtilsTest {

    @Test
    fun readAloudMediaId_roundTrip_parsesCorrectly() = runTest {
        val original = ReadAloudUtils.readAloudMediaId(
            documentId = "doc-123",
            pageStart = 5000L,
            pageLength = 12000,
            continuous = true,
            sourceOffset = 150L,
            cacheKey = "abc-def-ghi"
        )

        val parsed = ReadAloudUtils.parseReadAloudMediaId(original)

        assertNotNull(parsed)
        assertEquals("doc-123", parsed!!.documentId)
        assertEquals(5000L, parsed.pageStart)
        assertEquals(12000, parsed.pageLength)
        assertTrue(parsed.continuous)
        assertEquals(150L, parsed.sourceOffset)
        assertEquals("abc-def-ghi", parsed.cacheKey)
    }

    @Test
    fun readAloudMediaId_roundTrip_pageMode() = runTest {
        val original = ReadAloudUtils.readAloudMediaId(
            documentId = "doc-456",
            pageStart = 0L,
            pageLength = 12000,
            continuous = false,
            sourceOffset = 0L,
            cacheKey = "cache-key-123"
        )

        val parsed = ReadAloudUtils.parseReadAloudMediaId(original)

        assertNotNull(parsed)
        assertEquals("doc-456", parsed!!.documentId)
        assertEquals(0L, parsed.pageStart)
        assertEquals(12000, parsed.pageLength)
        assertFalse(parsed.continuous)
        assertEquals(0L, parsed.sourceOffset)
        assertEquals("cache-key-123", parsed.cacheKey)
    }

    @Test
    fun parseReadAloudMediaId_invalidFormat_returnsNull() = runTest {
        assertNull(ReadAloudUtils.parseReadAloudMediaId("invalid"))
        assertNull(ReadAloudUtils.parseReadAloudMediaId("read_aloud|doc|1|2|3|4")) // only 6 fields
        assertNull(ReadAloudUtils.parseReadAloudMediaId("wrong_prefix|doc|1|2|3|4|5"))
        assertNull(ReadAloudUtils.parseReadAloudMediaId("read_aloud|doc|not_a_number|2|page|0|key"))
    }

    // Simple URL encoding test without Android Uri
    @Test
    fun readAloudMediaId_handlesSpacesAndSymbols() = runTest {
        val original = ReadAloudUtils.readAloudMediaId(
            documentId = "doc with spaces",
            pageStart = 100L,
            pageLength = 1000,
            continuous = true,
            sourceOffset = 50L,
            cacheKey = "key with spaces"
        )

        val parsed = ReadAloudUtils.parseReadAloudMediaId(original)
        assertNotNull(parsed)
        assertEquals("doc with spaces", parsed!!.documentId)
        assertEquals("key with spaces", parsed.cacheKey)
    }
}