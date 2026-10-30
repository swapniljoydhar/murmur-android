package com.joy.murmur.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            cacheKey = "abc-def-ghi",
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
            cacheKey = "cache-key-123",
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
    fun parseReadAloudMediaId_invalidOrRemovedNeuralFormat_returnsNull() = runTest {
        assertNull(ReadAloudUtils.parseReadAloudMediaId("invalid"))
        assertNull(ReadAloudUtils.parseReadAloudMediaId("read_aloud|doc|1|2|3|4"))
        assertNull(ReadAloudUtils.parseReadAloudMediaId("wrong_prefix|doc|1|2|3|4|5"))
        assertNull(ReadAloudUtils.parseReadAloudMediaId("read_aloud|doc|not_a_number|2|page|0|key"))
        assertNull(parseReadAloudStartMediaId("neural_read_aloud_start|doc-1|0"))
    }

    @Test
    fun readAloudMediaId_encodesPipeAndPercentInEveryFieldWithoutShifting() = runTest {
        val original = readAloudMediaId(
            documentId = "doc|with|pipes%25and spaces",
            pageStart = 200L,
            pageLength = 4000,
            continuous = true,
            sourceOffset = 75L,
            cacheKey = "cache|key%with|pipes",
            sectionId = "sec|1|intro",
            paragraphId = "para|2",
            sentenceRangeStart = "s|0",
            sentenceRangeEnd = "s|4",
            textHash = "hash|abc",
            engine = "com.example|tts",
            voice = "en-US|Standard|A",
            locale = "en|US",
            documentFingerprint = "fp|size:99|mtime:1234",
            parseVersion = 1,
        )

        val parsed = parseReadAloudMediaId(original)
        assertNotNull(parsed)
        assertEquals("doc|with|pipes%25and spaces", parsed!!.documentId)
        assertEquals("cache|key%with|pipes", parsed.cacheKey)
        assertEquals("sec|1|intro", parsed.sectionId)
        assertEquals("para|2", parsed.paragraphId)
        assertEquals("s|0", parsed.sentenceRangeStart)
        assertEquals("s|4", parsed.sentenceRangeEnd)
        assertEquals("hash|abc", parsed.textHash)
        assertEquals("com.example|tts", parsed.engine)
        assertEquals("en-US|Standard|A", parsed.voice)
        assertEquals("en|US", parsed.locale)
        assertEquals("fp|size:99|mtime:1234", parsed.documentFingerprint)
        assertEquals(1, parsed.parseVersion)

        val startId = readAloudStartMediaId(
            documentId = "doc|with|pipes",
            startChar = 512L,
            mimeType = "text/markdown|custom",
            strictOffline = true,
            continuous = true,
        )
        val parsedStart = parseReadAloudStartMediaId(startId)
        assertNotNull(parsedStart)
        assertEquals("doc|with|pipes", parsedStart!!.documentId)
        assertEquals(512L, parsedStart.startChar)
        assertEquals("text/markdown|custom", parsedStart.mimeType)
        assertTrue(parsedStart.strictOffline)
        assertTrue(parsedStart.continuous)
    }
}