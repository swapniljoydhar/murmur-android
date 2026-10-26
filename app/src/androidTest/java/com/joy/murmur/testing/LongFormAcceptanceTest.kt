package com.joy.murmur.testing

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joy.murmur.domain.audiobooks.Audiobook
import com.joy.murmur.domain.documents.DocumentStructure
import com.joy.murmur.domain.documents.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Model-level checks for long-form values. These do not replace device playback,
 * process-death, memory, or latency acceptance tests.
 */
@RunWith(AndroidJUnit4::class)
class LongFormAcceptanceTest {
    @Test
    fun audiobook237HourDurationAndPositionStayWithinLongValues() {
        val durationMs = 237L * 60L * 60L * 1_000L
        val positionMs = durationMs - 1_000L
        val audiobook = Audiobook(
            id = "long-book",
            uri = Uri.parse("file:///long-book.m4b"),
            title = "Long book",
            author = null,
            durationMs = durationMs,
            coverArt = null,
            addedAtMillis = 0L,
            positionMs = positionMs,
            lastListenedAtMillis = null,
        )

        assertEquals(237L * 60L * 60L * 1_000L, audiobook.durationMs)
        assertEquals(positionMs, audiobook.positionMs)
        assertTrue(audiobook.isInProgress)
    }

    @Test
    fun distantSectionLookupUsesLongCharacterPositions() {
        val sectionCount = 100
        val wordsPerSection = 20_700L
        val charsPerWord = 6L
        val sectionLength = wordsPerSection * charsPerWord
        val sections = (0 until sectionCount).map { index ->
            val start = index.toLong() * sectionLength
            Section(
                id = "section-$index",
                title = "Section $index",
                startChar = start,
                endChar = start + sectionLength,
                startLine = index.toLong() + 1L,
                level = 1,
            )
        }
        val structure = DocumentStructure(
            documentId = "long-document",
            mimeType = "text/plain",
            totalChars = sectionCount.toLong() * sectionLength,
            totalLines = sectionCount.toLong(),
            sections = sections,
            fingerprint = "fixture-fingerprint",
            indexVersion = 1,
        )
        val estimatedHours = sectionCount * wordsPerSection / 150.0 / 60.0
        val distantPosition = sections[75].startChar + sectionLength / 2L
        val beyondIntPosition = Int.MAX_VALUE.toLong() + 1_024L
        val extendedStructure = structure.copy(
            totalChars = beyondIntPosition + 10L,
            sections = sections + Section(
                id = "section-after-int-range",
                title = "Extended section",
                startChar = beyondIntPosition,
                endChar = beyondIntPosition + 10L,
                startLine = sectionCount.toLong() + 1L,
                level = 1,
            ),
        )

        assertTrue(estimatedHours in 200.0..260.0)
        assertEquals("section-75", structure.findSectionAt(distantPosition)?.id)
        assertEquals("section-after-int-range", extendedStructure.findSectionAt(beyondIntPosition + 5L)?.id)
    }
}
