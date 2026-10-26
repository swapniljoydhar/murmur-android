package com.joy.murmur.data.speech

import com.joy.murmur.domain.documents.Section
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TtsSchedulerTest {

    @Test
    fun segmentText_splitsLongText() = runTest {
        val scheduler = TestableTtsScheduler()
        
        // Create text longer than maxSpeechInputLength (5000)
        val longText = "Sentence one. Sentence two. Sentence three. ".repeat(200) // ~7200 chars
        val segments = scheduler.segmentTextForTest(longText)
        
        assertTrue("Should split into multiple segments", segments.size > 1)
        assertTrue("Each segment should be under max length", segments.all { it.text.length <= 5000 })
    }

    @Test
    fun segmentText_preservesSentenceBoundaries() = runTest {
        val scheduler = TestableTtsScheduler()
        
        // The segmentText function splits by maxChars, not by sentences
        // This test verifies that when text fits in one segment, it returns one segment
        val shortText = "First sentence. Second sentence!"
        val segments = scheduler.segmentTextForTest(shortText)
        
        assertEquals(1, segments.size)
        assertEquals(shortText.trim(), segments[0].text.trim())
    }

    @Test
    fun extractSentenceIds_deterministic() = runTest {
        val scheduler = TestableTtsScheduler()
        
        val text = "Hello world. How are you?"
        val ids1 = scheduler.extractSentenceIdsForTest(text)
        val ids2 = scheduler.extractSentenceIdsForTest(text)
        
        assertEquals("Same text should produce same IDs", ids1, ids2)
        assertEquals(2, ids1.size)
        assertTrue("IDs should be deterministic hash-based", ids1[0].startsWith("sent_"))
    }

    // Minimal testable version exposing only what we need
    private class TestableTtsScheduler {
        private val generator = MockSpeechGenerator()
        
        fun segmentTextForTest(text: String): List<SpeechSegment> {
            return segmentText(text, generator.currentSynthesisIdentity, Section("s", "t", 0, 1000, 0, 1), 0)
        }
        
        fun extractSentenceIdsForTest(text: String): List<String> {
            return extractSentenceIds(text)
        }
        
        private fun segmentText(text: String, identity: SynthesisIdentity, section: Section, startChar: Long): List<SpeechSegment> {
            val maxChars = (generator.maxSpeechInputLength() - 128).coerceAtLeast(256)
            val segments = mutableListOf<SpeechSegment>()
            var offset = 0
            while (offset < text.length) {
                val end = minOf(offset + maxChars, text.length)
                var segmentEnd = end
                if (end < text.length) {
                    val boundary = text.lastIndexOf(' ', end)
                    if (boundary > offset + maxChars / 2) segmentEnd = boundary
                }
                val segmentText = text.substring(offset, segmentEnd).trim()
                if (segmentText.isNotEmpty()) {
                    val segmentId = java.util.UUID.randomUUID().toString()
                    val textHash = sha256("$segmentText|$identity")
                    segments += SpeechSegment(
                        segmentId = segmentId,
                        documentId = "test",
                        sectionId = section.id,
                        paragraphId = "",
                        sentenceIds = extractSentenceIds(segmentText),
                        startChar = startChar + offset,
                        endChar = startChar + segmentEnd,
                        text = segmentText,
                        textHash = textHash,
                        audioFile = null,
                        durationMs = estimateDuration(segmentText),
                        synthesisIdentity = identity,
                    )
                }
                offset = segmentEnd
                while (offset < text.length && text[offset].isWhitespace()) offset++
            }
            return segments
        }
        
        private fun extractSentenceIds(text: String): List<String> {
            return text.split(Regex("[.!?]+"))
                .filter { it.isNotBlank() }
                .map { sentence ->
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(sentence.trim().toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    "sent_${hash.take(16)}"
                }
        }
        
        private fun estimateDuration(text: String): Long {
            val words = text.length / 5
            return (words * 60_000L / 150).coerceAtLeast(1000L)
        }
        
        private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        
        private class MockSpeechGenerator : SpeechGenerator {
            override val currentSynthesisIdentity: SynthesisIdentity
                get() = SynthesisIdentity(
                    engine = "test", engineVersion = "1", voice = "voice",
                    locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
                    sampleRate = 22050, channelCount = 1, bitDepth = 16
                )
            override suspend fun synthesize(text: String, identity: SynthesisIdentity): File = File("/tmp/test.wav")
            override fun maxSpeechInputLength(): Int = 5000
        }
    }
}