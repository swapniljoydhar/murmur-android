package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip and rejection tests for the single Read Aloud media-ID format. These are plain JVM tests: the
 * codec deliberately does not use `android.net.Uri`, so encoding behaviour is the same under test and on
 * device.
 */
class ReadAloudMediaIdTest {

    @Test
    fun segmentIdRoundTripsContinuousMode() {
        val id = readAloudMediaId(
            documentId = "document:8b1c",
            pageStart = 5_000L,
            pageLength = 12_000,
            continuous = true,
            sourceOffset = 150L,
            cacheKey = "abc-def-ghi",
        )
        val parsed = ReadAloudSegmentInfo(
            documentId = "document:8b1c",
            pageStart = 5_000L,
            pageLength = 12_000,
            continuous = true,
            sourceOffset = 150L,
            cacheKey = "abc-def-ghi",
        )
        assertEquals(parsed, parseReadAloudMediaId(id))
    }

    @Test
    fun segmentIdRoundTripsPageMode() {
        val id = readAloudMediaId("doc-456", 0L, 12_000, false, 0L, "cache-key-123")
        val parsed = parseReadAloudMediaId(id)
        assertNotNull(parsed)
        assertEquals("doc-456", parsed!!.documentId)
        assertEquals(0L, parsed.pageStart)
        assertEquals(12_000, parsed.pageLength)
        assertFalse(parsed.continuous)
        assertEquals(0L, parsed.sourceOffset)
        assertEquals("cache-key-123", parsed.cacheKey)
    }

    @Test
    fun separatorInsideValuesCannotShiftFields() {
        // The old 18-field format left cacheKey/sectionId unencoded, so a '|' moved every later field.
        val hostileDocument = "doc|1|2|page|3|fake"
        val hostileCacheKey = "key|with|pipes"
        val id = readAloudMediaId(hostileDocument, 1L, 10, false, 5L, hostileCacheKey)

        assertEquals(8, id.split('|').size)
        val parsed = parseReadAloudMediaId(id)
        assertNotNull(parsed)
        assertEquals(hostileDocument, parsed!!.documentId)
        assertEquals(hostileCacheKey, parsed.cacheKey)
        assertEquals(1L, parsed.pageStart)
        assertEquals(10, parsed.pageLength)
        assertEquals(5L, parsed.sourceOffset)
    }

    @Test
    fun spacesPercentsUnicodeAndNewlinesSurviveRoundTrip() {
        val document = "déjà vu 100% — \"quoted\"\nsecond line"
        val cacheKey = "key with spaces & symbols ?#/[]"
        val parsed = parseReadAloudMediaId(readAloudMediaId(document, 0L, 10, true, 0L, cacheKey))
        assertNotNull(parsed)
        assertEquals(document, parsed!!.documentId)
        assertEquals(cacheKey, parsed.cacheKey)
    }

    @Test
    fun encodedIdIsAsciiAndSeparatorFree() {
        val id = readAloudMediaId("döc ument|with pipe", 0L, 10, true, 0L, "kéy")
        assertTrue(id.all { it.code < 128 })
        assertEquals(8, id.split('|').size)
    }

    @Test
    fun legacyUnversionedSegmentIdStillParses() {
        // Shape written before the version field existed; queue rows persisted with it must keep working.
        val legacy = "read_aloud|document%3A8b1c|5000|12000|continuous|150|abc-def"
        val parsed = parseReadAloudMediaId(legacy)
        assertNotNull(parsed)
        assertEquals("document:8b1c", parsed!!.documentId)
        assertEquals(5_000L, parsed.pageStart)
        assertEquals(12_000, parsed.pageLength)
        assertTrue(parsed.continuous)
        assertEquals(150L, parsed.sourceOffset)
        assertEquals("abc-def", parsed.cacheKey)
    }

    @Test
    fun malformedSegmentIdsAreRejected() {
        assertNull(parseReadAloudMediaId("invalid"))
        assertNull(parseReadAloudMediaId(""))
        assertNull(parseReadAloudMediaId("wrong_prefix|2|doc|1|2|page|0|key"))
        // unknown version
        assertNull(parseReadAloudMediaId("read_aloud|9|doc|1|2|page|0|key"))
        // wrong field count
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|1|2|page|0"))
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|1|2|page|0|key|extra"))
        // non-numeric fields
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|not_a_number|2|page|0|key"))
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|1|2|page|oops|key"))
        // unknown mode
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|1|2|sideways|0|key"))
        // blank document id
        assertNull(parseReadAloudMediaId("read_aloud|2||1|2|page|0|key"))
        // truncated percent escape
        assertNull(parseReadAloudMediaId("read_aloud|2|doc%|1|2|page|0|key"))
        assertNull(parseReadAloudMediaId("read_aloud|2|doc%ZZ|1|2|page|0|key"))
        // offsets outside the page
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|1|2|page|3|key"))
        assertNull(parseReadAloudMediaId("read_aloud|2|doc|-1|2|page|0|key"))
    }

    @Test
    fun startRequestRoundTrips() {
        val request = ReadAloudStartRequest(
            documentId = "document:abc|def",
            startChar = 42_000L,
            mimeType = "text/plain",
            strictOffline = true,
            continuous = false,
        )
        assertEquals(request, parseReadAloudStartMediaId(readAloudStartMediaId(request)))
    }

    @Test
    fun legacyStartRequestStillParses() {
        val legacy = "read_aloud_start|document%3Aabc|1000|application/pdf|false|continuous"
        val parsed = parseReadAloudStartMediaId(legacy)
        assertNotNull(parsed)
        assertEquals("document:abc", parsed!!.documentId)
        assertEquals(1_000L, parsed.startChar)
        assertEquals("application/pdf", parsed.mimeType)
        assertFalse(parsed.strictOffline)
        assertTrue(parsed.continuous)
    }

    @Test
    fun startRequestRejectsMalformedIds() {
        assertNull(parseReadAloudStartMediaId("read_aloud_start|2|doc|1|text/plain|true"))
        assertNull(parseReadAloudStartMediaId("read_aloud_start|2||1|text/plain|true|page"))
        assertNull(parseReadAloudStartMediaId("read_aloud_start|2|doc|-5|text/plain|true|page"))
        assertNull(parseReadAloudStartMediaId("read_aloud_start|7|doc|1|text/plain|true|page"))
        assertNull(parseReadAloudStartMediaId(readAloudMediaId("doc", 0L, 10, true, 0L, "key")))
    }

    @Test
    fun kindPredicatesDistinguishSegmentsFromStartRequests() {
        val segment = readAloudMediaId("doc", 0L, 10, true, 0L, "key")
        val start = readAloudStartMediaId(ReadAloudStartRequest("doc", 0L, "text/plain", true, true))
        assertTrue(isReadAloudMediaId(segment))
        assertFalse(isReadAloudStartMediaId(segment))
        assertTrue(isReadAloudStartMediaId(start))
        assertFalse(isReadAloudMediaId(start))
    }

    @Test
    fun codecRoundTripsBytesAndRejectsBadEscapes() {
        listOf("", "plain", "a b", "100%", "üñïçø∂é", "%41%42", "|", "\n\t").forEach { value ->
            assertEquals(value, MediaIdCodec.decode(MediaIdCodec.encode(value)))
        }
        assertNull(MediaIdCodec.decode("%"))
        assertNull(MediaIdCodec.decode("%A"))
        assertNull(MediaIdCodec.decode("%GG"))
    }
}
